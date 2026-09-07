package network.azusake.halo.render;

import network.azusake.halo.HaloMod;
import network.azusake.halo.compat.emf.EmfHeadCapture;
import network.azusake.halo.compat.ysm.YsmHeadCapture;
import network.azusake.halo.physics.RenderHeadCapture;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the halo renderer with Fabric's world-render pipeline.
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
     * Register the halo renderer with the Fabric render event bus.
     * Idempotent — subsequent calls are no-ops.
     */
    public static void register() {
        if (registered) {
            LOG.warn("[HaloRenderListener] already registered — skipping");
            return;
        }
        registered = true;

        // Drop last frame's head captures right before entities render so the
        // halo pass (AFTER_ENTITIES) only sees this frame's captures; entities
        // that did not render this frame fall back to their previous provider.
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            RenderHeadCapture.clearFrame();
            // The world-render stack is at its root (the camera view
            // matrix) right before entities render.  Captured head
            // matrices are camera-relative, so the halo pipeline needs
            // this view matrix to recover world-space anchors.
            Matrix4f viewMatrix = new Matrix4f(context.matrixStack().peek().getPositionMatrix());
            RenderHeadCapture.setViewMatrix(viewMatrix);
            EmfHeadCapture.beginFrame(viewMatrix, context.camera().getPos());
            YsmHeadCapture.beginFrame(
                viewMatrix,
                context.camera().getPos(),
                context.frustum()
            );
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            HaloRenderer.getInstance().renderHalos(
                context.matrixStack(),
                context.camera(),
                context.tickDelta()
            );
        });

        LOG.info("[HaloRenderListener] registered on WorldRenderEvents.AFTER_ENTITIES");
    }
}
