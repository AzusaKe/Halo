package network.azusake.halo.render;

import network.azusake.halo.HaloMod;
import network.azusake.halo.physics.RenderHeadCapture;
import network.azusake.halo.compat.ysm.YsmHeadCapture;
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
 * which places them in the entity feature pipeline and lets the selected
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

    /** Register the halo renderer with NeoForge's render event bus. */
    public static void register() {
        if (registered) {
            LOG.warn("[HaloRenderListener] already registered — skipping");
            return;
        }
        registered = true;

        // Extraction phase (thread-safe, no GL): drop last frame's raw capture
        // diagnostics and record the frame tick delta. RenderHeadCapture keeps
        // only the immediately preceding valid main-pass head anchor because
        // custom geometry is submitted before deferred player models draw.
        NeoForge.EVENT_BUS.addListener(ExtractLevelRenderStateEvent.class, event -> {
            RenderHeadCapture.clearFrame();
            // On 1.21.1+ / 26.1 the world-render matrix stack has an identity
            // root (the camera view rotation is applied by the GPU at draw
            // time), so the head matrices captured during entity rendering are
            // already camera-relative world space.  The anchor pipeline
            // therefore must NOT un-rotate them — an identity "view matrix"
            // leaves the captures unchanged.
            RenderHeadCapture.setViewMatrix(new Matrix4f());
            lastTickDelta = event.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            RenderHeadCapture.setFrameTickDelta(lastTickDelta);
            YsmHeadCapture.beginFrame(
                new Matrix4f(), event.getCamera().position(), event.getFrustum(), lastTickDelta);
        });

        // Submit through NeoForge's native custom-geometry collector. The
        // collector later executes the selected vanilla ENTITY RenderTypes,
        // allowing Iris to retain its normal shader and framebuffer routing.
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
