package network.azusake.halo.anchor;

import network.azusake.halo.api.v2.AnchorRotation;
import network.azusake.halo.api.v2.AnchorVec3;

/** Pure quaternion and basis helpers shared by capture adapters and physics. */
public final class AnchorPoseMath {

    private static final double EPSILON = 1.0e-12;

    private AnchorPoseMath() {
    }

    public static AnchorRotation fromMinecraftYawPitchRoll(
        double yawDegrees,
        double pitchDegrees,
        double rollDegrees
    ) {
        double halfYaw = Math.toRadians(-yawDegrees) * 0.5;
        double halfPitch = Math.toRadians(pitchDegrees) * 0.5;
        double halfRoll = Math.toRadians(rollDegrees) * 0.5;
        AnchorRotation yaw = new AnchorRotation(0.0, Math.sin(halfYaw), 0.0, Math.cos(halfYaw));
        AnchorRotation pitch = new AnchorRotation(Math.sin(halfPitch), 0.0, 0.0, Math.cos(halfPitch));
        AnchorRotation roll = new AnchorRotation(0.0, 0.0, Math.sin(halfRoll), Math.cos(halfRoll));
        return multiply(multiply(yaw, pitch), roll);
    }

    /** Build a right-handed rotation whose local +Y is up and local +Z is forward. */
    public static AnchorRotation fromForwardUp(AnchorVec3 forwardValue, AnchorVec3 upValue) {
        AnchorVec3 forward = normalize(forwardValue);
        AnchorVec3 projectedUp = subtract(upValue, multiply(forward, dot(upValue, forward)));
        AnchorVec3 up = normalize(projectedUp);
        AnchorVec3 left = normalize(cross(up, forward));

        double m00 = left.x();
        double m01 = up.x();
        double m02 = forward.x();
        double m10 = left.y();
        double m11 = up.y();
        double m12 = forward.y();
        double m20 = left.z();
        double m21 = up.z();
        double m22 = forward.z();

        double x;
        double y;
        double z;
        double w;
        double trace = m00 + m11 + m22;
        if (trace > 0.0) {
            double s = Math.sqrt(trace + 1.0) * 2.0;
            w = 0.25 * s;
            x = (m21 - m12) / s;
            y = (m02 - m20) / s;
            z = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            double s = Math.sqrt(1.0 + m00 - m11 - m22) * 2.0;
            w = (m21 - m12) / s;
            x = 0.25 * s;
            y = (m01 + m10) / s;
            z = (m02 + m20) / s;
        } else if (m11 > m22) {
            double s = Math.sqrt(1.0 + m11 - m00 - m22) * 2.0;
            w = (m02 - m20) / s;
            x = (m01 + m10) / s;
            y = 0.25 * s;
            z = (m12 + m21) / s;
        } else {
            double s = Math.sqrt(1.0 + m22 - m00 - m11) * 2.0;
            w = (m10 - m01) / s;
            x = (m02 + m20) / s;
            y = (m12 + m21) / s;
            z = 0.25 * s;
        }
        return new AnchorRotation(x, y, z, w);
    }

    public static AnchorVec3 rotate(AnchorRotation q, AnchorVec3 v) {
        double qx = q.x();
        double qy = q.y();
        double qz = q.z();
        double qw = q.w();
        double tx = 2.0 * (qy * v.z() - qz * v.y());
        double ty = 2.0 * (qz * v.x() - qx * v.z());
        double tz = 2.0 * (qx * v.y() - qy * v.x());
        return new AnchorVec3(
            v.x() + qw * tx + (qy * tz - qz * ty),
            v.y() + qw * ty + (qz * tx - qx * tz),
            v.z() + qw * tz + (qx * ty - qy * tx)
        );
    }

    public static AnchorRotation multiply(AnchorRotation a, AnchorRotation b) {
        return new AnchorRotation(
            a.w() * b.x() + a.x() * b.w() + a.y() * b.z() - a.z() * b.y(),
            a.w() * b.y() - a.x() * b.z() + a.y() * b.w() + a.z() * b.x(),
            a.w() * b.z() + a.x() * b.y() - a.y() * b.x() + a.z() * b.w(),
            a.w() * b.w() - a.x() * b.x() - a.y() * b.y() - a.z() * b.z()
        );
    }

    private static AnchorVec3 normalize(AnchorVec3 value) {
        double length = Math.sqrt(dot(value, value));
        if (!Double.isFinite(length) || length < EPSILON) {
            throw new IllegalArgumentException("anchor basis vector must be finite and non-zero");
        }
        return multiply(value, 1.0 / length);
    }

    private static double dot(AnchorVec3 a, AnchorVec3 b) {
        return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
    }

    private static AnchorVec3 cross(AnchorVec3 a, AnchorVec3 b) {
        return new AnchorVec3(
            a.y() * b.z() - a.z() * b.y(),
            a.z() * b.x() - a.x() * b.z(),
            a.x() * b.y() - a.y() * b.x()
        );
    }

    private static AnchorVec3 subtract(AnchorVec3 a, AnchorVec3 b) {
        return new AnchorVec3(a.x() - b.x(), a.y() - b.y(), a.z() - b.z());
    }

    private static AnchorVec3 multiply(AnchorVec3 value, double factor) {
        return new AnchorVec3(value.x() * factor, value.y() * factor, value.z() * factor);
    }
}
