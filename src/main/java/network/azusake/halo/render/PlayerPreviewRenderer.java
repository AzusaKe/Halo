package network.azusake.halo.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.core.Vec3d;
import network.azusake.halo.core.render.*;
import network.azusake.halo.core.runtime.PreviewSession;
import network.azusake.halo.core.runtime.PreviewOptions;
import network.azusake.halo.platform.HaloClientState;

/** Internal Minecraft preview integration; public only for adapter lifecycle and Mixin hooks. */
public final class PlayerPreviewRenderer {
    private static final PreviewSessionPool VIEWS = new PreviewSessionPool(options -> HaloClientState.get().openPreview(options));
    private PlayerPreviewRenderer() {}

    public static void beginFrame() {
        HaloRenderer.getInstance().beginFrame();
        var client = net.minecraft.client.Minecraft.getInstance();
        if (!HaloModConfigStore.get().isPlayerPreviewHaloEnabled()) { VIEWS.close(); return; }
        VIEWS.beginFrame(client.screen, client.level, motionOptions(), System.nanoTime());
    }
    public static void endFrame() { VIEWS.endFrame(); }
    public static void resetAutomaticMotion() { VIEWS.resetMotion(); }
    public static void clearAutomaticViews() { VIEWS.close(); PlayerPreviewCapture.clearAnchorScopes(); }
    private static PreviewOptions motionOptions() {
        return HaloModConfigStore.get().isPlayerPreviewHaloPhysicsEnabled() ? PreviewOptions.PHYSICS : PreviewOptions.RIGID;
    }

    /** Submit after the host's model buffers are flushed, while its projection and GUI lighting are active. */
    private static void draw(PreviewSession session, PreviewFrame frame) {
        RenderSystem.assertOnRenderThread();
        if (!HaloModConfigStore.get().isPlayerPreviewHaloEnabled()) return;
        // 1.21's inventory clips the player to a small rectangle. A halo can extend
        // past that rectangle; preserve clipping for the player and subsequent UI,
        // but let the halo use its full geometry while retaining model depth tests.
        boolean clipped = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        int[] clip = clipped ? new int[4] : null;
        if (clipped) {
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, clip);
            RenderSystem.disableScissor();
        }
        try {
            HaloRenderer.getInstance().submitPreview(session.render(frame), frame.visuals());
        } finally {
            if (clipped) RenderSystem.enableScissor(clip[0], clip[1], clip[2], clip[3]);
        }
    }

    /**
     * Wrap a vanilla player renderer at its final preview root. Flushes model buffers before drawing
     * the halo. Invoked by the InventoryScreen.drawEntity adapter hook.
     */
    public static void renderPlayer(GuiGraphics context, LivingEntity entity, Object viewIdentity, Runnable renderEntity) {
        if (!(entity instanceof Player)) { renderEntity.run(); return; }
        try (var capture = PlayerPreviewCapture.open(entity, context.pose().last().pose())) {
            renderEntity.run();
            context.flush();
            var head = capture.head();
            if (head == null || !entity.isAlive() || !HaloModConfigStore.get().isPlayerPreviewHaloEnabled()) return;
            var assets = HaloMeshResources.snapshot();
            try (var lease = VIEWS.acquire(viewIdentity, entity.getUUID(), entity.getId(), motionOptions())) {
                draw(lease.session(), new PreviewFrame(entity.getUUID(), entity.getId(), head,
                    new FrameScene.CameraSample(new Vec3d(0, 0, 0), new Vec3d(0, -1, 0), new Vec3d(1, 0, 0)),
                    capture.root().get(new float[16]), System.currentTimeMillis(), VIEWS.frameNanos(),
                    LightSample.FULL_BRIGHT, id -> true, assets.visuals(), PreviewFrame.Projection.ORTHOGRAPHIC,
                    HaloRenderer.getInstance().primitiveMode()));
            }
        }
    }
}
