package network.azusake.halo.animation;

/**
 * Sealed hierarchy for scalar animation curves {@code y = f(t)}.
 * Each subtype defines a different interpolation strategy.
 *
 * <p>All implementations must live in this package because the project
 * does not use a named Java module (JPMS), and sealed classes in unnamed
 * modules cannot span packages.
 *
 * @see ConstantCurve
 * @see LinearCurve
 * @see OscillateCurve
 */
public sealed interface AnimationCurve
    permits ConstantCurve, LinearCurve, OscillateCurve {

    /**
     * Evaluate the curve at the given time.
     *
     * @param time elapsed time in seconds
     * @return the curve value at {@code time}
     */
    double evaluate(double time);
}
