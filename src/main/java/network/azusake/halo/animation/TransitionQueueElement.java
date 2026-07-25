package network.azusake.halo.animation;

/**
 * A single element in a {@link TransitionQueue}, representing one animation
 * segment for a specific property of a specific group.
 *
 * @param startTime  absolute start time in seconds (from transition start)
 * @param endTime    absolute end time in seconds
 * @param duration   endTime - startTime (cached for convenience)
 * @param startVal   property value at startTime (null = hold previous)
 * @param endVal     property value at endTime (null = hold startVal)
 * @param easing     easing curve applied within this element
 */
public record TransitionQueueElement(
    double startTime,
    double endTime,
    double duration,
    float[] startVal,
    float[] endVal,
    EasingType easing
) {
    /**
     * Evaluate this element at the given absolute time.
     *
     * @param time absolute time in seconds
     * @return interpolated value, clamped to [startTime, endTime]
     */
    public float[] evaluate(double time) {
        if (startVal == null || endVal == null) {
            // Should not happen after build phase — return startVal as fallback
            return startVal != null ? startVal : endVal;
        }
        double progress = (time - startTime) / duration;
        progress = Math.max(0.0, Math.min(1.0, progress));
        double eased = easing.evaluate(progress);
        return lerp(startVal, endVal, (float) eased);
    }

    /**
     * Create a reversed copy: swap startVal/endVal and remap times.
     *
     * @param totalDuration the total queue duration (used to remap times)
     * @return a new element with swapped values and reversed time mapping
     */
    public TransitionQueueElement reversed(double totalDuration) {
        double newStart = totalDuration - endTime;
        double newEnd = totalDuration - startTime;
        return new TransitionQueueElement(newStart, newEnd, duration, endVal, startVal, easing);
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        int len = Math.max(a.length, b.length);
        float[] result = new float[len];
        for (int i = 0; i < len; i++) {
            float av = i < a.length ? a[i] : 0;
            float bv = i < b.length ? b[i] : 0;
            result[i] = av + (bv - av) * t;
        }
        return result;
    }
}
