package com.example.halo.animation;

/**
 * A sinusoidal oscillation: {@code value(t) = amplitude * sin(frequency * t + phase)}.
 *
 * @param amplitude peak deviation from zero
 * @param frequency angular frequency in radians per second
 * @param phase     initial phase offset in radians
 */
public record OscillateCurve(double amplitude, double frequency, double phase) implements AnimationCurve {

    @Override
    public double evaluate(double time) {
        return amplitude * Math.sin(frequency * time + phase);
    }
}
