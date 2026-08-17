package network.azusake.halo.render;

import network.azusake.halo.HaloMod;
import network.azusake.halo.physics.RenderHeadCapture;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the halo renderer with NeoForge's world-render pipeline.
 *
 * <p>Halos are drawn <em>after</em> entities so they always appear on top of
 * the entity they are attached to.  The glow layer uses additive blending and
 * renders correctly against both opaque and translucent geometry.</p>
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

        // Extraction phase (thread-safe, no GL): drop last frame's head
        // captures and record the frame tick delta so the drawing phase can
        // interpolate halo animation.  Entities that did not render this frame
        // fall back to their previous anchor provider.
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
        });

        // Drawing phase: halos are drawn after terrain, entities and their
        // translucent submits so they always appear on top of the entity they
        // are attached to.  The glow layer uses additive blending and renders
        // correctly against both opaque and translucent geometry.
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentBlocks.class, event -> {
            Vec3 camPos = event.getLevelRenderState().cameraRenderState.pos;
            HaloRenderer.getInstance().renderHalos(event.getPoseStack(), camPos, lastTickDelta);
        });

        LOG.info("[HaloRenderListener] registered on ExtractLevelRenderStateEvent / RenderLevelStageEvent.AfterTranslucentBlocks");
    }

    /** Frame tick delta captured during extraction, consumed by the draw pass. */
    private static volatile float lastTickDelta;
}
