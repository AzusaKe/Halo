package network.azusake.halo.physics;

import network.azusake.halo.HaloMod;
import network.azusake.halo.manager.HaloManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Per-tick server handler for halo lifecycle maintenance.
 *
 * <p>Registered as a {@link ServerTickEvents.EndTick} listener.  Delegates
 * to {@link HaloManager#tickAll} which performs periodic entity cleanup
 * (removing halos whose attached entity has died or despawned).</p>
 *
 * <p>All pose computation and damping physics have moved to
 * {@link HaloPoseCalculator} on the render thread — this handler
 * no longer performs any physics work.</p>
 */
public class HaloTickHandler implements ServerTickEvents.EndTick {

    private static final HaloTickHandler INSTANCE = new HaloTickHandler();

    private HaloTickHandler() {
        // singleton — use register()
    }

    /**
     * Register this handler on the Fabric tick event bus.
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(INSTANCE);
        HaloMod.LOGGER.debug("HaloTickHandler: registered on END_SERVER_TICK");
    }

    @Override
    public void onEndTick(MinecraftServer server) {
        // Refresh the server reference so debug chat messages work
        network.azusake.halo.lifecycle.EntityHaloTracker.setCurrentServer(server);
        HaloManager.getInstance().tickAll(server);
    }
}
