package network.azusake.halo.physics;

import network.azusake.halo.HaloMod;
import network.azusake.halo.manager.HaloManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Per-tick server handler for halo lifecycle maintenance.
 *
 * <p>Registered for Forge's {@link TickEvent.ServerTickEvent} END phase. Delegates
 * to {@link HaloManager#tickAll} which performs periodic entity cleanup
 * (removing halos whose attached entity has died or despawned).</p>
 *
 * <p>All pose computation and damping physics have moved to
 * {@link AnchorFrameCalculator} on the render thread — this handler
 * no longer performs any physics work.</p>
 */
public class HaloTickHandler {

    private static final HaloTickHandler INSTANCE = new HaloTickHandler();

    private HaloTickHandler() {
        // singleton — use register()
    }

    /**
     * Register this handler on the Forge event bus.
     */
    public static void register() {
        register(MinecraftForge.EVENT_BUS);
    }

    public static void register(IEventBus eventBus) {
        eventBus.addListener(
            EventPriority.NORMAL,
            false,
            TickEvent.ServerTickEvent.class,
            event -> {
                if (event.phase == TickEvent.Phase.END) INSTANCE.onEndTick(event.getServer());
            }
        );
        HaloMod.LOGGER.debug("HaloTickHandler: registered on END_SERVER_TICK");
    }

    public void onEndTick(MinecraftServer server) {
        // Refresh the server reference so debug chat messages work
        network.azusake.halo.lifecycle.EntityHaloTracker.setCurrentServer(server);
        HaloManager.getInstance().tickAll(server);
        network.azusake.halo.item.HaloScepterService.tick(server);
    }
}
