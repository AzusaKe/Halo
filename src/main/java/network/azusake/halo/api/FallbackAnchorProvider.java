package network.azusake.halo.api;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Fallback {@link EntityAnchorProvider} for entities without a dedicated
 * provider.
 *
 * <p>Uses the original {@code height * 0.85} heuristic to estimate the head
 * centre, preserving the pre-registry behaviour for all entities that have no
 * dedicated {@code entity_anchors/*.json} profile and no registered provider.
 * Head orientation is yaw/pitch from the entity and roll is always 0 (vanilla
 * entity heads never roll).</p>
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

        return new HeadAnchor(headCenter, yaw, pitch, 0f);
    }
}
