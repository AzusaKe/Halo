package network.azusake.halo.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import network.azusake.halo.core.render.*;
import network.azusake.halo.core.render.DrawBatch.Vertex;
import network.azusake.halo.render.HaloMeshShader.PreparedProgram;
import org.joml.Matrix4f;
import static network.azusake.halo.platform.PlatformTypes.game;

/** Shared GPU submission. Scheduling and render-environment selection belong to the caller. */
final class HaloDrawSubmitter {
    record Submission(long generation, VisualResources visuals, List<MeshDraw> draws,
                      Matrix4f modelView, Matrix4f projection) {}
    private HaloDrawSubmitter() {}
    static void submitMeshes(Minecraft client, Submission pending, HaloMeshBufferCache meshBuffers, RenderEnvironment environment) {
        if (pending.draws().isEmpty()) return;
        try (HaloRenderState ignored = new HaloRenderState()) {
            // Direct VBO submission does not pass through a vanilla RenderLayer,
            // so its LIGHTMAP render phase cannot bind Sampler2 for us.
            client.gameRenderer.lightTexture().turnOnLightLayer();
            // A single draw cannot amortize either workspace or material lookup caches.
            boolean reuse = pending.draws().size() > 1;
            MeshDrawWorkspace workspace = reuse ? new MeshDrawWorkspace() : null;
            HaloMeshShader.Submission materials = reuse ? new HaloMeshShader.Submission(client, environment) : null;
            for (MeshDraw draw : pending.draws()) {
                RenderType renderType = draw.directionalLighting()
                    ? HaloForgeRenderTypes.lit(draw.texture(), draw.blend()) : null;
                if (renderType != null) renderType.setupRenderState();
                try {
                    applyState(draw.cull(), draw.blend(), draw.depthTest(), draw.depthWrite(),
                        draw.red(), draw.green(), draw.blue(), draw.alpha());
                    var prepared = materials == null ? null : materials.bind(draw);
                    var shader = materials == null ? HaloMeshShader.bind(client, draw, environment)
                        : prepared == null ? null : prepared.shader;
                    if (shader == null) continue;
                    if (!meshBuffers.draw(pending.generation(), draw, pending.modelView(), pending.projection(), shader, workspace, prepared)) {
                        var fallback = new FrameOutput(pending.generation(), List.of(), List.of(draw))
                            .expandedBatches(pending.visuals());
                        for (DrawBatch batch : fallback) submit(client, batch, environment);
                        if (materials != null) materials.invalidate();
                    }
                } finally {
                    if (renderType != null) renderType.clearRenderState();
                }
            }
        }
    }

    static void submitBatches(Minecraft client, List<DrawBatch> batches, RenderEnvironment environment) {
        if (batches.isEmpty()) return;
        try (HaloRenderState ignored = new HaloRenderState()) {
            // Tessellator submission likewise runs outside a RenderLayer. Bind
            // the current 16x16 vanilla lightmap before any lightmapped batch.
            client.gameRenderer.lightTexture().turnOnLightLayer();
            for (int start = 0; start < batches.size();) {
                int end = LegacyBatchRuns.end(batches, start);
                submitRun(client, batches, start, end, environment);
                start = end;
            }
        }

    }
    private static void submit(Minecraft client, DrawBatch b, RenderEnvironment environment) {
        submitRun(client, List.of(b), 0, 1, environment);
    }
    private static void submitRun(Minecraft client, List<DrawBatch> batches, int start, int end, RenderEnvironment environment) {
        DrawBatch b = batches.get(start);
        RenderType renderType = b.directionalLighting() ? HaloForgeRenderTypes.lit(b.texture(), b.blend()) : null;
        if (renderType != null) renderType.setupRenderState();
        try {
            applyState(b.cull(), b.blend(), b.depthTest(), b.depthWrite(), b.red(), b.green(), b.blue(), b.alpha());
            boolean nativeLight = b.light().available();
            if (b.material() instanceof MaterialState.Mesh mesh) {
                if (!HaloMeshShader.bind(client, b, mesh, environment)) return;
            } else if (b.directionalLighting() || b.textured() && nativeLight) {
                if (!HaloMeshShader.bindLegacy(client, b, environment)) return;
            } else if(b.textured()) {
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                RenderSystem.setShaderTexture(0,client.getTextureManager().getTexture(game(b.texture())).getId());
            } else if (nativeLight) RenderSystem.setShader(GameRenderer::getPositionColorLightmapShader);
            else RenderSystem.setShader(GameRenderer::getPositionColorShader);
            var builder=Tesselator.getInstance().getBuilder();
            VertexFormat format = b.directionalLighting() ? DefaultVertexFormat.NEW_ENTITY
                : b.textured() ? DefaultVertexFormat.POSITION_TEX_COLOR
                : nativeLight ? DefaultVertexFormat.POSITION_COLOR_LIGHTMAP : DefaultVertexFormat.POSITION_COLOR;
            builder.begin(b.topology()==DrawBatch.Topology.QUADS?VertexFormat.Mode.QUADS:VertexFormat.Mode.TRIANGLES,
                format);
            int packedLight = packLight(b.light());
            for (int batch = start; batch < end; batch++) for (var v : batches.get(batch).vertices()) {
                builder.vertex(v.x(),v.y(),v.z());
                if (b.directionalLighting()) {
                    builder.color(v.red(),v.green(),v.blue(),v.alpha());
                    builder.uv(b.textured() ? v.u() : 0f, b.textured() ? v.v() : 0f);
                    builder.overlayCoords(OverlayTexture.NO_OVERLAY);
                    builder.uv2(packedLight);
                    builder.normal(v.normalX(), v.normalY(), v.normalZ());
                } else {
                    if(b.textured())builder.uv(v.u(),v.v());
                    builder.color(v.red(),v.green(),v.blue(),v.alpha());
                    if (!b.textured() && nativeLight) builder.uv2(packedLight);
                }
                builder.endVertex();
            }
            Tesselator.getInstance().end();
        } finally {
            if (renderType != null) renderType.clearRenderState();
        }
    }
    static void submitPrimitives(Minecraft client, FrameOutput output, HaloMeshBufferCache buffers,
                                 RenderEnvironment environment) {
        if (output.primitiveDraws().isEmpty()) return;
        try (HaloRenderState ignored = new HaloRenderState(true)) {
            client.gameRenderer.lightTexture().turnOnLightLayer();
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
                RenderType renderType = state.directionalLighting()
                    ? HaloForgeRenderTypes.lit(state.texture(), state.blend()) : null;
                if (renderType != null) renderType.setupRenderState();
                try {
                    applyState(state.cull(), state.blend(), state.depthTest(), state.depthWrite(),
                        state.red(), state.green(), state.blue(), state.alpha());
                    if (!HaloMeshShader.bindLegacy(client, state, environment)) continue;
                    buffers.drawPrimitive(output.visualGeneration(), draw, modelView, projection, RenderSystem.getShader());
                } finally {
                    if (renderType != null) renderType.clearRenderState();
                }
            }
        }
    }

    /** BufferBuilder's float color setter converts to an unsigned byte before shader modulation. */
    static float quantizedColor(float value) { return ((int)(value * 255f) & 255) / 255f; }

    static int packLight(LightSample light) {
        LightSample sample = light.available() ? light : LightSample.FULL_BRIGHT;
        return net.minecraft.client.renderer.LightTexture.pack(sample.block(), sample.sky());
    }
    private static void applyState(boolean cull, boolean blend, boolean depthTest, boolean depthWrite,
                                   float red, float green, float blue, float alpha) {
        if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
        if(blend){RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();}else RenderSystem.disableBlend();
        if(depthTest)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(depthWrite);RenderSystem.setShaderColor(red,green,blue,alpha);
    }
}
