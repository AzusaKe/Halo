package network.azusake.halo.compat.ysm;

import net.minecraft.world.entity.LivingEntity;
import network.azusake.halo.api.EntityAnchorProvider;
import network.azusake.halo.api.HeadAnchor;
import network.azusake.halo.config.HaloModConfigStore;

/** YSM-aware wrapper used for non-player living entities. */
public final class YsmEntityAnchorProvider implements EntityAnchorProvider {
    private final EntityAnchorProvider fallback;

    public YsmEntityAnchorProvider(EntityAnchorProvider fallback) {
        this.fallback = fallback;
    }

    @Override
    public HeadAnchor resolve(LivingEntity entity, float tickDelta) {
        if (!HaloModConfigStore.get().isExperimentalYsmAnchorEnabled()) {
            return fallback.resolve(entity, tickDelta);
        }
        if (!YsmHeadCapture.isVisibleToMainCamera(entity)) {
            YsmHeadCapture.discard(entity.getUUID());
            return fallback.resolve(entity, tickDelta);
        }
        HeadAnchor current = YsmHeadCapture.resolveCurrent(entity, tickDelta);
        if (current != null) return current;
        HeadAnchor previous = YsmHeadCapture.resolvePrevious(entity, tickDelta);
        return previous != null ? previous : fallback.resolve(entity, tickDelta);
    }
}
