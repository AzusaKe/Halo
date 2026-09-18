package network.azusake.halo.client;

import java.util.Objects;

/**
 * Marks the one re-entry into {@code ClientPacketListener.sendCommand} that is
 * used to forward a Forge client command to the server.
 *
 * <p>Forge normally executes its client command dispatcher at the start of
 * {@code sendCommand}. Halo's client command tree deliberately shares the
 * {@code /halo} root with the server tree, so forwarding an already-executed
 * client node must bypass that dispatcher exactly once. The rest of the
 * vanilla method still runs and produces the normal command signatures and
 * last-seen-message update.</p>
 */
public final class ServerCommandForwardingGuard {
    private static final ThreadLocal<Boolean> FORWARDING =
        ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ServerCommandForwardingGuard() {
    }

    /** Returns whether Forge's client dispatcher should be skipped for this call. */
    public static boolean shouldBypassClientDispatcher() {
        return FORWARDING.get();
    }

    /** Runs one normal connection send while marking its client-dispatch pass for bypass. */
    public static void forward(Runnable send) {
        Objects.requireNonNull(send, "send");
        if (FORWARDING.get()) {
            throw new IllegalStateException("Nested Halo server-command forwarding");
        }

        FORWARDING.set(Boolean.TRUE);
        try {
            send.run();
        } finally {
            FORWARDING.remove();
        }
    }
}
