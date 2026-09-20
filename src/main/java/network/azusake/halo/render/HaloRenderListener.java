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
    private static final WorldFrameGate FRAME = new WorldFrameGate();

    static void beginFrame() { FRAME.beginFrame(); HaloRenderDiagnostics.beginFrame(); }

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
        if (!network.azusake.halo.physics.OptionalIrisPassDetector.isMainPass()) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            if (!FRAME.capture(true)) return;
            Matrix4f viewMatrix = new Matrix4f();
            float tickDelta = event.getPartialTick().getGameTimeDeltaPartialTick(true);
            RenderHeadCapture.beginFrame(viewMatrix, event.getCamera().getPosition(),
                event.getFrustum(), tickDelta, Minecraft.getInstance().level);
            EmfHeadCapture.beginFrame(viewMatrix, event.getCamera().getPosition());
            YsmHeadCapture.beginFrame(viewMatrix, event.getCamera().getPosition(), event.getFrustum());
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            if (!FRAME.render(true)) return;
            HaloRenderer.getInstance().renderHalos(event.getPoseStack(), event.getCamera(),
                event.getPartialTick().getGameTimeDeltaPartialTick(true));
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES
            && network.azusake.halo.physics.OptionalIrisPassDetector.hasShaderPack()) {
            // AFTER_TRANSLUCENT_BLOCKS still precedes the entity buffer flush.
            // Iris needs these draws inside its active world pipeline.
            HaloRenderer.getInstance().submitDeferredMeshes();
        }
    }
}
