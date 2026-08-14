package network.azusake.halo.animation;

/**
 * Pure per-axis helpers for rotation transition endpoints (F8).
 *
 * <p>{@code degrees} on a rotation property forces a <em>minimum signed
 * travel</em> for each axis: the effective end {@code from + d} satisfies
 * {@code |d| >= |degrees|}, {@code sign(d) == sign(degrees)} and
 * {@code (from + d) ≡ to (mod 360)}, choosing the smallest {@code |d|}.
 * A missing or zero {@code degrees} keeps the raw {@code from → to}
 * interpolation (legacy behaviour).</p>
 */
public final class RotationTravel {

    private RotationTravel() {}

    /**
     * Compute the effective per-axis end value for one axis.
     *
     * @param from    authored start (degrees)
     * @param to      authored/derived end (degrees)
     * @param degrees minimum signed travel (degrees); 0 = raw interpolation
     * @return the effective end value the element should interpolate to
     */
    public static float effectiveEnd(float from, float to, float degrees) {
        if (degrees == 0f) {
            return to; // back-compat: plain from→to interpolation
        }
        float r = ((to - from) % 360f + 360f) % 360f; // [0, 360)
        float d;
        if (degrees > 0f) {
            d = r + 360f * (float) Math.ceil((degrees - r) / 360.0);
        } else {
            d = r - 360f * (float) Math.ceil((r - degrees) / 360.0);
        }
        return from + d;
    }

    /**
     * Per-axis {@link #effectiveEnd(float, float, float)}.  Shorter inputs are
     * padded with 0 (missing axes = no minimum travel).
     *
     * @return array of length {@code max(from.length, to.length, degrees.length)}
     */
    public static float[] effectiveEnds(float[] from, float[] to, float[] degrees) {
        int len = Math.max(from.length, Math.max(to.length, degrees.length));
        float[] result = new float[len];
        for (int i = 0; i < len; i++) {
            float f = i < from.length ? from[i] : 0f;
            float t = i < to.length ? to[i] : 0f;
            float deg = i < degrees.length ? degrees[i] : 0f;
            result[i] = effectiveEnd(f, t, deg);
        }
        return result;
    }

    /** Negate every component of the given degrees vector (reversal mirror). */
    public static float[] negated(float[] degrees) {
        float[] result = new float[degrees.length];
        for (int i = 0; i < degrees.length; i++) {
            result[i] = -degrees[i];
        }
        return result;
    }
}
