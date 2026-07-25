package network.azusake.halo.animation;

/**
 * Easing functions for transition animations.
 *
 * <p>Each function maps a linear progress value {@code t} (0.0 → 1.0) to an
 * eased value (also 0.0 → 1.0 at the endpoints) that controls how the
 * transition interpolates between its start and end states.</p>
 */
public enum EasingType {

    /** Constant speed from start to end. */
    LINEAR {
        @Override
        public double evaluate(double t) {
            return t;
        }
    },

    /** Starts fast, decelerates toward the end (cubic ease-out). */
    EASE_OUT_CUBIC {
        @Override
        public double evaluate(double t) {
            double inv = 1.0 - t;
            return 1.0 - inv * inv * inv;
        }
    },

    /** Slow start, fast middle, slow end (cubic ease-in-out). */
    EASE_IN_OUT_CUBIC {
        @Override
        public double evaluate(double t) {
            if (t < 0.5) {
                return 4.0 * t * t * t;
            } else {
                double f = -2.0 * t + 2.0;
                return 1.0 - (f * f * f) / 2.0;
            }
        }
    };

    /**
     * Evaluate the easing curve at the given linear progress value.
     *
     * @param t linear progress in [0, 1]
     * @return the eased value, with easing(0)=0 and easing(1)=1
     */
    public abstract double evaluate(double t);

    /**
     * Parse an easing type name (case-insensitive).
     *
     * @param s the easing type string (e.g. "linear", "ease_out_cubic")
     * @return the matching {@link EasingType}
     * @throws IllegalArgumentException if the name is not recognized
     */
    public static EasingType fromString(String s) {
        if (s == null || s.isEmpty()) {
            return LINEAR;
        }
        return EasingType.valueOf(s.toUpperCase().replace("-", "_"));
    }
}
