package network.azusake.halo.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import network.azusake.halo.core.render.*;
import org.joml.Matrix4f;
import static network.azusake.halo.platform.PlatformTypes.game;

/** Shared GPU submission. Scheduling and render-environment selection belong to the caller. */
final class HaloDrawSubmitter {
    record Submission(long generation, VisualResources visuals, List<MeshDraw> draws,
                      Matrix4f modelView, Matrix4f projection) {}
    private HaloDrawSubmitter() {}
    static void submitMeshes(MinecraftClient client, Submission pending, HaloMeshBufferCache meshBuffers, RenderEnvironment environment) {
        if (pending.draws().isEmpty()) return;
        try (HaloRenderState ignored = new HaloRenderState()) {
            // Direct VBO submission does not pass through a vanilla RenderLayer,
            // so its LIGHTMAP render phase cannot bind Sampler2 for us.
            client.gameRenderer.getLightmapTextureManager().enable();
            // A single draw cannot amortize either workspace or material lookup caches.
            boolean reuse = pending.draws().size() > 1;
            MeshDrawWorkspace workspace = reuse ? new MeshDrawWorkspace() : null;
            HaloMeshShader.Submission materials = reuse ? new HaloMeshShader.Submission(client, environment) : null;
            for (MeshDraw draw : MeshDrawViewSorter.backToFront(
                    pending.draws(), pending.visuals(), pending.modelView())) {
                boolean hasMask = draw.material().mask() != null;
                try (var entity = HaloMeshShader.openEntityLayer(draw.texture(), draw.blend(), environment,
                        draw.directionalLighting(), hasMask, draw.light())) {
                    // RenderLayer phases select the pack's entity program and auxiliary
                    // textures; the command's exact depth/blend/cull contract remains authoritative.
                    applyState(draw.cull(), draw.blend(), draw.depthTest(), draw.depthWrite(),
                        draw.red(), draw.green(), draw.blue(), draw.alpha());
                    var prepared = entity == null && materials != null ? materials.bind(draw) : null;
                    var shader = entity != null ? entity.shader
                        : materials == null ? HaloMeshShader.bind(client, draw, environment)
                        : prepared == null ? null : prepared.shader;
                    if (shader == null) continue;
                    if (!meshBuffers.draw(pending.generation(), draw, pending.modelView(), pending.projection(),
                            shader, workspace, prepared, entity != null)) {
                        var fallback = new FrameOutput(pending.generation(), List.of(), List.of(draw))
                            .expandedBatches(pending.visuals(), pending.modelView().get(new float[16]), pending.projection().m33() == 0);
                        for (DrawBatch batch : fallback) submit(client, batch, environment);
                        if (materials != null) materials.invalidate();
                    }
                }
            }
        }
    }

    static void submitBatches(MinecraftClient client, List<DrawBatch> batches, RenderEnvironment environment) {
        if (batches.isEmpty()) return;
        try (HaloRenderState ignored = new HaloRenderState()) {
            // Tessellator submission likewise runs outside a RenderLayer. Bind
            // the current 16x16 vanilla lightmap before any lightmapped batch.
            client.gameRenderer.getLightmapTextureManager().enable();
            for (int start = 0; start < batches.size();) {
                int end = LegacyBatchRuns.end(batches, start);
                submitRun(client, batches, start, end, environment);
                start = end;
            }
        }

    }
    private static void submit(MinecraftClient client, DrawBatch b, RenderEnvironment environment) {
        submitRun(client, List.of(b), 0, 1, environment);
    }
    private static void submitRun(MinecraftClient client, List<DrawBatch> batches, int start, int end, RenderEnvironment environment) {
        DrawBatch b = batches.get(start);
        boolean nativeLight = b.light().available();
        boolean hasMask = b.material() instanceof MaterialState.Mesh mesh && mesh.mask() != null;
        try (var entity = HaloMeshShader.openEntityLayer(b.texture(), b.blend(), environment,
                b.directionalLighting(), hasMask, b.light())) {
            applyState(b.cull(), b.blend(), b.depthTest(), b.depthWrite(), b.red(), b.green(), b.blue(), b.alpha());
            if (entity == null) {
                if (b.material() instanceof MaterialState.Mesh mesh) {
                    if (!HaloMeshShader.bind(client, b, mesh, environment)) return;
                } else if (b.directionalLighting() || b.textured() && nativeLight) {
                    if (!HaloMeshShader.bindLegacy(client, b, environment)) return;
                } else if(b.textured()) {
                    RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
                    RenderSystem.setShaderTexture(0,client.getTextureManager().getTexture(game(b.texture())).getGlId());
                } else if (nativeLight) RenderSystem.setShader(GameRenderer::getPositionColorLightmapProgram);
                else RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            }
            VertexFormat format = b.directionalLighting() ? VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
                : b.textured() ? VertexFormats.POSITION_TEXTURE_COLOR
                : nativeLight ? VertexFormats.POSITION_COLOR_LIGHT : VertexFormats.POSITION_COLOR;
            var builder=Tessellator.getInstance().begin(
                b.topology()==DrawBatch.Topology.QUADS?VertexFormat.DrawMode.QUADS:VertexFormat.DrawMode.TRIANGLES,
                format);
            int packedLight = packLight(b.light());
            for (int batch = start; batch < end; batch++) for (var v : batches.get(batch).vertices()) {
                builder.vertex(v.x(),v.y(),v.z());
                if (b.directionalLighting()) {
                    builder.color(v.red(),v.green(),v.blue(),v.alpha());
                    builder.texture(b.textured() ? v.u() : 0f, b.textured() ? v.v() : 0f);
                    builder.overlay(OverlayTexture.DEFAULT_UV);
                    builder.light(packedLight);
                    builder.normal(v.normalX(), v.normalY(), v.normalZ());
                } else {
                    if(b.textured())builder.texture(v.u(),v.v());
                    builder.color(v.red(),v.green(),v.blue(),v.alpha());
                    if (!b.textured() && nativeLight) builder.light(packedLight);
                }
            }
            BufferRenderer.drawWithGlobalProgram(builder.end());
        }
    }
    static void submitPrimitives(MinecraftClient client, FrameOutput output, HaloMeshBufferCache buffers,
                                 RenderEnvironment environment) {
        if (output.primitiveDraws().isEmpty()) return;
        try (HaloRenderState ignored = new HaloRenderState(true)) {
            client.gameRenderer.getLightmapTextureManager().enable();
            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
            for (PrimitiveDraw draw : output.primitiveDraws()) {
                DrawBatch state = draw.state();
                // Older/non-native and untextured flat shader formats retain their established path.
                if (!buffers.contains(output.visualGeneration(), draw.geometry().id()) || !state.textured()
                        || !(state.directionalLighting() || state.light().available())) {
                    submit(client, draw.expand(), environment);
                    continue;
                }
                try (var entity = HaloMeshShader.openEntityLayer(state.texture(), state.blend(), environment,
                        state.directionalLighting(), false, state.light())) {
                    applyState(state.cull(), state.blend(), state.depthTest(), state.depthWrite(),
                        state.red(), state.green(), state.blue(), state.alpha());
                    if (entity == null && !HaloMeshShader.bindLegacy(client, state, environment)) continue;
                    buffers.drawPrimitive(output.visualGeneration(), draw, modelView, projection,
                        entity == null ? RenderSystem.getShader() : entity.shader, entity != null);
                }
            }
        }
    }

    /** BufferBuilder's float color setter converts to an unsigned byte before shader modulation. */
    static float quantizedColor(float value) { return ((int)(value * 255f) & 255) / 255f; }

    static int packLight(LightSample light) {
        LightSample sample = light.available() ? light : LightSample.FULL_BRIGHT;
        return net.minecraft.client.render.LightmapTextureManager.pack(sample.block(), sample.sky());
    }
    private static void applyState(boolean cull, boolean blend, boolean depthTest, boolean depthWrite,
                                   float red, float green, float blue, float alpha) {
        if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
        if(blend){RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();}else RenderSystem.disableBlend();
        if(depthTest)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(depthWrite);RenderSystem.setShaderColor(red,green,blue,alpha);
    }
}
