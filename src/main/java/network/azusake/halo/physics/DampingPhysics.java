package network.azusake.halo.physics;

import network.azusake.halo.data.HaloDampingConfig;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * Stateless damping physics engine for halo position and rotation.
 *
 * <p>Every method is {@code static} and takes a mutable
 * {@link HaloDampingState} parameter that carries per-instance history
 * across ticks.  The core idea:</p>
 *
 * <h3>Position damping</h3>
 * <pre>
 *   T  = target position (head anchor)
 *   H  = previous frame halo position
 *   R  = T − H  (gap vector from halo to target)
 *   k_f = 1 − (1 − k)^(Δt / 0.05)   where k = linearFactor
 *   S  = k_f × R  (displacement this frame)
 *   H_new = H + S = (1−k_f)×H + k_f×T
 *   d  = |T − H_new| = (1−k_f)×|R|  (remaining gap)
 *   if d &gt; max_d  →  clamp: H_new = T − normalize(T−H) × max_d
 * </pre>
 *
 * <p>The {@code Δt/0.05} exponent scales the damping factor to be
 * frame-rate-independent: at 20 TPS (Δt = 0.05 s) the factor is exactly
 * {@code k}, at 60 FPS (Δt ≈ 0.0167 s) it is proportionally smaller so
 * the halo converges at the same real-time speed regardless of frame rate.</p>
 *
 * <h3>Rotation damping</h3>
 * <p>Uses quaternion slerp with the same frame-rate-independent factor:
 * {@code result = slerp(prev, target, k_f)}.  Angular deviation is clamped
 * to {@code maxAngularDegrees}.</p>
 *
 * <p>When {@link HaloDampingState#needsSnap} is {@code true} (first
 * spawn, teleport) both methods return an identity offset — the halo
 * snaps to the anchor instantly with no sliding.</p>
 */
public final class DampingPhysics {

    /** Reference tick duration in seconds (50 ms = 20 TPS). */
    private static final double REFERENCE_TICK = 0.05;

    private DampingPhysics() {
        // utility class — no instances
    }

    /**
     * Compute the damped relative position for the current frame/tick.
     *
     * <p>Calling convention: {@code current} is the offset vector from
     * target to the halo's previous position (halo − target).
     * {@code target_param} is the desired offset from anchor (typically
     * {@link Vec3d#ZERO}).  The returned value is the new damped offset,
     * which the caller adds to the absolute target position.</p>
     *
     * @param current    the current offset from the entity anchor
     *                   (world-space halo centre minus anchor position)
     * @param targetParam the desired target offset (typically {@link Vec3d#ZERO}
     *                   — right on the anchor)
     * @param damping   damping configuration (linear factor, max distance)
     * @param state     mutable per-instance state; updated in-place
     * @param deltaTime seconds since the last update (frame or tick)
     * @return the new damped relative position (offset from anchor)
     */
    public static Vec3d computeDampedPosition(
        Vec3d current,
        Vec3d targetParam,
        HaloDampingConfig damping,
        HaloDampingState state,
        double deltaTime
    ) {
        if (state.needsSnap) {
            state.needsSnap = false;
            state.prevRelativePosition = Vec3d.ZERO;
            return Vec3d.ZERO;
        }

        // Frame-rate-independent exponential factor
        // k_f = 1 − (1 − k)^(Δt / 0.05)
        double k = damping.linearFactor();
        k = Math.max(0.0, Math.min(1.0, k));
        double exponent = deltaTime / REFERENCE_TICK;

        // Guard: degenerate deltaTime (first frame, resume from pause, etc.)
        if (exponent <= 0.0) {
            exponent = 1.0; // behave as a single tick
        }
        // Cap: extremely long frames (>500ms) would overshoot
        if (exponent > 10.0) {
            exponent = 10.0; // max ~0.5 s worth of convergence per step
        }

        double kF;
        // k_f = 1 − (1−k)^(Δt/0.05)
        // k=0 → k_f=0   (frozen — halo never moves toward target)
        // k=1 → k_f=1   (instant snap — halo jumps to target immediately)
        if (k >= 0.999) {
            kF = 1.0; // effectively instant snap
        } else if (k <= 0.001) {
            kF = 0.0; // effectively frozen
        } else {
            kF = 1.0 - Math.pow(1.0 - k, exponent);
        }
        kF = Math.max(0.0, Math.min(1.0, kF));

        // H_new = H − k_f × (H − T)  ≡  current − k_f × current  ≡  current × (1 − k_f)
        // (keeping the existing calling convention where "current" = H − T)
        Vec3d damped = current.multiply(1.0 - kF);

        // targetParam is typically ZERO; include it for API consistency
        if (targetParam.lengthSquared() > 0.0) {
            damped = damped.add(targetParam.multiply(kF));
        }

        // Hard-clamp to max distance (the remaining gap d must not exceed max_d)
        double distance = damped.length();
        double maxDist = damping.maxLinearDistance();
        if (distance > maxDist && distance > 1e-9) {
            damped = damped.normalize().multiply(maxDist);
        }

        // Persist for the next frame/tick
        state.prevRelativePosition = damped;

        return damped;
    }

    /**
     * Compute the damped relative rotation for the current frame/tick.
     *
     * <p>Uses quaternion slerp to interpolate from the previous frame's
     * damped rotation toward {@code target}.  The interpolation weight is
     * {@code angularK_f = 1 − (1 − angularFactor)^(Δt/0.05)}, giving
     * frame-rate-independent angular convergence.  An {@code angularFactor}
     * of 0 means frozen (halo never rotates toward target), while 1 means
     * instant snap to the target rotation.</p>
     *
     * @param current    the current relative rotation (unused in the standard
     *                   damping formula but accepted for API symmetry)
     * @param target     the desired target rotation (typically an identity
     *                   quaternion — no offset from anchor)
     * @param damping    damping configuration (angular factor, max angle)
     * @param state      mutable per-instance state; updated in-place
     * @param deltaTime  seconds since the last update
     * @return the new damped relative rotation
     */
    public static Quaternionf computeDampedRotation(
        Quaternionf current,
        Quaternionf target,
        HaloDampingConfig damping,
        HaloDampingState state,
        double deltaTime
    ) {
        if (state.needsSnap) {
            state.prevRelativeRotation = new Quaternionf();
            return new Quaternionf();
        }

        // Frame-rate-independent angular factor
        double angularK = damping.angularFactor();
        angularK = Math.max(0.0, Math.min(1.0, angularK));
        double exponent = deltaTime / REFERENCE_TICK;
        if (exponent <= 0.0) exponent = 1.0;
        if (exponent > 10.0) exponent = 10.0;

        double angularKF;
        // k_f = 1 − (1−k)^(Δt/0.05)
        if (angularK >= 0.999) {
            angularKF = 1.0; // effectively instant snap
        } else if (angularK <= 0.001) {
            angularKF = 0.0; // effectively frozen
        } else {
            angularKF = 1.0 - Math.pow(1.0 - angularK, exponent);
        }
        angularKF = Math.max(0.0, Math.min(1.0, angularKF));

        // Slerp from previous toward target
        Quaternionf result = new Quaternionf(state.prevRelativeRotation);
        result.slerp(target, (float) angularKF);

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

        // Persist for the next frame/tick
        state.prevRelativeRotation = new Quaternionf(result);

        return result;
    }
}
