package network.azusake.halo.physics;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Fallback {@link EntityAnchorProvider} for non-player entities.
 *
 * <p>Uses the original {@code height * 0.85} heuristic to estimate the
 * head centre.  This preserves the Phase 2 behaviour for all entities
 * that do not yet have a dedicated {@code entity_anchors/*.json} profile.</p>
 *
 * <p>Future phases will add JSON profiles for common entity types
 * (cow, pig, chicken, creeper, etc.), after which this fallback only
 * applies to unrecognized / modded entities.</p>
 */
public final class FallbackAnchorProvider implements EntityAnchorProvider {

    private static final FallbackAnchorProvider INSTANCE = new FallbackAnchorProvider();

    private FallbackAnchorProvider() { /* singleton */ }

    public static FallbackAnchorProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public HeadAnchor resolve(LivingEntity entity, float tickDelta) {
        // Interpolated foot position
        double x = entity.prevX + (entity.getX() - entity.prevX) * tickDelta;
        double y = entity.prevY + (entity.getY() - entity.prevY) * tickDelta;
        double z = entity.prevZ + (entity.getZ() - entity.prevZ) * tickDelta;

        // height * 0.85 approximation (original behaviour)
        Vec3d headCenter = new Vec3d(x, y + entity.getHeight() * 0.85, z);

        // Head yaw/pitch: same interpolation as original getInterpolatedHeadYaw
        float prevYaw = entity.prevHeadYaw;
        float currYaw = entity.headYaw;
        float diffYaw = currYaw - prevYaw;
        if (diffYaw > 180f) diffYaw -= 360f;
        if (diffYaw < -180f) diffYaw += 360f;
        float yaw = prevYaw + diffYaw * tickDelta;

        float pitch = entity.prevPitch + (entity.getPitch() - entity.prevPitch) * tickDelta;

        return new HeadAnchor(headCenter, yaw, pitch);
    }
}
