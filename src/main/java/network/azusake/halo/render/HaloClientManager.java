package network.azusake.halo.render;

import net.minecraft.client.MinecraftClient;
import network.azusake.halo.client.HaloLocalManager;
import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.platform.HaloClientState;

/** Connection adapter for persistent local ownership. Visibility and lifecycle run in core. */
public final class HaloClientManager {
    private static final HaloClientManager INSTANCE = new HaloClientManager();
    private HaloClientManager() {}
    public static HaloClientManager getInstance() { return INSTANCE; }

    public void restoreLocalOwnership() {
        if (!HaloPhaseTracker.getInstance().shouldIntercept()) return;
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.getNetworkHandler() == null) return;
        String key = HaloLocalManager.serverKeyFromAddress(client.getNetworkHandler().getConnection().getAddress());
        if (key != null) HaloLocalManager.getInstance().restoreInto(key, HaloClientState.get());
    }
}
