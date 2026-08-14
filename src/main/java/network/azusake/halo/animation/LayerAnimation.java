package network.azusake.halo.animation;

import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.util.Collections;
import java.util.List;

/**
 * Per-layer visual animation — a collection of {@link AnimationTerm}s
 * organised by axis (offset: x/y/z, rotation: yaw/pitch/roll, scale: x/y/z)
 * plus two scalar channels (alpha, glow).
 *
 * <p>All terms on a given axis are summed (linear superposition).
 * Offset terms produce values in <b>blocks</b>; rotation terms produce
 * values in <b>degrees</b> (converted to radians when building the
 * quaternion in {@link #evaluateRotation(double)}); scale terms produce
 * <b>delta factors</b> added to a base of 1.0 (e.g. {@code sin(A=0.1)}
 * oscillates between 0.9 and 1.1); alpha and glow are scalar channels whose
 * terms are summed directly and clamped to {@code [0, 1]} — an empty channel
 * evaluates to 1.0 (fully opaque / full glow), while a populated channel
 * evaluates to {@code clamp(sum(terms), 0, 1)}.</p>
 *
 * <p>This animation is <em>purely visual</em> — it is applied as extra
 * matrix-stack transforms during rendering and does not affect the
 * physics-based anchor-frame computation.</p>
 *
 * @param offsetX       terms driving the local-X translation offset
 * @param offsetY       terms driving the local-Y translation offset
 * @param offsetZ       terms driving the local-Z translation offset
 * @param rotationYaw   terms driving the yaw rotation (degrees)
 * @param rotationPitch terms driving the pitch rotation (degrees)
 * @param rotationRoll  terms driving the roll rotation (degrees)
 * @param scaleX        terms driving the local-X scale factor (delta from 1.0)
 * @param scaleY        terms driving the local-Y scale factor (delta from 1.0)
 * @param scaleZ        terms driving the local-Z scale factor (delta from 1.0)
 * @param alpha         terms driving the layer opacity (delta from 1.0, clamped to [0, 1])
 * @param glow          terms driving the glow intensity (delta from 1.0, clamped to [0, 1])
 */
public record LayerAnimation(
    List<AnimationTerm> offsetX,
    List<AnimationTerm> offsetY,
    List<AnimationTerm> offsetZ,
    List<AnimationTerm> rotationYaw,
    List<AnimationTerm> rotationPitch,
    List<AnimationTerm> rotationRoll,
    List<AnimationTerm> scaleX,
    List<AnimationTerm> scaleY,
    List<AnimationTerm> scaleZ,
    List<AnimationTerm> alpha,
    List<AnimationTerm> glow
) {
    /** Sentinel instance with no terms on any channel. */
    public static final LayerAnimation EMPTY = new LayerAnimation(
        List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(),
        List.of(), List.of());

    /**
     * Create a LayerAnimation with defensive copies of each list.
     */
    public LayerAnimation {
        offsetX = List.copyOf(offsetX);
        offsetY = List.copyOf(offsetY);
        offsetZ = List.copyOf(offsetZ);
        rotationYaw = List.copyOf(rotationYaw);
        rotationPitch = List.copyOf(rotationPitch);
        rotationRoll = List.copyOf(rotationRoll);
        scaleX = List.copyOf(scaleX);
        scaleY = List.copyOf(scaleY);
        scaleZ = List.copyOf(scaleZ);
        alpha = List.copyOf(alpha);
        glow = List.copyOf(glow);
    }

    // ------------------------------------------------------------------
    // Evaluation
    // ------------------------------------------------------------------

    /**
     * Evaluate the total animated position offset at time {@code t}.
     * All terms per axis are summed.
     *
     * @param t wall-clock time in seconds
     * @return offset vector in blocks (local space)
     */
    public Vec3d evaluateOffset(double t) {
        return new Vec3d(
            sumTerms(offsetX, t),
            sumTerms(offsetY, t),
            sumTerms(offsetZ, t));
    }

    /**
     * Evaluate the total animated rotation at time {@code t} as a
     * quaternion in <b>YXZ Euler order</b> (matching the existing
     * layer-rotation convention).
     *
     * <p>Rotation term values are in <b>degrees</b>; this method
     * converts them to radians internally.</p>
     *
     * @param t wall-clock time in seconds
     * @return rotation quaternion (identity if no rotation terms)
     */
    public Quaternionf evaluateRotation(double t) {
        float yaw   = (float) Math.toRadians(sumTerms(rotationYaw, t));
        float pitch = (float) Math.toRadians(sumTerms(rotationPitch, t));
        float roll  = (float) Math.toRadians(sumTerms(rotationRoll, t));

        if (yaw == 0f && pitch == 0f && roll == 0f) {
            return new Quaternionf(); // identity — avoid unnecessary multiply
        }

        // YXZ order, matching HaloDefinitionDeserializer.parseLayer()
        return new Quaternionf().rotateY(yaw).rotateX(pitch).rotateZ(roll);
    }

    /**
     * Evaluate the total animated scale factors at time {@code t}.
     * Each axis produces a multiplicative factor: {@code 1.0 + sum(terms)}.
     *
     * <p>Scale terms are <b>delta values</b> added to a base of 1.0.
     * For example, {@code sin(A=0.1)} oscillates between 0.9 and 1.1.</p>
     *
     * @param t wall-clock time in seconds
     * @return per-axis scale factors (1.0 = no scaling on that axis)
     */
    public float[] evaluateScale(double t) {
        return new float[] {
            (float) (1.0 + sumTerms(scaleX, t)),
            (float) (1.0 + sumTerms(scaleY, t)),
            (float) (1.0 + sumTerms(scaleZ, t))
        };
    }

    /**
     * Evaluate the animated layer opacity at time {@code t}.
     * An empty alpha channel evaluates to 1.0 (fully opaque); otherwise the
     * result is {@code clamp(sum(terms), 0, 1)} — 0 means fully transparent
     * (layer invisible), 1 means fully opaque.
     *
     * @param t wall-clock time in seconds
     * @return layer opacity factor in {@code [0, 1]}
     */
    public float evaluateAlpha(double t) {
        if (alpha.isEmpty()) return 1.0f;
        return (float) clamp01(sumTerms(alpha, t));
    }

    /**
     * Evaluate the animated glow intensity at time {@code t}.
     * An empty glow channel evaluates to 1.0 (full glow); otherwise the
     * result is {@code clamp(sum(terms), 0, 1)} — 0 means no glow,
     * 1 means full glow. When the group is {@code glowing} this drives the
     * primitive's own brightness; when not {@code glowing} it has no effect.
     *
     * @param t wall-clock time in seconds
     * @return glow intensity factor in {@code [0, 1]}
     */
    public float evaluateGlow(double t) {
        if (glow.isEmpty()) return 1.0f;
        return (float) clamp01(sumTerms(glow, t));
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /**
     * Returns {@code true} if no channel has any animation terms.
     * Use this as a fast-path skip at render time.
     */
    public boolean isEmpty() {
        return offsetX.isEmpty() && offsetY.isEmpty() && offsetZ.isEmpty()
            && rotationYaw.isEmpty() && rotationPitch.isEmpty() && rotationRoll.isEmpty()
            && scaleX.isEmpty() && scaleY.isEmpty() && scaleZ.isEmpty()
            && alpha.isEmpty() && glow.isEmpty();
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private static double sumTerms(List<AnimationTerm> terms, double t) {
        double sum = 0.0;
        for (AnimationTerm term : terms) {
            sum += term.evaluate(t);
        }
        return sum;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
