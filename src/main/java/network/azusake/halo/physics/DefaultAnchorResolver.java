package network.azusake.halo.physics;

import network.azusake.halo.anchor.AnchorPoseMath;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorVec3;
import net.minecraft.entity.LivingEntity;

/** Internal Vanilla fallback for entities without a render-time capture. */
public final class DefaultAnchorResolver {

    private DefaultAnchorResolver() {
    }

    public static AnchorPose resolve(LivingEntity entity, float tickDelta) {
        double x = entity.prevX + (entity.getX() - entity.prevX) * tickDelta;
        double y = entity.prevY + (entity.getY() - entity.prevY) * tickDelta;
        double z = entity.prevZ + (entity.getZ() - entity.prevZ) * tickDelta;

        float yaw = interpolateDegrees(entity.prevHeadYaw, entity.headYaw, tickDelta);
        float pitch = entity.prevPitch + (entity.getPitch() - entity.prevPitch) * tickDelta;
        return network.azusake.halo.core.AnchorFallback.entity(new network.azusake.halo.core.Vec3d(x,y,z),entity.getHeight(),yaw,pitch);
    }

    private static float interpolateDegrees(float previous, float current, float tickDelta) {
        float difference = current - previous;
        if (difference > 180f) difference -= 360f;
        if (difference < -180f) difference += 360f;
        return previous + difference * tickDelta;
    }
}
