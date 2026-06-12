package com.example.halo.animation;

/**
 * A linear curve: {@code value(t) = start + speed * t}.
 *
 * @param start initial value at t = 0
 * @param speed rate of change per second
 */
public record LinearCurve(double start, double speed) implements AnimationCurve {

    @Override
    public double evaluate(double time) {
        return start + speed * time;
    }
}
