package network.azusake.halo.animation;

/**
 * A curve that always returns the same value, regardless of time.
 *
 * @param value the constant output
 */
public record ConstantCurve(double value) implements AnimationCurve {

    @Override
    public double evaluate(double time) {
        return value;
    }
}
