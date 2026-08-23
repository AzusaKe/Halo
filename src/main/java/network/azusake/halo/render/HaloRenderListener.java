package network.azusake.halo.render;

import network.azusake.halo.HaloMod;
import network.azusake.halo.physics.RenderHeadCapture;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the halo renderer with NeoForge's world-render pipeline.
 *
 * <p>Halos are submitted through NeoForge's native custom-geometry collector,
 * which places them in the translucent feature phase and lets the selected
 * vanilla entity RenderType control blending and shader integration.</p>
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
     * Register the halo renderer with the NeoForge render event bus.
     * Idempotent — subsequent calls are no-ops.
     */
    public static void register() {
        if (registered) {
            LOG.warn("[HaloRenderListener] already registered — skipping");
            return;
        }
        registered = true;

        // Extraction phase (thread-safe, no GL): drop raw capture diagnostics
        // and record the tick delta for deferred drawing. RenderHeadCapture
        // retains only a trustworthy current/preceding main-world anchor for
        // the next custom-geometry lookup.
        NeoForge.EVENT_BUS.addListener(ExtractLevelRenderStateEvent.class, event -> {
            RenderHeadCapture.clearFrame();
            // On modern render-state versions the world-render matrix stack has an identity
            // root (the camera view rotation is applied by the GPU at draw
            // time), so the head matrices captured during entity rendering are
            // already camera-relative world space.  The anchor pipeline
            // therefore must NOT un-rotate them — an identity "view matrix"
            // leaves the captures unchanged.
            RenderHeadCapture.setViewMatrix(new Matrix4f());
            lastTickDelta = event.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            RenderHeadCapture.setFrameTickDelta(lastTickDelta);
        });

        // Keep NeoForge's native collection point. Each primitive is submitted
        // as ordered custom entity geometry, so Minecraft and Iris own upload,
        // translucent ordering, reverse-Z depth, shader and framebuffer state.
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, event -> {
            Vec3 camPos = event.getLevelRenderState().cameraRenderState.pos;
            HaloRenderer.getInstance().renderHalos(
                event.getPoseStack(), event.getSubmitNodeCollector(),
                camPos, lastTickDelta);
        });

        LOG.info("[HaloRenderListener] registered on ExtractLevelRenderStateEvent / SubmitCustomGeometryEvent");
        LOG.debug("[HaloRenderListener] backend=vanilla_entity_submit_nodes (Iris-compatible)");
    }

    /** Frame tick delta captured during extraction, consumed by the draw pass. */
    private static volatile float lastTickDelta;
}
