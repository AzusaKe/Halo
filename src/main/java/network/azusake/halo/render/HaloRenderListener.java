package network.azusake.halo.render;

import network.azusake.halo.HaloMod;
import network.azusake.halo.compat.emf.EmfHeadCapture;
import network.azusake.halo.compat.ysm.YsmHeadCapture;
import network.azusake.halo.physics.RenderHeadCapture;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

/** Connects frame capture and halo drawing to NeoForge's level render stages. */
public final class HaloRenderListener {
    private static boolean registered;

    private HaloRenderListener() {
    }

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
            Matrix4f viewMatrix = new Matrix4f();
            float tickDelta = event.getPartialTick().getGameTimeDeltaPartialTick(true);
            RenderHeadCapture.beginFrame(viewMatrix, event.getCamera().getPosition(),
                event.getFrustum(), tickDelta, Minecraft.getInstance().level);
            EmfHeadCapture.beginFrame(viewMatrix, event.getCamera().getPosition());
            YsmHeadCapture.beginFrame(viewMatrix, event.getCamera().getPosition(), event.getFrustum());
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            HaloRenderer.getInstance().renderHalos(event.getPoseStack(), event.getCamera(),
                event.getPartialTick().getGameTimeDeltaPartialTick(true));
        }
    }
}
