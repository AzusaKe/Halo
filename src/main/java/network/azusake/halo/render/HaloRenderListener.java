package network.azusake.halo.render;

import network.azusake.halo.HaloMod;
import network.azusake.halo.physics.RenderHeadCapture;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the halo renderer with Fabric's world-render pipeline.
 *
 * <p>Halos are submitted through Fabric's feature collector with vanilla
 * entity render types, so shader replacements keep their expected matrices
 * and targets.</p>
 *
 * <p>Usage: call {@link #register()} once during client initialisation.</p>
 */
public final class HaloRenderListener {

    private static final Logger LOG = LoggerFactory.getLogger(HaloMod.MOD_ID);

    private static boolean registered;

    private HaloRenderListener() {
        // utility class
    }

    /**
     * Register the halo renderer with the Fabric render event bus.
     * Idempotent — subsequent calls are no-ops.
     */
    public static void register() {
        if (registered) {
            LOG.warn("[HaloRenderListener] already registered — skipping");
            return;
        }
        registered = true;

        // End extraction before deferred draw: drop this extraction's raw
        // capture diagnostics and record the tick delta for the upcoming draw.
        // RenderHeadCapture retains only a trustworthy main-world anchor from
        // the current/preceding draw frame for the next COLLECT_SUBMITS lookup.
        LevelRenderEvents.END_EXTRACTION.register(context -> {
            RenderHeadCapture.clearFrame();
            // On modern render-state versions the world-render matrix stack has an identity
            // root (the camera view rotation is applied by the GPU at draw
            // time), so the head matrices captured during entity rendering are
            // already camera-relative world space.  The anchor pipeline
            // therefore must NOT un-rotate them — an identity "view matrix"
            // leaves the captures unchanged.
            RenderHeadCapture.setViewMatrix(new Matrix4f());
            lastTickDelta = context.deltaTracker().getGameTimeDeltaPartialTick(true);
            RenderHeadCapture.setFrameTickDelta(lastTickDelta);
        });

        // Keep 26.2's native collection point. Each primitive is submitted as
        // ordered custom entity geometry, so Minecraft and Iris own upload,
        // translucent ordering, reverse-Z depth, shader and framebuffer state.
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            Vec3 camPos = context.levelState().cameraRenderState.pos;
            HaloRenderer.getInstance().renderHalos(
                context.poseStack(), context.submitNodeCollector(), camPos, lastTickDelta);
        });

        LOG.info("[HaloRenderListener] registered on LevelRenderEvents.END_EXTRACTION / COLLECT_SUBMITS");
        LOG.debug("[HaloRenderListener] backend=vanilla_entity_submit_nodes (Iris-compatible)");
    }

    /** Frame tick delta captured during extraction, consumed by the draw pass. */
    private static volatile float lastTickDelta;
}
