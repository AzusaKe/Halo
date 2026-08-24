package network.azusake.halo.server;

import network.azusake.halo.HaloMod;
import net.minecraft.server.MinecraftServer;

/**
 * Per-tick server handler invoked at the <em>end</em> of every server tick.
 *
 * <p>Registered via {@link net.neoforged.neoforge.event.tick.ServerTickEvent.Post}
 * by {@link HaloServerEvents#registerTickHandler()}.  This is the hook where
 * halo physics, animation evaluation, and per-entity updates are driven.  For
 * now it only emits a trace-level log line so we can confirm the tick loop is
 * wired correctly.</p>
 */
public class ServerTickHandler {

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
     * Called by the NeoForge event bus at the end of every server tick.
     *
     * @param server the current Minecraft server instance
     */
    public void onEndTick(MinecraftServer server) {
        tickCounter++;

        if (tickCounter % LOG_INTERVAL_TICKS == 0) {
            HaloMod.LOGGER.trace(
                "ServerTickHandler: tick {} – playerCount={}, ticksRunning={}",
                tickCounter,
                server.getPlayerCount(),
                server.getTickCount()
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
