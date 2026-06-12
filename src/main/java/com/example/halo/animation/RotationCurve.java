package com.example.halo.animation;

/**
 * Associates an {@link AnimationCurve} with a rotational axis.
 *
 * @param axis  the axis this curve drives (YAW, PITCH, or ROLL)
 * @param curve the scalar animation curve
 */
public record RotationCurve(RotationAxis axis, AnimationCurve curve) {

    public enum RotationAxis {
        YAW, PITCH, ROLL
    }
}
