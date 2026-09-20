package network.azusake.halo.physics;

import network.azusake.halo.HaloMod;
import network.azusake.halo.manager.HaloManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Per-tick server handler for halo lifecycle maintenance.
 *
 * <p>Registered as a Forge end-of-server-tick listener. Delegates
 * to {@link HaloManager#tickAll} which performs periodic entity cleanup
 * (removing halos whose attached entity has died or despawned).</p>
 *
 * <p>All pose computation and damping physics have moved to
 * {@link AnchorFrameCalculator} on the render thread — this handler
 * no longer performs any physics work.</p>
 */
public final class HaloTickHandler {

    private static boolean registered;
    private static final HaloTickHandler INSTANCE = new HaloTickHandler();

    private HaloTickHandler() {
        // singleton — use register()
    }

    /**
     * Register this handler on the Forge event bus.
     */
    public static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(INSTANCE::onServerTick);
        HaloMod.LOGGER.debug("HaloTickHandler: registered on END_SERVER_TICK");
    }

    public void onEndTick(MinecraftServer server) {
        // Refresh the server reference so debug chat messages work
        network.azusake.halo.lifecycle.EntityHaloTracker.setCurrentServer(server);
        HaloManager.getInstance().tickAll(server);
        network.azusake.halo.item.HaloScepterService.tick(server);
    }

    private void onServerTick(ServerTickEvent.Post event) {
        onEndTick(event.getServer());
    }
}
