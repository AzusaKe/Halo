package network.azusake.halo.api.client.preview.v1;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.core.Vec3d;
import network.azusake.halo.core.render.*;
import network.azusake.halo.core.runtime.PreviewSession;
import network.azusake.halo.platform.HaloClientState;
import network.azusake.halo.render.HaloMeshResources;
import network.azusake.halo.render.HaloRenderer;
import network.azusake.halo.render.PlayerPreviewCapture;

/** Minecraft 1.20.1 client facade. All calls belong to the render thread. */
public final class HaloPreviewApi {
    private HaloPreviewApi() {}

    /** For explicit-anchor integrations. The caller owns this session and supplies PreviewFrame values. */
    public static PreviewSession openPreview() { return HaloClientState.get().openPreview(); }

    /** Submit after the host's model buffers are flushed, while its projection and GUI lighting are active. */
    public static void draw(PreviewSession session, PreviewFrame frame) {
        RenderSystem.assertOnRenderThread();
        if (!HaloModConfigStore.get().isPlayerPreviewHaloEnabled()) return;
        HaloRenderer.getInstance().submitPreview(session.render(frame), frame.visuals());
    }

    /**
     * Wrap a vanilla player renderer at its final preview root. Flushes model buffers before drawing
     * the halo. Callers using InventoryScreen.drawEntity already receive this behavior automatically.
     */
    public static void renderPlayer(DrawContext context, LivingEntity entity, Runnable renderEntity) {
        if (!(entity instanceof PlayerEntity)) { renderEntity.run(); return; }
        try (var capture = PlayerPreviewCapture.open(entity, context.getMatrices().peek().getPositionMatrix())) {
            renderEntity.run();
            context.draw();
            if (capture.head() == null || !entity.isAlive() || !HaloModConfigStore.get().isPlayerPreviewHaloEnabled()) return;
            var assets = HaloMeshResources.snapshot();
            try (var session = openPreview()) {
                draw(session, new PreviewFrame(entity.getUuid(), entity.getId(), capture.head(),
                    new FrameScene.CameraSample(new Vec3d(0, 0, 0), new Vec3d(0, -1, 0), new Vec3d(1, 0, 0)),
                    capture.root().get(new float[16]), System.currentTimeMillis(), System.nanoTime(),
                    LightSample.FULL_BRIGHT, id -> true, assets.visuals()));
            }
        }
    }
}
