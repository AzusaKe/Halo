package network.azusake.halo.render;

import network.azusake.halo.HaloMod;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
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
