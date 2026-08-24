package network.azusake.halo.compat.ysm;

import net.minecraft.world.phys.Vec3;
import network.azusake.halo.api.HeadAnchor;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Converts a final YSM Head matrix into Halo's six-degree anchor. */
public final class YsmHeadMath {
    private YsmHeadMath() {}

    public static HeadAnchor toHeadAnchor(
        Matrix4f viewSpace, Vec3 localOffset, Vec3 cameraPos, Matrix4f viewMatrix
    ) {
        if (!YsmV265Adapter.isFinite(viewSpace) || !finite(localOffset)
                || !finite(cameraPos) || !YsmV265Adapter.isFinite(viewMatrix)) {
            return null;
        }
        Matrix4f inverse = new Matrix4f(viewMatrix).invert();
        if (!YsmV265Adapter.isFinite(inverse)) return null;
        Matrix4f world = inverse.mul(viewSpace, new Matrix4f());
        if (!YsmV265Adapter.isFinite(world)) return null;

        Vector4f point = world.transform(new Vector4f(
            (float) localOffset.x, (float) localOffset.y, (float) localOffset.z, 1f));
        Vec3 center = cameraPos.add(point.x, point.y, point.z);

        Vec3 forward = direction(world, 0f, 0f, -1f);
        Vec3 up = direction(world, 0f, 1f, 0f);
        if (!finite(forward) || !finite(up) || forward.lengthSqr() < 1.0e-12
                || up.lengthSqr() < 1.0e-12) return null;
        forward = forward.normalize();
        up = up.subtract(forward.scale(up.dot(forward)));
        if (!finite(up) || up.lengthSqr() < 1.0e-12) return null;
        up = up.normalize();

        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        float pitch = (float) Math.toDegrees(Math.asin(clamp(-forward.y)));
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right0;
        if (Math.abs(forward.dot(worldUp)) > 0.999) {
            float yawRad = (float) Math.toRadians(yaw);
            right0 = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad));
        } else {
            right0 = forward.cross(worldUp).normalize();
        }
        Vec3 headUp0 = right0.cross(forward).normalize();
        float roll = (float) Math.toDegrees(Math.atan2(up.dot(right0), up.dot(headUp0)));
        HeadAnchor result = new HeadAnchor(center, yaw, pitch, roll);
        return finite(result) ? result : null;
    }

    private static Vec3 direction(Matrix4f matrix, float x, float y, float z) {
        Vector4f origin = matrix.transform(new Vector4f(0, 0, 0, 1));
        Vector4f endpoint = matrix.transform(new Vector4f(x, y, z, 1));
        return new Vec3(endpoint.x - origin.x, endpoint.y - origin.y, endpoint.z - origin.z);
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y)
            && Double.isFinite(value.z);
    }

    private static boolean finite(HeadAnchor anchor) {
        return anchor != null && finite(anchor.headCenter()) && Float.isFinite(anchor.yaw())
            && Float.isFinite(anchor.pitch()) && Float.isFinite(anchor.roll());
    }

    private static double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }
}
