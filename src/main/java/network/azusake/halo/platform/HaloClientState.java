package network.azusake.halo.platform;

import network.azusake.halo.core.runtime.ClientRuntime;

/** Fabric's logical-client composition root; never used by server handlers. */
public final class HaloClientState {
    private static final ClientRuntime CLIENT=new ClientRuntime();
    private HaloClientState() {}
    public static ClientRuntime get() { return CLIENT; }
}
