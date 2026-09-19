package network.azusake.halo.render;

import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import network.azusake.halo.core.render.*;
import network.azusake.halo.core.Identifier;
import org.joml.*;
import org.lwjgl.system.MemoryUtil;
import java.util.*;

/** Native GPU buffers, scoped to visual generation. Failed uploads use the completed frame's CPU fallback. */
final class HaloMeshBufferCache implements AutoCloseable {
    private GenerationMeshCache<MeshBuffer> buffers = GenerationMeshCache.empty();
    static HaloMeshBufferCache empty() { return new HaloMeshBufferCache(); }
    HaloMeshBufferCache updated(VisualResources resources) { return updated(resources, Set.of()); }
    HaloMeshBufferCache updated(VisualResources resources, Set<Identifier> quads) {
        buffers = buffers.updated(resources.generation(), resources.meshes(), (id, mesh) -> new MeshBuffer(mesh),
            (id, error) -> org.slf4j.LoggerFactory.getLogger("halo").warn("GPU upload failed for {}; using CPU fallback",id,error)).cache();
        return this;
    }
    boolean draw(long generation, MeshDraw draw, Matrix4f outer, RenderEnvironment environment, MeshDrawWorkspace workspace) {
        if (buffers.generation() != generation) return false;
        var buffer = buffers.get(draw.model()); if (buffer == null) return false;
        var matrix=workspace.modelView(outer,draw);
        var state=new DrawBatch(DrawBatch.Topology.TRIANGLES,List.of(),draw.texture(),true,draw.cull(),draw.blend(),
            draw.depthTest(),draw.depthWrite(),draw.red(),draw.green(),draw.blue(),draw.alpha(),draw.material(),draw.light(),draw.directionalLighting());
        return buffer.draw(state,matrix,workspace.normal(matrix),draw.blend(),draw.mirrored(),environment);
    }
    boolean drawPrimitive(long generation, PrimitiveDraw draw, Matrix4f outer, RenderEnvironment environment) {
        if (buffers.generation()!=generation) return false;
        var buffer=buffers.get(draw.geometry().id()); if(buffer==null) return false;
        var s=draw.state(); float brightness=HaloDrawSubmitter.quantizedColor(draw.brightness());
        var state=new DrawBatch(DrawBatch.Topology.TRIANGLES,List.of(),s.texture(),s.textured(),s.cull(),s.blend(),s.depthTest(),s.depthWrite(),
            s.red()*brightness,s.green()*brightness,s.blue()*brightness,s.alpha(),s.material(),s.light(),s.directionalLighting());
        var modelView=new Matrix4f(outer).mul(new Matrix4f().set(draw.localToView()));
        var normal=new MeshDrawWorkspace().normal(outer).mul(new Matrix3f().set(draw.normalToView()));
        return buffer.draw(state,modelView,normal,false,false,environment);
    }
    @Override public void close() { buffers.close(); buffers=GenerationMeshCache.empty(); }
    private static final class MeshBuffer implements GenerationMeshCache.Owned {
        private final MeshIndexWriter writer;
        private Stream nativeStream, irisStream;
        private boolean nativeFailed, irisFailed;

        MeshBuffer(TriangleMesh mesh) { writer = new MeshIndexWriter(mesh); }

        boolean draw(DrawBatch state, Matrix4f matrix, Matrix3f normal, boolean sort,
                     boolean mirrored, RenderEnvironment environment) {
            boolean extended = environment == RenderEnvironment.WORLD
                && network.azusake.halo.physics.OptionalIrisPassDetector.hasShaderPack();
            if (extended ? irisFailed : nativeFailed) return false;
            Stream stream = extended ? irisStream : nativeStream;
            if (stream == null) {
                // Iris only extends BufferBuilder while a world pass is active. Uploading
                // during resource reload creates a vanilla stride that its world VAO cannot read.
                // GUI and world therefore own separate streams, built in their actual draw scope.
                try {
                    stream = new Stream(writer, extended);
                    if (extended) irisStream = stream; else nativeStream = stream;
                } catch (RuntimeException | OutOfMemoryError error) {
                    if (extended) irisFailed = true; else nativeFailed = true;
                    org.slf4j.LoggerFactory.getLogger("halo").warn(
                        "Halo {} mesh upload failed; using CPU fallback", extended ? "Iris" : "native", error);
                    return false;
                }
            }
            try {
                stream.draw(state, matrix, normal, sort, mirrored, environment);
                return true;
            } catch (RuntimeException | OutOfMemoryError error) {
                // No command is queued until preparation finishes. Retain storage for earlier
                // commands in this frame, and use the existing CPU result for this failed draw.
                if (extended) irisFailed = true; else nativeFailed = true;
                org.slf4j.LoggerFactory.getLogger("halo").warn(
                    "Halo {} mesh preparation failed; using CPU fallback until reload",
                    extended ? "Iris" : "native", error);
                return false;
            }
        }
        public void close() {
            if (nativeStream != null) nativeStream.close();
            if (irisStream != null) irisStream.close();
        }
    }

    /** One vertex layout and its own EBO residency; the CPU sorting workspace can be shared. */
    private static final class Stream implements AutoCloseable {
        private final MeshIndexWriter writer;
        private final boolean expanded;
        private GpuBuffer vertices, source, mirroredSource;
        private record IndexSlot(GpuBuffer buffer, MeshIndexUpload uploaded) {}
        private final FrameIndexSlots<IndexSlot> sortedSlots = new FrameIndexSlots<>(slot -> slot.buffer().close());

        Stream(MeshIndexWriter writer, boolean expanded) {
            this.writer = writer;
            this.expanded = expanded;
            TriangleMesh mesh = writer.mesh();
            try {
                int vertexCount = expanded ? writer.indexCount() : mesh.vertexCount();
                try (var allocator = new ByteBufferBuilder(java.lang.Math.max(256, vertexCount * 64))) {
                    var builder = new BufferBuilder(allocator, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.ENTITY);
                    for (int corner = 0; corner < vertexCount; corner++) {
                        int i = expanded ? mesh.index(corner) : corner;
                        builder.addVertex(mesh.x(i), mesh.y(i), mesh.z(i)).setColor(-1)
                            .setUv(mesh.u(i), mesh.v(i)).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                            .setNormal(mesh.normalX(i), mesh.normalY(i), mesh.normalZ(i));
                    }
                    try (var data = builder.buildOrThrow()) {
                        boolean actualExtended = !data.drawState().format().equals(DefaultVertexFormat.ENTITY);
                        if (actualExtended != expanded) throw new IllegalStateException("Unexpected Iris vertex layout in mesh upload scope");
                        vertices = RenderSystem.getDevice().createBuffer(() -> "Halo mesh", GpuBuffer.USAGE_VERTEX, data.vertexBuffer());
                    }
                }
                source = indices(false);
                mirroredSource = indices(true);
                HaloFrameDiagnostics.geometryUploaded();

            } catch (RuntimeException | OutOfMemoryError error) { close(); throw error; }
        }
        private GpuBuffer indices(boolean mirrored) {
            var data = MemoryUtil.memAlloc(writer.indexCount() * 4);
            try {
                if (expanded) writer.writeExpandedSourceOrder(data.asIntBuffer(), mirrored);
                else writer.writeSourceOrder(data.asIntBuffer(), mirrored);
                return RenderSystem.getDevice().createBuffer(() -> "Halo indices", GpuBuffer.USAGE_INDEX, data);
            } finally { MemoryUtil.memFree(data); }
        }
        void draw(DrawBatch state, Matrix4f matrix, Matrix3f normal, boolean sort,
                  boolean mirrored, RenderEnvironment environment) {
            GpuBuffer index = mirrored ? mirroredSource : source;
            if (sort) {
                long frame = network.azusake.halo.physics.RenderHeadCapture.getFrameId();
                var slot = sortedSlots.acquire(frame, () -> new IndexSlot(
                    RenderSystem.getDevice().createBuffer(() -> "Halo sorted indices", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
                        writer.indexCount() * 4L), new MeshIndexUpload()));
                var dynamic = slot.buffer();
                var uploaded = slot.uploaded();
                long revision = writer.prepareBackToFrontTransform(matrix.m02(), matrix.m12(), matrix.m22(), matrix.m32());
                if (!uploaded.matches(revision, mirrored)) {
                    var data = MemoryUtil.memAlloc(writer.indexCount() * 4);
                    try {
                        if (expanded) writer.writeExpandedPrepared(data.asIntBuffer(), mirrored);
                        else writer.writePrepared(data.asIntBuffer(), mirrored);
                        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(dynamic.slice(), data);
                        uploaded.uploaded(revision, mirrored);
                        HaloFrameDiagnostics.indicesUploaded();
                    } finally { MemoryUtil.memFree(data); }
                }
                index = dynamic;
            }
            HaloDrawSubmitter.draw(state, HaloMeshShader.material(state, environment), vertices, index,
                IndexType.INT, writer.indexCount(), matrix, normal);
        }
        public void close() {
            if (vertices != null) vertices.close();
            if (source != null) source.close();
            if (mirroredSource != null) mirroredSource.close();
            sortedSlots.close();
        }
    }
}
