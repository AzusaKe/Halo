package com.example.halo.physics;

import com.example.halo.HaloMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Per-tick physics driver for all active halo instances.
 *
 * <p>Registered as a {@link ServerTickEvents.EndTick} listener so damping
 * calculations run once per server tick <em>after</em> entity movement has
 * been processed.  Each loaded halo instance has its
 * {@link com.example.halo.data.HaloInstance#tickDamping} method called.</p>
 *
 * <p>Registration is a one-liner:</p>
 * <pre>{@code
 *   HaloTickHandler.register();
 * }</pre>
 */
public class HaloTickHandler implements ServerTickEvents.EndTick {

    private static final HaloTickHandler INSTANCE = new HaloTickHandler();

    private HaloTickHandler() {
        // singleton — use register()
    }

    /**
     * Register this handler on the Fabric tick event bus.
     * Safe to call multiple times (Fabric events support multiple
     * registrations, though the caller should only invoke this once).
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(INSTANCE);
        HaloMod.LOGGER.debug("HaloTickHandler: registered on END_SERVER_TICK");
    }

    @Override
    public void onEndTick(MinecraftServer server) {
        // TODO: Iterate all active HaloInstances and call tickDamping().
        //       Requires HaloManager (future task).
        //
        //   HaloManager.getInstance().forEachActive((instance, entity) -> {
        //       Vec3d anchorPos = entity.getEyePos();
        //       Vec3d haloCenter = instance.getRelativePosition().add(anchorPos);
        //       HaloDampingConfig damping = getDefinition(instance).damping();
        //       instance.tickDamping(anchorPos, haloCenter, damping);
        //   });
    }
}
