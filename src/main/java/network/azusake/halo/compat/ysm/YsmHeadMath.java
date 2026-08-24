package network.azusake.halo.compat.ysm;

import network.azusake.halo.api.HeadAnchor;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Converts a final YSM Head locator matrix into Halo's 6-DOF anchor. */
public final class YsmHeadMath {

    private YsmHeadMath() {
    }

    /** Convert using the exact camera frame that produced this capture. */
    public static HeadAnchor toHeadAnchor(YsmHeadCapture.CapturedHead captured, Vec3d localOffset) {
        if (captured == null) {
            return null;
        }
        return toHeadAnchor(
            captured.headMatrix(),
            localOffset,
            captured.cameraPos(),
            captured.viewMatrix()
        );
    }

    public static HeadAnchor toHeadAnchor(
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

        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        float pitch = (float) Math.toDegrees(Math.asin(clamp(-forward.y)));

        Vec3d worldUp = new Vec3d(0, 1, 0);
        Vec3d right0;
        if (Math.abs(forward.dotProduct(worldUp)) > 0.999) {
            float yawRad = (float) Math.toRadians(yaw);
            right0 = new Vec3d(-Math.cos(yawRad), 0, -Math.sin(yawRad));
        } else {
            right0 = forward.crossProduct(worldUp).normalize();
        }
        Vec3d headUp0 = right0.crossProduct(forward).normalize();
        float roll = (float) Math.toDegrees(Math.atan2(
            up.dotProduct(right0),
            up.dotProduct(headUp0)
        ));

        HeadAnchor anchor = new HeadAnchor(center, yaw, pitch, roll);
        return isFinite(anchor) ? anchor : null;
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

    private static boolean isFinite(HeadAnchor anchor) {
        return anchor != null && isFinite(anchor.headCenter())
            && Float.isFinite(anchor.yaw())
            && Float.isFinite(anchor.pitch())
            && Float.isFinite(anchor.roll());
    }

    private static double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }
}
