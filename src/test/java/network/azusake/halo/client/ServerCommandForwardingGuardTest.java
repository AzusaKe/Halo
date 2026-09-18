package network.azusake.halo.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerCommandForwardingGuardTest {
    @Test
    void bypassesOnlyInsideForwardingScope() {
        assertFalse(ServerCommandForwardingGuard.shouldBypassClientDispatcher());

        ServerCommandForwardingGuard.forward(() ->
            assertTrue(ServerCommandForwardingGuard.shouldBypassClientDispatcher()));

        assertFalse(ServerCommandForwardingGuard.shouldBypassClientDispatcher());
    }

    @Test
    void clearsScopeWhenSendFails() {
        assertThrows(IllegalStateException.class, () ->
            ServerCommandForwardingGuard.forward(() -> {
                throw new IllegalStateException("send failed");
            }));

        assertFalse(ServerCommandForwardingGuard.shouldBypassClientDispatcher());
    }

    @Test
    void rejectsUnexpectedNestedForwarding() {
        assertThrows(IllegalStateException.class, () ->
            ServerCommandForwardingGuard.forward(() ->
                ServerCommandForwardingGuard.forward(() -> {
                })));

        assertFalse(ServerCommandForwardingGuard.shouldBypassClientDispatcher());
    }
}
