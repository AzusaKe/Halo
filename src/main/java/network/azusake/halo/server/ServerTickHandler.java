package network.azusake.halo.server;

import network.azusake.halo.HaloMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Per-tick server handler invoked at the <em>end</em> of every server tick.
 *
 * <p>Registered via {@link ServerTickEvents#END_SERVER_TICK}.  This is the
 * hook where halo physics, animation evaluation, and per-entity updates
 * will be driven in later tasks.  For now it only emits a trace-level log
 * line so we can confirm the tick loop is wired correctly.</p>
 */
public class ServerTickHandler implements ServerTickEvents.EndTick {

    /**
     * Number of ticks that elapse between trace-log emissions.
     * Set to 20 so we log roughly once per second at 20 TPS.
     */
    private static final int LOG_INTERVAL_TICKS = 20;

    private int tickCounter;

    public ServerTickHandler() {
        this.tickCounter = 0;
    }

    /**
     * Called by Fabric API at the end of every server tick.
     *
     * @param server the current Minecraft server instance
     */
    @Override
    public void onEndTick(MinecraftServer server) {
        tickCounter++;

        if (tickCounter % LOG_INTERVAL_TICKS == 0) {
            HaloMod.LOGGER.trace(
                "ServerTickHandler: tick {} – playerCount={}, ticksRunning={}",
                tickCounter,
                server.getCurrentPlayerCount(),
                server.getTicks()
            );
        }

        // Halo physics is driven by HaloTickHandler (calls HaloManager.tickAll).
        // This handler remains as a lightweight heartbeat / tracer.
    }

    /**
     * The number of ticks this handler has processed since creation.
     *
     * @return current tick count (package-private for testing)
     */
    int getTickCounter() {
        return tickCounter;
    }
}
