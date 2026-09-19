package network.azusake.halo.physics;

import org.joml.Quaternionf;

/** Minecraft 1.21.1 camera-quaternion decoding kept inside the platform adapter. */
final class CameraRollMath {
    private CameraRollMath() {}

    static float recoverRollDeg(float yawDeg, float pitchDeg, Quaternionf cameraRotation) {
        // Since 1.21, Camera#setRotation stores the view-facing orientation as
        // rotationYXZ(PI - yaw, -pitch, roll), rather than the 1.20.1 basis.
        Quaternionf yawPitch = new Quaternionf()
            .rotateY((float) Math.PI - (float) Math.toRadians(yawDeg))
            .rotateX(-(float) Math.toRadians(pitchDeg));
        Quaternionf roll = new Quaternionf(yawPitch).conjugate().mul(cameraRotation);
        return (float) Math.toDegrees(2.0 * Math.atan2(roll.z, roll.w));
    }
}
