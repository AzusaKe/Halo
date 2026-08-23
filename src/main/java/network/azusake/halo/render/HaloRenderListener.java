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
 * <p>Halos are drawn <em>after</em> entities and use vanilla entity render
 * types so shader replacements keep their expected matrices and targets.</p>
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

        // Extraction phase (thread-safe, no GL): drop last frame's raw capture
        // diagnostics and record the frame tick delta. RenderHeadCapture keeps
        // only the immediately preceding valid main-pass head anchor because
        // 26.1 draws translucent player models after Halo's submission point.
        LevelRenderEvents.END_EXTRACTION.register(context -> {
            RenderHeadCapture.clearFrame();
            // On 1.21.1+ / 26.1 the world-render matrix stack has an identity
            // root (the camera view rotation is applied by the GPU at draw
            // time), so the head matrices captured during entity rendering are
            // already camera-relative world space.  The anchor pipeline
            // therefore must NOT un-rotate them — an identity "view matrix"
            // leaves the captures unchanged.
            RenderHeadCapture.setViewMatrix(new Matrix4f());
            lastTickDelta = context.deltaTracker().getGameTimeDeltaPartialTick(true);
            RenderHeadCapture.setFrameTickDelta(lastTickDelta);
        });

        // Submit after solid entity features and before translucent features.
        // The translucent base player body has not drawn yet, so third-person
        // Halo placement consumes the immediately preceding main-pass capture.
        // Adding Halo as custom entity geometry here lets Minecraft and Iris
        // draw it inside the normal translucent phase instead of flushing a
        // BufferSource with the wrong shader matrix/target context.
        LevelRenderEvents.AFTER_SOLID_FEATURES.register(context -> {
            Vec3 camPos = context.levelState().cameraRenderState.pos;
            HaloRenderer.getInstance().renderHalos(
                context.poseStack(), context.submitNodeCollector(), camPos, lastTickDelta);
        });

        LOG.info("[HaloRenderListener] registered on LevelRenderEvents.END_EXTRACTION / AFTER_SOLID_FEATURES");
        LOG.debug("[HaloRenderListener] backend=vanilla_entity_submit_nodes (Iris-compatible)");
    }

    /** Frame tick delta captured during extraction, consumed by the draw pass. */
    private static volatile float lastTickDelta;
}
