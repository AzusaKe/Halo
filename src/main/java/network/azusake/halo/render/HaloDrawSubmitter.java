package network.azusake.halo.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
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
final class HaloDrawSubmitter {
    private HaloDrawSubmitter() {}
    static void submitBatches(Minecraft client, List<DrawBatch> batches, RenderEnvironment environment, Matrix4f outer) {
        for (DrawBatch batch : batches) {
            if (batch.vertices().isEmpty()) continue;
            var material = HaloMeshShader.material(batch, environment);
            try (var allocator = new ByteBufferBuilder(java.lang.Math.max(256, batch.vertices().size() * 64))) {
                var builder = new BufferBuilder(allocator, material.type().mode(), material.type().format());
                for (var v : batch.vertices()) builder.addVertex(v.x(), v.y(), v.z())
                    .setColor(v.red(), v.green(), v.blue(), v.alpha()).setUv(v.u(), v.v())
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packLight(batch.light()))
                    .setNormal(v.normalX(), v.normalY(), v.normalZ());
                try (var mesh = builder.buildOrThrow()) {
                    var format = mesh.drawState().format();
                    var vertex = format.uploadImmediateVertexBuffer(mesh.vertexBuffer());
                    MeshRenderMetrics.expandedDraw();
                    MeshRenderMetrics.vertexUpload(mesh.vertexBuffer().remaining());
                    var sequential = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
                    var index = sequential.getBuffer(mesh.drawState().indexCount());
                    draw(batch, material, vertex, index, sequential.type(), mesh.drawState().indexCount(),
                        outer, new MeshDrawWorkspace().normal(outer));
                }
            }
        }
    }
    static void draw(DrawBatch batch, HaloMeshShader.Material material, GpuBuffer vertices, GpuBuffer indices,
                     VertexFormat.IndexType indexType, int count, Matrix4f modelView, Matrix3f normal) {
        var textures = material.setup().getTextures();
        var target = material.type().outputTarget().getRenderTarget();
        var color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
        var depth = target.useDepth ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView()) : null;
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
            try (var uniforms = RenderSystem.getDevice().createBuffer(() -> "Halo material", GpuBuffer.USAGE_UNIFORM, bytes);
                 var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Halo", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
                pass.setPipeline(material.type().pipeline());
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transform);
                pass.setUniform("HaloMaterial", uniforms);
                var scissor=RenderSystem.getScissorStateForRenderTypeDraws();
                if(scissor.enabled()) pass.enableScissor(scissor.x(),scissor.y(),scissor.width(),scissor.height());
                for (var entry : textures.entrySet())
                    pass.bindTexture(entry.getKey(), entry.getValue().textureView(), entry.getValue().sampler());
                pass.setVertexBuffer(0, vertices); pass.setIndexBuffer(indices, indexType); pass.drawIndexed(0,0,count,1);
            }
        }
    }
    static float quantizedColor(float value) { return ((int)(value * 255f) & 255) / 255f; }
    static int packLight(LightSample light) {
        var sample=light.available()?light:LightSample.FULL_BRIGHT;
        return (sample.block() << 4) | (sample.sky() << 20);
    }
}
