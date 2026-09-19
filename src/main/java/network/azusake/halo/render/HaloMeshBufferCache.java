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
        // Iris computes tangent/mid-UV per face in its extended BufferBuilder. Until an
        // expanded-corner resident buffer is available, use that exact CPU submission path.
        if (environment == RenderEnvironment.WORLD && network.azusake.halo.physics.OptionalIrisPassDetector.hasShaderPack()) return false;
        if (buffers.generation() != generation) return false;
        var buffer = buffers.get(draw.model()); if (buffer == null) return false;
        var matrix=workspace.modelView(outer,draw);
        var state=new DrawBatch(DrawBatch.Topology.TRIANGLES,List.of(),draw.texture(),true,draw.cull(),draw.blend(),
            draw.depthTest(),draw.depthWrite(),draw.red(),draw.green(),draw.blue(),draw.alpha(),draw.material(),draw.light(),draw.directionalLighting());
        buffer.draw(state,matrix,workspace.normal(matrix),draw.blend(),draw.mirrored(),environment);
        return true;
    }
    boolean drawPrimitive(long generation, PrimitiveDraw draw, Matrix4f outer, RenderEnvironment environment) {
        // Iris computes tangent/mid-UV per face in its extended BufferBuilder. Until an
        // expanded-corner resident buffer is available, use that exact CPU submission path.
        if (environment == RenderEnvironment.WORLD && network.azusake.halo.physics.OptionalIrisPassDetector.hasShaderPack()) return false;
        if (buffers.generation()!=generation) return false;
        var buffer=buffers.get(draw.geometry().id()); if(buffer==null) return false;
        var s=draw.state(); float brightness=HaloDrawSubmitter.quantizedColor(draw.brightness());
        var state=new DrawBatch(DrawBatch.Topology.TRIANGLES,List.of(),s.texture(),s.textured(),s.cull(),s.blend(),s.depthTest(),s.depthWrite(),
            s.red()*brightness,s.green()*brightness,s.blue()*brightness,s.alpha(),s.material(),s.light(),s.directionalLighting());
        var modelView=new Matrix4f(outer).mul(new Matrix4f().set(draw.localToView()));
        var normal=new MeshDrawWorkspace().normal(outer).mul(new Matrix3f().set(draw.normalToView()));
        buffer.draw(state,modelView,normal,false,false,environment);
        return true;
    }
    @Override public void close() { buffers.close(); buffers=GenerationMeshCache.empty(); }
    private static final class MeshBuffer implements GenerationMeshCache.Owned {
        private final MeshIndexWriter writer;
        private GpuBuffer vertices, source, mirroredSource, dynamic;
        private final MeshIndexUpload uploaded = new MeshIndexUpload();
        MeshBuffer(TriangleMesh mesh) {
            writer=new MeshIndexWriter(mesh);
            try {
                try(var allocator=new ByteBufferBuilder(java.lang.Math.max(256,mesh.vertexCount()*64))) {
                    var builder=new BufferBuilder(allocator,VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.ENTITY);
                    for(int i=0;i<mesh.vertexCount();i++) builder.addVertex(mesh.x(i),mesh.y(i),mesh.z(i)).setColor(-1)
                        .setUv(mesh.u(i),mesh.v(i)).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                        .setNormal(mesh.normalX(i),mesh.normalY(i),mesh.normalZ(i));
                    try(var data=builder.buildOrThrow()) { vertices=RenderSystem.getDevice().createBuffer(()->"Halo mesh",GpuBuffer.USAGE_VERTEX,data.vertexBuffer()); }
                }
                source=indices(false); mirroredSource=indices(true);
                dynamic=RenderSystem.getDevice().createBuffer(()->"Halo sorted indices",GpuBuffer.USAGE_INDEX|GpuBuffer.USAGE_COPY_DST,writer.indexCount()*4L);
            } catch(RuntimeException|OutOfMemoryError e) { close(); throw e; }
        }
        private GpuBuffer indices(boolean mirrored) {
            var data=MemoryUtil.memAlloc(writer.indexCount()*4);
            try { writer.writeSourceOrder(data.asIntBuffer(),mirrored);return RenderSystem.getDevice().createBuffer(()->"Halo indices",GpuBuffer.USAGE_INDEX,data); }
            finally { MemoryUtil.memFree(data); }
        }
        void draw(DrawBatch state,Matrix4f matrix,Matrix3f normal,boolean sort,boolean mirrored,RenderEnvironment environment) {
            GpuBuffer index=mirrored?mirroredSource:source;
            if(sort) {
                long revision=writer.prepareBackToFrontTransform(matrix.m02(),matrix.m12(),matrix.m22(),matrix.m32());
                if(!uploaded.matches(revision,mirrored)) {
                    var data=MemoryUtil.memAlloc(writer.indexCount()*4);
                    try { writer.writePrepared(data.asIntBuffer(),mirrored);RenderSystem.getDevice().createCommandEncoder().writeToBuffer(dynamic.slice(),data);uploaded.uploaded(revision,mirrored); }
                    finally { MemoryUtil.memFree(data); }
                }
                index=dynamic;
            }
            HaloDrawSubmitter.draw(state,HaloMeshShader.material(state,environment),vertices,index,VertexFormat.IndexType.INT,writer.indexCount(),matrix,normal);
        }
        public void close() { if(vertices!=null)vertices.close();if(source!=null)source.close();if(mirroredSource!=null)mirroredSource.close();if(dynamic!=null)dynamic.close(); }
    }
}
