package network.azusake.halo.physics;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Pure head-frame math shared by the anchor providers and the frame
 * calculator so every consumer builds the identical orthonormal head basis.
 *
 * <p>All angles are degrees and follow the Minecraft convention (yaw = head
 * yaw, pitch = head pitch, roll = head/camera roll).  The camera/head
 * orientation quaternion used by the anchor frame is
 * {@code rotateY(-yaw) * rotateX(pitch) * rotateZ(roll)}.  Minecraft 1.21.1's
 * {@code Camera} stores the equivalent view-facing orientation with a flipped
 * basis as {@code rotationYXZ(PI - yaw, -pitch, roll)}; see
 * {@link #recoverRollDeg}.</p>
 */
public final class HeadFrameMath {

    private HeadFrameMath() { /* utility class */ }

    /**
     * Build the orthonormal head basis (right, headUp, forward) from
     * yaw/pitch/roll.
     *
 * <p>{@code roll == 0} reproduces the classic basis used before the 6DOF
 * upgrade: forward from yaw/pitch, right = forward × worldUp (with the
 * near-parallel fallback), headUp = right × forward.  A non-zero roll
 * rotates right/headUp around forward using the same convention as the
 * Minecraft camera ({@code rotationYXZ(-yaw, pitch, roll)}):
 * {@code right' = right·cos(roll) − headUp·sin(roll)},
 * {@code headUp' = headUp·cos(roll) + right·sin(roll)}.</p>
     */
    public static HeadFrame of(float yawDeg, float pitchDeg, float rollDeg) {
        float yawRad = (float) Math.toRadians(yawDeg);
        float pitchRad = (float) Math.toRadians(pitchDeg);
        float rollRad = (float) Math.toRadians(rollDeg);

        Vec3 forward = new Vec3(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        ).normalize();

        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right;
        if (Math.abs(forward.dot(worldUp)) > 0.999) {
            // Forward nearly parallel to worldUp — the cross product
            // degenerates.  Use a fallback continuous with the cross-product
            // result: forward × worldUp normalised to (–cos yaw, 0, –sin yaw)
            // for cos(pitch) > 0 (the MC pitch range).
            right = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad));
        } else {
            right = forward.cross(worldUp).normalize();
        }
        Vec3 headUp = right.cross(forward).normalize();

        if (Math.abs(rollRad) > 0.001f) {
            double cosRoll = Math.cos(rollRad);
            double sinRoll = Math.sin(rollRad);
            Vec3 newRight = right.scale(cosRoll).subtract(headUp.scale(sinRoll));
            Vec3 newHeadUp = headUp.scale(cosRoll).add(right.scale(sinRoll));
            right = newRight.normalize();
            headUp = newHeadUp.normalize();
        }

        return new HeadFrame(right, headUp, forward);
    }

    /**
     * Recover the roll (degrees) folded into a Minecraft camera/head rotation.
     *
     * <p>The 1.21.1 {@code Camera} has no {@code getRoll()}; a non-zero roll
     * (e.g. injected by a camera mod) is folded into
     * {@code Camera.getRotation()} as
     * {@code rotationYXZ(PI - yaw, -pitch, roll)}.  This strips that version's
     * yaw/pitch component and returns the remaining pure Z-rotation angle,
     * which can be fed into {@link #of}.</p>
     *
     * @param yawDeg         camera yaw in degrees
     * @param pitchDeg       camera pitch in degrees
     * @param cameraRotation the full camera rotation quaternion
     * @return roll in degrees within (−180, 180]
     */
    public static float recoverRollDeg(float yawDeg, float pitchDeg, Quaternionf cameraRotation) {
        // Since 1.21, Camera#setRotation stores a view-facing quaternion with
        // a 180-degree yaw basis flip and inverted pitch:
        // rotateY(PI - yaw) * rotateX(-pitch) * rotateZ(roll).
        // Stripping the matching yaw/pitch part leaves a pure Z rotation:
        // (x=0, y=0, z=sin(roll/2), w=cos(roll/2)).
        Quaternionf qYawPitch = new Quaternionf()
            .rotateY((float) Math.PI - (float) Math.toRadians(yawDeg))
            .rotateX(-(float) Math.toRadians(pitchDeg));
        Quaternionf qRoll = new Quaternionf(qYawPitch).conjugate().mul(cameraRotation);
        return (float) Math.toDegrees(2.0 * Math.atan2(qRoll.z, qRoll.w));
    }

    /**
     * Orthonormal head basis in world space: {@code right} (entity's right),
     * {@code headUp}, {@code forward} (look direction).
     */
    public record HeadFrame(Vec3 right, Vec3 headUp, Vec3 forward) {}
}
