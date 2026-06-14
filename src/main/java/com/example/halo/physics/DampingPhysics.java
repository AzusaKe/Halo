package com.example.halo.physics;

import com.example.halo.data.HaloDampingConfig;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * Stateless damping physics engine for halo position and rotation.
 *
 * <p>Every method is {@code static} and takes a mutable
 * {@link HaloDampingState} parameter that carries per-instance history
 * across ticks.  The core idea is simple:</p>
 *
 * <ul>
 *   <li><b>Position:</b> {@code newPos = current + k * prevRelPos}
 *       where {@code k = 1 − linearFactor}.  The result is clamped to
 *       {@code maxLinearDistance}.</li>
 *   <li><b>Rotation:</b> spherical linear interpolation (slerp) from the
 *       previous damped rotation toward the target, weighted by
 *       {@code angularK = 1 − angularFactor}.  The result is clamped to
 *       {@code maxAngularDegrees}.</li>
 * </ul>
 *
 * <p>When {@link HaloDampingState#needsSnap} is {@code true} (first
 * spawn, teleport) both methods return an identity offset — the halo
 * snaps to the anchor instantly with no sliding.</p>
 */
public final class DampingPhysics {

    private DampingPhysics() {
        // utility class — no instances
    }

    /**
     * Compute the damped relative position for the current tick.
     *
     * @param current  the current offset from the entity anchor (world-space
     *                 halo centre minus anchor position)
     * @param target   the desired target offset (typically {@link Vec3d#ZERO}
     *                 — right on the anchor)
     * @param damping  damping configuration (linear factor, max distance)
     * @param state    mutable per-instance state; updated in-place
     * @return the new damped relative position (offset from anchor)
     */
    public static Vec3d computeDampedPosition(
        Vec3d current,
        Vec3d target,
        HaloDampingConfig damping,
        HaloDampingState state
    ) {
        if (state.needsSnap) {
            state.needsSnap = false;
            state.prevRelativePosition = Vec3d.ZERO;
            return Vec3d.ZERO;
        }

        // Relative offset from the previous frame
        Vec3d offset = state.prevRelativePosition;

        // Damping blend: new = current + k * prevRelPos
        double k = 1.0 - damping.linearFactor();
        Vec3d damped = current.add(offset.multiply(k));

        // Hard-clamp to max distance (prevent runaway drift)
        double distance = damped.length();
        double maxDist = damping.maxLinearDistance();
        if (distance > maxDist) {
            damped = damped.normalize().multiply(maxDist);
        }

        // Persist for the next tick
        state.prevRelativePosition = damped;

        return damped;
    }

    /**
     * Compute the damped relative rotation for the current tick.
     *
     * <p>Uses quaternion slerp to interpolate from the previous frame's
     * damped rotation toward {@code target}.  The interpolation weight is
     * {@code angularK = 1 − angularFactor}, so an {@code angularFactor} of
     * 0 means instant snap, while 1 means no movement at all.</p>
     *
     * @param current  the current relative rotation (unused in the standard
     *                 damping formula but accepted for API symmetry)
     * @param target   the desired target rotation (typically an identity
     *                 quaternion — no offset from anchor)
     * @param damping  damping configuration (angular factor, max angle)
     * @param state    mutable per-instance state; updated in-place
     * @return the new damped relative rotation
     */
    public static Quaternionf computeDampedRotation(
        Quaternionf current,
        Quaternionf target,
        HaloDampingConfig damping,
        HaloDampingState state
    ) {
        if (state.needsSnap) {
            state.prevRelativeRotation = new Quaternionf();
            return new Quaternionf();
        }

        // Slerp from previous toward target
        double angularK = 1.0 - damping.angularFactor();
        Quaternionf result = new Quaternionf(state.prevRelativeRotation);
        result.slerp(target, (float) angularK);

        // Clamp angular deviation: extract rotation axis, clamp half-angle, reconstruct
        double wAbs = Math.min(1.0, Math.abs((double) result.w));
        double halfAngleRad = Math.acos(wAbs);
        float angleDeg = (float) Math.toDegrees(2.0 * halfAngleRad);

        double maxAngle = damping.maxAngularDegrees();
        if (angleDeg > maxAngle && angleDeg > 0.0001f) {
            // Extract the rotation axis from the quaternion's xyz components
            // q = (axis * sin(θ/2), cos(θ/2))
            float sinHalf = (float) Math.sin(halfAngleRad);
            if (sinHalf > 0.0001f) {
                float axisX = result.x / sinHalf;
                float axisY = result.y / sinHalf;
                float axisZ = result.z / sinHalf;

                // Reconstruct with clamped angle
                float clampedHalf = (float) Math.toRadians(maxAngle / 2.0);
                float newSinHalf = (float) Math.sin(clampedHalf);
                result.x = axisX * newSinHalf;
                result.y = axisY * newSinHalf;
                result.z = axisZ * newSinHalf;
                result.w = (float) Math.cos(clampedHalf);
            }
        }

        // Persist for the next tick
        state.prevRelativeRotation = new Quaternionf(result);

        return result;
    }
}
