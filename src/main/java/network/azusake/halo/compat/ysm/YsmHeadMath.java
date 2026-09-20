package network.azusake.halo.compat.ysm;

import net.minecraft.world.phys.Vec3;
import network.azusake.halo.anchor.AnchorPoseMath;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorRotation;
import network.azusake.halo.api.v2.AnchorVec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Converts a final YSM Head matrix into the API v2 world-space pose. */
public final class YsmHeadMath {
    private YsmHeadMath() {}

    public static AnchorPose toAnchorPose(
        Matrix4f viewSpace, Vec3 localOffset, Vec3 cameraPos, Matrix4f viewMatrix
    ) {
        if (!YsmV265Adapter.isFinite(viewSpace) || !finite(localOffset)
                || !finite(cameraPos) || !YsmV265Adapter.isFinite(viewMatrix)) return null;
        Matrix4f inverse = new Matrix4f(viewMatrix).invert();
        if (!YsmV265Adapter.isFinite(inverse)) return null;
        Matrix4f world = inverse.mul(viewSpace, new Matrix4f());
        if (!YsmV265Adapter.isFinite(world)) return null;

        Vector4f point = world.transform(new Vector4f(
            (float) localOffset.x, (float) localOffset.y, (float) localOffset.z, 1f));
        Vec3 center = cameraPos.add(point.x, point.y, point.z);
        try {
            AnchorRotation rotation = AnchorPoseMath.fromForwardUp(
                direction(world, 0f, 0f, -1f), direction(world, 0f, 1f, 0f));
            return new AnchorPose(new AnchorVec3(center.x, center.y, center.z), rotation);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static AnchorVec3 direction(Matrix4f matrix, float x, float y, float z) {
        Vector4f origin = matrix.transform(new Vector4f(0, 0, 0, 1));
        Vector4f endpoint = matrix.transform(new Vector4f(x, y, z, 1));
        return new AnchorVec3(endpoint.x - origin.x, endpoint.y - origin.y, endpoint.z - origin.z);
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y)
            && Double.isFinite(value.z);
    }
}
