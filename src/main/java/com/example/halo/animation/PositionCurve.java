package com.example.halo.animation;

/**
 * Associates an {@link AnimationCurve} with a positional axis.
 *
 * @param axis  the axis this curve drives (X, Y, or Z)
 * @param curve the scalar animation curve
 */
public record PositionCurve(PositionAxis axis, AnimationCurve curve) {

    public enum PositionAxis {
        X, Y, Z
    }
}
