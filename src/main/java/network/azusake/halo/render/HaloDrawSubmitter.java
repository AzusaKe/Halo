package network.azusake.halo.render;

import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import network.azusake.halo.core.render.*;
import org.joml.*;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteOrder;
import java.util.*;

/** Lexically owned buffers and render passes preserve draw ordering and external render state. */
public final class HaloDrawSubmitter {
    private static com.mojang.renderpearl.api.commands.RenderPass activePass;
    private static PreparedSubmission preparing;
    private static final List<PreparedSubmission> FRAME_RESOURCES = new ArrayList<>();
    public static final class PreparedSubmission implements AutoCloseable {
        private final List<java.util.function.Consumer<com.mojang.renderpearl.api.commands.RenderPass>> draws = new ArrayList<>();
        private final List<GpuBuffer> owned = new ArrayList<>();
        public void submit(com.mojang.renderpearl.api.commands.RenderPass pass) { draws.forEach(draw -> draw.accept(pass)); }
        public void submitWorld() {
            if (draws.isEmpty()) return;
            var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Halo", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
                submit(pass);
            }
        }
        @Override public void close() { owned.forEach(GpuBuffer::close); owned.clear(); draws.clear(); }
    }
    public static PreparedSubmission prepare(Runnable build) {
        var result = new PreparedSubmission();
        var previous = preparing;
        preparing = result;
        try { build.run(); FRAME_RESOURCES.add(result); return result; }
        catch (RuntimeException | Error error) { result.close(); throw error; }
        finally { preparing = previous; }
    }
    public static void endFrame() { FRAME_RESOURCES.forEach(PreparedSubmission::close); FRAME_RESOURCES.clear(); }
    public static void withPass(com.mojang.renderpearl.api.commands.RenderPass pass, Runnable draw) {
        var previous = activePass;
        activePass = pass;
        try { draw.run(); } finally { activePass = previous; }
    }
    static void submit(PreparedSubmission submission) {
        if (activePass == null) submission.submitWorld(); else submission.submit(activePass);
    }
    private HaloDrawSubmitter() {}
    static void submitBatches(Minecraft client, List<DrawBatch> batches, RenderEnvironment environment, Matrix4f outer) {
        var normal = new MeshDrawWorkspace().normal(outer);
        for (int start = 0; start < batches.size();) {
            DrawBatch batch = batches.get(start);
            int end = LegacyBatchRuns.end(batches, start);
            int vertices = 0;
            for (int i = start; i < end; i++) vertices += batches.get(i).vertices().size();
            if (vertices != 0) {
                var material = HaloMeshShader.material(batch, environment);
                try (var allocator = new ByteBufferBuilder(java.lang.Math.max(256, vertices * 64))) {
                    var builder = new BufferBuilder(allocator, material.type().primitiveTopology(), material.type().format());
                    for (int i = start; i < end; i++) for (var v : batches.get(i).vertices())
                        builder.addVertex(v.x(), v.y(), v.z())
                            .setColor(v.red(), v.green(), v.blue(), v.alpha()).setUv(v.u(), v.v())
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packLight(batch.light()))
                            .setNormal(v.normalX(), v.normalY(), v.normalZ());
                    try (var mesh = builder.buildOrThrow()) {
                        var vertex = RenderSystem.getDevice().createBuffer(() -> "Halo transient vertices", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                        preparing.owned.add(vertex);
                        var sequential = RenderSystem.getSequentialBuffer(mesh.drawState().primitiveTopology());
                        var index = sequential.getBuffer(mesh.drawState().indexCount());
                        draw(batch, material, vertex, index, sequential.type(), mesh.drawState().indexCount(), outer, normal, sequential);
                    }
                }
            }
            start = end;
        }
    }
    static void draw(DrawBatch batch, HaloMeshShader.Material material, GpuBuffer vertices, GpuBuffer indices,
                     IndexType indexType, int count, Matrix4f modelView, Matrix3f normal) {
        draw(batch, material, vertices, indices, indexType, count, modelView, normal, null);
    }
    private static void draw(DrawBatch batch, HaloMeshShader.Material material, GpuBuffer vertices, GpuBuffer indices,
                     IndexType indexType, int count, Matrix4f modelView, Matrix3f normal,
                     RenderSystem.AutoStorageIndexBuffer sequential) {
        if (preparing == null) throw new IllegalStateException("Halo GPU resources must be prepared before the render pass");
        var textures = material.type().prepare().textures();
        var pipeline = RenderSystem.getCompiledPipeline(material.type().pipeline());
        var transform = RenderSystem.getDynamicUniforms().writeTransform(modelView,
            new Vector4f(batch.red(), batch.green(), batch.blue(), batch.alpha()), new Vector3f(), new Matrix4f());
        try (var stack = MemoryStack.stackPush()) {
            var bytes = stack.calloc(80).order(ByteOrder.nativeOrder());
            // std140: mat3 occupies three vec4 columns, followed by two vec4 material parameters.
            for (int c=0;c<3;c++) for(int row=0;row<3;row++) bytes.putFloat(c*16+row*4, normal.get(c,row));
            var mask = batch.material() instanceof MaterialState.Mesh m ? m.mask() : null;
            bytes.putFloat(48, mask == null ? 0 : 1).putFloat(52, mask != null && mask.mode() == MaterialState.MaskMode.STEP ? 1 : 0)
                .putFloat(56, mask == null ? 0 : mask.threshold()).putFloat(60, batch.material() instanceof MaterialState.Legacy ? 0.1f : 0);
            var light = batch.light().available() ? batch.light() : LightSample.FULL_BRIGHT;
            bytes.putFloat(64, mask == null ? 0 : mask.offsetU()).putFloat(68, mask == null ? 0 : mask.offsetV())
                .putFloat(72, light.block()).putFloat(76, light.sky());
            var uniforms = RenderSystem.getDevice().createBuffer(() -> "Halo material", GpuBuffer.USAGE_UNIFORM, bytes);
            preparing.owned.add(uniforms);
            var scissor = RenderSystem.getScissorStateForRenderTypeDraws();
            boolean scissorEnabled = scissor.enabled();
            int sx = scissor.x(), sy = scissor.y(), sw = scissor.width(), sh = scissor.height();
            preparing.draws.add(pass -> {
                pass.setPipeline(pipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transform);
                pass.setUniform("HaloMaterial", uniforms);
                if (scissorEnabled) pass.enableScissor(sx, sy, sw, sh); else pass.disableScissor();
                for (var entry : textures) pass.setUniform(entry.name(), entry.textureView(), entry.sampler());
                pass.setVertexBuffer(0, vertices.slice());
                // A later preparation may grow Minecraft's shared sequential buffer and close
                // its previous allocation. Resolve the current allocation/type without uploading.
                pass.setIndexBuffer(sequential == null ? indices : sequential.getBuffer(),
                    sequential == null ? indexType : sequential.type());
                pass.drawIndexed(count, 1, 0, 0, 0);
            });
        }
    }
    static float quantizedColor(float value) { return ((int)(value * 255f) & 255) / 255f; }
    static int packLight(LightSample light) {
        var sample=light.available()?light:LightSample.FULL_BRIGHT;
        return (sample.block() << 4) | (sample.sky() << 20);
    }
}
