package network.azusake.halo.compat.ysm;

import network.azusake.halo.api.EntityAnchorProvider;
import network.azusake.halo.api.HeadAnchor;
import network.azusake.halo.config.HaloModConfigStore;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Optional YSM render-anchor wrapper for every living entity class. */
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
        YsmHeadCapture.markProviderInvoked(false);
        if (!YsmHeadCapture.isVisibleToMainCamera(entity)) {
            YsmHeadCapture.markProviderOutsideFrustum(false);
            YsmHeadCapture.discard(entity.getUUID());
            return fallback.resolve(entity, tickDelta);
        }

        HeadAnchor current = resolveCapture(YsmHeadCapture.getCurrent(entity.getUUID()));
        if (isFinite(current)) {
            YsmHeadCapture.markAnchorConsumed(false);
            return current;
        }

        HeadAnchor previous = resolveCapture(YsmHeadCapture.getPrevious(entity.getUUID()));
        if (isFinite(previous)) {
            YsmHeadCapture.markAnchorConsumed(true);
            return previous;
        }
        YsmHeadCapture.markProviderMiss(false);
        return fallback.resolve(entity, tickDelta);
    }

    private static HeadAnchor resolveCapture(YsmHeadCapture.CapturedHead captured) {
        if (captured == null) {
            return null;
        }
        double[] rawOffset = HaloModConfigStore.get().getExperimentalYsmHeadLocalOffset();
        HeadAnchor anchor = YsmHeadMath.toHeadAnchor(
            captured, new Vec3(rawOffset[0], rawOffset[1], rawOffset[2]));
        if (!isFinite(anchor)) {
            YsmHeadCapture.markAnchorConversionFailed();
            return null;
        }
        return anchor;
    }

    private static boolean isFinite(HeadAnchor anchor) {
        if (anchor == null) {
            return false;
        }
        Vec3 center = anchor.headCenter();
        return Double.isFinite(center.x) && Double.isFinite(center.y) && Double.isFinite(center.z)
            && Float.isFinite(anchor.yaw()) && Float.isFinite(anchor.pitch()) && Float.isFinite(anchor.roll());
    }
}
