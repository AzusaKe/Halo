package network.azusake.halo.render;

import network.azusake.halo.HaloMod;
import network.azusake.halo.compat.ysm.YsmHeadCapture;
import network.azusake.halo.physics.RenderHeadCapture;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

/** Connects frame capture and halo drawing to NeoForge's level render stages. */
public final class HaloRenderListener {
    private static boolean registered;
    private HaloRenderListener() {}

    public static void register() {
        if (registered) {
            HaloMod.LOGGER.warn("[HaloRenderListener] already registered - skipping");
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(HaloRenderListener::onRenderLevelStage);
        HaloMod.LOGGER.info("[HaloRenderListener] registered on RenderLevelStageEvent");
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            RenderHeadCapture.clearFrame();
            Matrix4f viewMatrix = new Matrix4f();
            RenderHeadCapture.setViewMatrix(viewMatrix);
            YsmHeadCapture.beginFrame(viewMatrix, event.getCamera().getPosition(), event.getFrustum());
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            HaloRenderer.getInstance().renderHalos(event.getPoseStack(), event.getCamera(),
                event.getPartialTick().getGameTimeDeltaPartialTick(true));
        }
    }
}
