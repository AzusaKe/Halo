package network.azusake.halo.physics;

import java.util.UUID;
import network.azusake.halo.api.v2.AnchorVec3;

/** Facts copied during extraction; delayed model draws never read a moving entity. */
public record EntityRenderSnapshot(UUID uuid, int runtimeId, Object world, AnchorVec3 position,
                                   boolean living, boolean player) {
    public interface Holder {
        EntityRenderSnapshot halo$snapshot();
        void halo$snapshot(EntityRenderSnapshot value);
    }
}
