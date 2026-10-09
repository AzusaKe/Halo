package network.azusake.halo.platform;

import java.util.UUID;
import network.azusake.halo.core.runtime.ClientRuntime;

/** Client untracking is distinct from a confirmed death on the owning client thread. */
public final class ClientDepartures {
    private ClientDepartures() {}

    public static void depart(ClientRuntime runtime, UUID uuid, boolean player, boolean confirmedDeath) {
        if (confirmedDeath) runtime.died(uuid, player);
        else runtime.unload(uuid);
    }
}
