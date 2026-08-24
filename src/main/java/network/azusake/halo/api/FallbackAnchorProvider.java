package network.azusake.halo.api;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

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
        double x = entity.xo + (entity.getX() - entity.xo) * tickDelta;
        double y = entity.yo + (entity.getY() - entity.yo) * tickDelta;
        double z = entity.zo + (entity.getZ() - entity.zo) * tickDelta;

        // height * 0.85 approximation (original behaviour)
        Vec3 headCenter = new Vec3(x, y + entity.getBbHeight() * 0.85, z);

        // Head yaw/pitch: same interpolation as original getInterpolatedHeadYaw
        float prevYaw = entity.yHeadRotO;
        float currYaw = entity.yHeadRot;
        float diffYaw = currYaw - prevYaw;
        if (diffYaw > 180f) diffYaw -= 360f;
        if (diffYaw < -180f) diffYaw += 360f;
        float yaw = prevYaw + diffYaw * tickDelta;

        float pitch = entity.xRotO + (entity.getXRot() - entity.xRotO) * tickDelta;

        return new HeadAnchor(headCenter, yaw, pitch, 0f);
    }
}
