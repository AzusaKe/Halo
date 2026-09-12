package network.azusake.halo.platform;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.runtime.ClientStatus;
import network.azusake.halo.core.runtime.RuntimeConfigSnapshot;

/** Explicit, client-installed bridge; server-only startup never resolves client classes. */
public final class IntegratedBridge {
    private static volatile Map<UUID, ClientStatus> diagnostics = Map.of();
    public static Consumer<RuntimeConfigSnapshot> config = ignored -> {};
    public static Consumer<UUID> teleport = ignored -> {};

    private IntegratedBridge() {}

    public static void publishDiagnostics(Map<UUID, ClientStatus> snapshot) {
        diagnostics = Map.copyOf(snapshot);
    }

    public static void clearDiagnostics() {
        diagnostics = Map.of();
    }

    public static ClientStatus status(UUID uuid, Identifier id) {
        var value = diagnostics.get(uuid);
        return value != null && value.definition().equals(id) ? value : null;
    }
}
