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

        // Extraction phase (thread-safe, no GL): drop last frame's head
        // captures and record the frame tick delta so the drawing phase can
        // interpolate halo animation.  Entities that did not render this frame
        // fall back to their previous anchor provider.
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
        });

        // Submit into the native feature collector. Vanilla entity RenderTypes
        // let Iris choose the correct shader program and scaled framebuffer.
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
