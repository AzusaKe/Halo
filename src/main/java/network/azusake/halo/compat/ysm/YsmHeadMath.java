package network.azusake.halo.compat.ysm;

import network.azusake.halo.anchor.AnchorPoseMath;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorRotation;
import network.azusake.halo.api.v2.AnchorVec3;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Converts a final YSM Head locator matrix into Halo's 6-DOF anchor. */
public final class YsmHeadMath {

    private YsmHeadMath() {
    }

    /** Convert using the exact camera frame that produced this capture. */
    public static AnchorPose toAnchorPose(YsmHeadCapture.CapturedHead captured, Vec3d localOffset) {
        if (captured == null) {
            return null;
        }
        return toAnchorPose(
            captured.headMatrix(),
            localOffset,
            captured.cameraPos(),
            captured.viewMatrix()
        );
    }

    public static AnchorPose toAnchorPose(
        Matrix4f viewSpace,
        Vec3d localOffset,
        Vec3d cameraPos,
        Matrix4f viewMatrix
    ) {
        if (!YsmV265Adapter.isFinite(viewSpace)
            || localOffset == null || !isFinite(localOffset)
            || cameraPos == null || !isFinite(cameraPos)) {
            return null;
        }

        Matrix4f world = viewToWorld(viewSpace, viewMatrix);
        if (!YsmV265Adapter.isFinite(world)) {
            return null;
        }

        Vector4f point = world.transform(new Vector4f(
            (float) localOffset.x,
            (float) localOffset.y,
            (float) localOffset.z,
            1f
        ));
        Vec3d center = new Vec3d(
            cameraPos.x + point.x,
            cameraPos.y + point.y,
            cameraPos.z + point.z
        );

        // YSM/Bedrock geometry is Y-up; its model front is local -Z.
        Vec3d forward = direction(world, 0f, 0f, -1f);
        Vec3d up = direction(world, 0f, 1f, 0f);
        if (!isFinite(forward) || !isFinite(up)
            || forward.lengthSquared() < 1.0e-12 || up.lengthSquared() < 1.0e-12) {
            return null;
        }
        forward = forward.normalize();
        up = up.subtract(forward.multiply(up.dotProduct(forward)));
        if (!isFinite(up) || up.lengthSquared() < 1.0e-12) {
            return null;
        }
        up = up.normalize();

        try {
            AnchorRotation rotation = AnchorPoseMath.fromForwardUp(
                new AnchorVec3(forward.x, forward.y, forward.z),
                new AnchorVec3(up.x, up.y, up.z)
            );
            return new AnchorPose(new AnchorVec3(center.x, center.y, center.z), rotation);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static Matrix4f viewToWorld(Matrix4f viewSpace, Matrix4f viewMatrix) {
        if (viewMatrix == null || !YsmV265Adapter.isFinite(viewMatrix)) {
            return null;
        }
        Matrix4f inverse = new Matrix4f(viewMatrix).invert();
        if (!YsmV265Adapter.isFinite(inverse)) {
            return null;
        }
        return inverse.mul(viewSpace, new Matrix4f());
    }

    private static Vec3d direction(Matrix4f matrix, float x, float y, float z) {
        Vector4f origin = matrix.transform(new Vector4f(0f, 0f, 0f, 1f));
        Vector4f endpoint = matrix.transform(new Vector4f(x, y, z, 1f));
        return new Vec3d(endpoint.x - origin.x, endpoint.y - origin.y, endpoint.z - origin.z);
    }

    private static boolean isFinite(Vec3d value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

}
