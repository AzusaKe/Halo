package network.azusake.halo.api.v2;

import network.azusake.halo.anchor.AnchorCaptureCoordinator;

/** Cross-loader entry point for Halo's render-scoped anchor API. */
public final class HaloAnchorApi {

    private HaloAnchorApi() {
    }

    public static AnchorSource register(String sourceId) {
        return AnchorCaptureCoordinator.register(sourceId);
    }
}
