package network.azusake.halo.render;

import network.azusake.halo.HaloMod;
import network.azusake.halo.physics.RenderHeadCapture;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers capture and halo submission in the 26.x extraction pipeline. */
public final class HaloRenderListener {
    private static final Logger LOG = LoggerFactory.getLogger(HaloMod.MOD_ID);
    private static boolean registered;
    private static volatile float lastTickDelta;

    private HaloRenderListener() {}

    public static void register() {
        if (registered) return;
        registered = true;
        LevelRenderEvents.END_EXTRACTION.register(context -> {
            RenderHeadCapture.clearFrame();
            RenderHeadCapture.setViewMatrix(new Matrix4f());
            lastTickDelta = context.deltaTracker().getGameTimeDeltaPartialTick(true);
            RenderHeadCapture.setFrameTickDelta(lastTickDelta);
        });
        LevelRenderEvents.AFTER_SOLID_FEATURES.register(context -> {
            Vec3 camera = context.levelState().cameraRenderState.pos;
            HaloRenderer.getInstance().renderHalos(
                context.poseStack(), context.submitNodeCollector(), camera, lastTickDelta);
        });
        LOG.info("[HaloRenderListener] registered on extraction / solid feature events");
    }
}
