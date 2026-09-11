package network.azusake.halo.physics;

import network.azusake.halo.anchor.AnchorPoseMath;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorVec3;
import net.minecraft.world.entity.LivingEntity;

/** Internal Vanilla fallback for entities without a render-time capture. */
final class DefaultAnchorResolver {

    private DefaultAnchorResolver() {
    }

    static AnchorPose resolve(LivingEntity entity, float tickDelta) {
        double x = entity.xo + (entity.getX() - entity.xo) * tickDelta;
        double y = entity.yo + (entity.getY() - entity.yo) * tickDelta;
        double z = entity.zo + (entity.getZ() - entity.zo) * tickDelta;

        float yaw = interpolateDegrees(entity.yHeadRotO, entity.yHeadRot, tickDelta);
        float pitch = entity.xRotO + (entity.getXRot() - entity.xRotO) * tickDelta;
        return new AnchorPose(
            new AnchorVec3(x, y + entity.getBbHeight() * 0.85, z),
            AnchorPoseMath.fromMinecraftYawPitchRoll(yaw, pitch, 0.0)
        );
    }

    private static float interpolateDegrees(float previous, float current, float tickDelta) {
        float difference = current - previous;
        if (difference > 180f) difference -= 360f;
        if (difference < -180f) difference += 360f;
        return previous + difference * tickDelta;
    }
}
