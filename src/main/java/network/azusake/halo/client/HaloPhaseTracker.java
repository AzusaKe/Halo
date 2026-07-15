package network.azusake.halo.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

/**
 * Tracks whether the client is in {@link Phase#LOCAL} or {@link Phase#MULTIPLAYER} phase.
 *
 * <p>The client starts in LOCAL phase when connecting to a dedicated server.
 * If a {@code halo:hello} handshake packet arrives from the server, the phase
 * transitions to MULTIPLAYER — all subsequent {@code /halo} commands are sent
 * to the server-side Brigadier dispatcher instead of being intercepted locally.
 *
 * <p><b>Singleplayer safety net:</b> {@link #shouldIntercept()} always returns
 * {@code false} when an integrated server is running, so existing singleplayer
 * behaviour is never affected.
 *
 * <p><b>Thread safety:</b> the {@code phase} field is {@code volatile} so a
 * transition signalled from a netty I/O thread is immediately visible to the
 * main client thread that runs the Mixin injector.
 */
@Environment(EnvType.CLIENT)
public final class HaloPhaseTracker {

    public enum Phase {
        /** Client is connected to a server without the mod, or has not yet received the handshake. */
        LOCAL,
        /** Client has received {@code halo:hello} — server has the mod installed. */
        MULTIPLAYER
    }

    private static final HaloPhaseTracker INSTANCE = new HaloPhaseTracker();

    private volatile Phase phase = Phase.LOCAL;

    private HaloPhaseTracker() {
        // singleton
    }

    public static HaloPhaseTracker getInstance() {
        return INSTANCE;
    }

    public Phase getPhase() {
        return phase;
    }

    /**
     * Transition from LOCAL to MULTIPLAYER.  Called when {@code halo:hello} is received.
     * Idempotent — subsequent calls after the first are no-ops.
     */
    public void transitionToMultiplayer() {
        if (phase == Phase.LOCAL) {
            phase = Phase.MULTIPLAYER;
        }
    }

    /**
     * Reset to LOCAL phase.  Called on disconnect so the next server connection
     * starts fresh.
     */
    public void resetToLocal() {
        phase = Phase.LOCAL;
    }

    /**
     * Whether {@code /halo} commands should be intercepted and handled locally
     * instead of being sent to the server.
     *
     * <p>Returns {@code false} when running an integrated server (singleplayer /
     * LAN host) — commands flow to the integrated Brigadier dispatcher as normal.
     */
    public boolean shouldIntercept() {
        if (MinecraftClient.getInstance().isIntegratedServerRunning()) {
            return false;
        }
        return phase == Phase.LOCAL;
    }
}
