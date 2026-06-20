package network.azusake.halo.animation;

/**
 * Sealed hierarchy for per-layer animation terms.
 *
 * <p>Each term is a scalar function of time {@code y = f(t)}.
 * Multiple terms on the same axis are summed (linear superposition),
 * enabling complex periodic motion from simple building blocks.</p>
 *
 * <h3>Term types</h3>
 * <ul>
 *   <li>{@link Sin} — sinusoidal oscillation, useful for bobbing/swaying</li>
 *   <li>{@link Cos} — cosine oscillation, same as sin with a π/2 phase lead</li>
 *   <li>{@link Linear} — constant-rate progression, useful for spinning</li>
 * </ul>
 *
 * <h3>Units</h3>
 * <p>The raw {@code evaluate()} return value is in the natural unit of
 * whatever axis this term is bound to:</p>
 * <ul>
 *   <li>Position axes (x, y, z) → <b>blocks</b> (metres)</li>
 *   <li>Rotation axes (yaw, pitch, roll) → <b>degrees</b></li>
 * </ul>
 * <p>The caller is responsible for converting degrees to radians when
 * building a rotation quaternion.</p>
 *
 * @see LayerAnimation
 */
public sealed interface AnimationTerm
    permits AnimationTerm.Sin, AnimationTerm.Cos, AnimationTerm.Linear {

    /**
     * Evaluate this term at the given wall-clock time.
     *
     * @param t elapsed wall-clock time in seconds
     * @return the scalar value of this term at time {@code t}
     */
    double evaluate(double t);

    // ------------------------------------------------------------------
    // Implementations
    // ------------------------------------------------------------------

    /**
     * Sinusoidal oscillation: {@code A * sin(omega * pi * t + phi)}.
     *
     * @param A     amplitude (peak deviation from zero)
     * @param omega angular frequency in units of π (omega=1 → period=2 s)
     * @param phi   phase offset in radians (default 0)
     */
    record Sin(double A, double omega, double phi) implements AnimationTerm {
        /** Convenience constructor with phi = 0. */
        public Sin(double A, double omega) {
            this(A, omega, 0.0);
        }

        @Override
        public double evaluate(double t) {
            return A * Math.sin(omega * Math.PI * t + phi);
        }
    }

    /**
     * Cosine oscillation: {@code A * cos(omega * pi * t + phi)}.
     *
     * @param A     amplitude (peak deviation from zero)
     * @param omega angular frequency in units of π (omega=1 → period=2 s)
     * @param phi   phase offset in radians (default 0)
     */
    record Cos(double A, double omega, double phi) implements AnimationTerm {
        /** Convenience constructor with phi = 0. */
        public Cos(double A, double omega) {
            this(A, omega, 0.0);
        }

        @Override
        public double evaluate(double t) {
            return A * Math.cos(omega * Math.PI * t + phi);
        }
    }

    /**
     * Constant-rate linear progression: {@code speed * t}.
     *
     * <p>For rotation axes this produces a steady spin; for position axes
     * it produces a constant-velocity drift.</p>
     *
     * @param speed rate of change per second
     */
    record Linear(double speed) implements AnimationTerm {
        @Override
        public double evaluate(double t) {
            return speed * t;
        }
    }
}
