package network.azusake.halo.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import network.azusake.halo.core.render.*;
import network.azusake.halo.core.Identifier;
import org.joml.*;
import org.lwjgl.system.MemoryUtil;
import java.util.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import network.azusake.halo.compat.iris.IrisMeshBridge;
import network.azusake.halo.physics.OptionalIrisPassDetector;

/** Native GPU buffers, scoped to visual generation. Failed uploads use the completed frame's CPU fallback. */
final class HaloMeshBufferCache implements AutoCloseable {
    private GenerationMeshCache<MeshBuffer> buffers = GenerationMeshCache.empty();
    static HaloMeshBufferCache empty() { return new HaloMeshBufferCache(); }
    HaloMeshBufferCache updated(VisualResources resources) { return updated(resources, Set.of()); }
    HaloMeshBufferCache updated(VisualResources resources, Set<Identifier> quads) {
        buffers = buffers.updated(resources.generation(), resources.meshes(), (id, mesh) -> new MeshBuffer(mesh, quads.contains(id)),
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
        private final TriangleMesh mesh;
        private final boolean sourceQuad;
        private final MeshIndexWriter writer;
        private final LazyMeshResource<ResidentBuffer> nativeBuffer = new LazyMeshResource<>();
        private final LazyMeshResource<ResidentBuffer> irisBuffer = new LazyMeshResource<>();

        MeshBuffer(TriangleMesh mesh, boolean sourceQuad) {
            this.mesh = mesh;
            this.sourceQuad = sourceQuad;
            writer = new MeshIndexWriter(mesh);
        }

        boolean draw(DrawBatch state, Matrix4f matrix, Matrix3f normal, boolean sort,
                     boolean mirrored, RenderEnvironment environment) {
            boolean iris = environment == RenderEnvironment.WORLD && OptionalIrisPassDetector.hasShaderPack();
            if (iris && Boolean.getBoolean("halo.mesh.forceExpanded")) return false;
            var slot = iris ? irisBuffer : nativeBuffer;
            var resident = slot.get(() -> new ResidentBuffer(mesh, writer, new MeshVertexLayout(iris, sourceQuad)),
                error -> org.slf4j.LoggerFactory.getLogger("halo").warn(
                    "Cannot upload {} Halo mesh ({} triangles); using CPU fallback for this generation",
                    iris ? "Iris" : "native", mesh.triangleCount(), error));
            if (resident == null) return false;
            resident.draw(state, matrix, normal, sort, mirrored, environment);
            return true;
        }

        public void close() { nativeBuffer.close(); irisBuffer.close(); }
    }

    private static final class ResidentBuffer implements GenerationMeshCache.Owned {
        private final MeshIndexWriter writer;
        private final MeshVertexLayout layout;
        private GpuBuffer vertices, source, mirroredSource, dynamic;
        private ByteBuffer indexBytes;
        private IntBuffer indexInts;
        private final MeshIndexUpload uploaded = new MeshIndexUpload();
        private final float[] sortView = new float[16];

        ResidentBuffer(TriangleMesh mesh, MeshIndexWriter writer, MeshVertexLayout layout) {
            this.writer = writer;
            this.layout = layout;
            try {
                int vertexCount = layout.vertexCount(mesh);
                try (var allocator = new ByteBufferBuilder(java.lang.Math.max(256, java.lang.Math.multiplyExact(vertexCount, 64)))) {
                    // Iris only extends builders during world drawing. Upload lazily here, never
                    // during resource reload or pipeline construction, and validate the actual layout.
                    java.util.function.Supplier<MeshData> build = () -> {
                        var builder = new BufferBuilder(allocator,
                            layout.iris() && layout.sourceQuad() ? VertexFormat.Mode.QUADS : VertexFormat.Mode.TRIANGLES,
                            DefaultVertexFormat.ENTITY);
                        for (int corner = 0; corner < vertexCount; corner++) {
                            int vertex = layout.vertexAt(mesh, corner);
                            builder.addVertex(mesh.x(vertex), mesh.y(vertex), mesh.z(vertex)).setColor(-1)
                                .setUv(mesh.u(vertex), mesh.v(vertex)).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                                .setNormal(mesh.normalX(vertex), mesh.normalY(vertex), mesh.normalZ(vertex));
                        }
                        return builder.buildOrThrow();
                    };
                    try (var data = layout.iris() ? build.get() : IrisMeshBridge.withoutVertexExtension(build)) {
                        if (layout.iris()) IrisMeshBridge.requireExtendedEntityFormat(data.drawState().format());
                        else if (data.drawState().format() != DefaultVertexFormat.ENTITY)
                            throw new IllegalStateException("Unexpected native Halo vertex format");
                        vertices = RenderSystem.getDevice().createBuffer(() -> "Halo resident mesh", GpuBuffer.USAGE_VERTEX, data.vertexBuffer());
                        MeshRenderMetrics.vertexUpload(data.vertexBuffer().remaining());
                    }
                }
                indexBytes = MemoryUtil.memAlloc(java.lang.Math.multiplyExact(writer.indexCount(), Integer.BYTES));
                indexInts = indexBytes.asIntBuffer();
                source = indices(false);
                mirroredSource = indices(true);
                dynamic = RenderSystem.getDevice().createBuffer(() -> "Halo sorted indices",
                    GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, indexBytes.capacity());
            } catch (RuntimeException | OutOfMemoryError error) { close(); throw error; }
        }

        private GpuBuffer indices(boolean mirrored) {
            indexInts.clear();
            layout.sourceIndices(writer, indexInts, mirrored);
            indexBytes.clear();
            return RenderSystem.getDevice().createBuffer(() -> "Halo indices", GpuBuffer.USAGE_INDEX, indexBytes);
        }

        void draw(DrawBatch state, Matrix4f matrix, Matrix3f normal, boolean sort, boolean mirrored, RenderEnvironment environment) {
            GpuBuffer index = mirrored ? mirroredSource : source;
            if (sort) {
                long revision = writer.prepareBackToFrontView(matrix.get(sortView), environment == RenderEnvironment.WORLD);
                if (!uploaded.matches(revision, mirrored)) {
                    indexInts.clear();
                    layout.sortedIndices(writer, indexInts, mirrored);
                    indexBytes.clear();
                    RenderSystem.getDevice().createCommandEncoder().writeToBuffer(dynamic.slice(), indexBytes);
                    uploaded.uploaded(revision, mirrored);
                    MeshRenderMetrics.indexUpload(indexBytes.capacity());
                }
                index = dynamic;
            }
            HaloDrawSubmitter.draw(state, HaloMeshShader.material(state, environment), vertices, index,
                VertexFormat.IndexType.INT, writer.indexCount(), matrix, normal);
            MeshRenderMetrics.residentDraw();
        }

        public void close() {
            if (vertices != null) vertices.close();
            if (source != null) source.close();
            if (mirroredSource != null) mirroredSource.close();
            if (dynamic != null) dynamic.close();
            if (indexBytes != null) MemoryUtil.memFree(indexBytes);
        }
    }
}
