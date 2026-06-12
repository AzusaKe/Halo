package com.example.halo.animation;

import java.util.Collections;
import java.util.List;

/**
 * Container for all position and rotation animation curves of a halo definition.
 * Each curve is evaluated independently; the results are combined additively.
 *
 * @param positionCurves list of per-axis position curves
 * @param rotationCurves list of per-axis rotation curves
 */
public record HaloAnimation(
    List<PositionCurve> positionCurves,
    List<RotationCurve> rotationCurves
) {
    /**
     * Canonical empty animation (no curves).
     */
    public static final HaloAnimation EMPTY = new HaloAnimation(
        Collections.emptyList(), Collections.emptyList());
}
