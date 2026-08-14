package network.azusake.halo.animation;

/**
 * A single element in a {@link TransitionQueue}, representing one animation
 * segment for a specific property of a specific group.
 *
 * @param startTime  absolute start time in seconds (from transition start)
 * @param endTime    absolute end time in seconds
 * @param duration   endTime - startTime (cached for convenience)
 * @param startVal     property value at startTime (null = hold previous)
 * @param endVal       property value at endTime (null = hold startVal)
 * @param easing       easing curve applied within this element
 * @param fromExplicit whether {@code startVal} was authored in JSON (false =
 *                     derived/gap value that per-instance endpoint patching may override)
 * @param toExplicit   whether {@code endVal} was authored in JSON (false =
 *                     derived/gap value that per-instance endpoint patching may override)
 */
public record TransitionQueueElement(
    double startTime,
    double endTime,
    double duration,
    float[] startVal,
    float[] endVal,
    EasingType easing,
    boolean fromExplicit,
    boolean toExplicit
) {
    /**
     * Convenience constructor deriving the explicit flags from whether the
     * corresponding value is non-null (parse-time segments and gap elements).
     */
    public TransitionQueueElement(
        double startTime, double endTime, double duration,
        float[] startVal, float[] endVal, EasingType easing
    ) {
        this(startTime, endTime, duration, startVal, endVal, easing,
            startVal != null, endVal != null);
    }

    /** Copy with a new start value, keeping all flags. */
    public TransitionQueueElement withStartVal(float[] value) {
        return new TransitionQueueElement(startTime, endTime, duration, value, endVal, easing,
            fromExplicit, toExplicit);
    }

    /** Copy with a new end value, keeping all flags. */
    public TransitionQueueElement withEndVal(float[] value) {
        return new TransitionQueueElement(startTime, endTime, duration, startVal, value, easing,
            fromExplicit, toExplicit);
    }

    /** Copy with new start and end values, keeping all flags. */
    public TransitionQueueElement withValues(float[] newStartVal, float[] newEndVal) {
        return new TransitionQueueElement(startTime, endTime, duration, newStartVal, newEndVal, easing,
            fromExplicit, toExplicit);
    }

    /** Whether this element holds a constant value (startVal == endVal). */
    public boolean isHold() {
        if (startVal == null || endVal == null) {
            return startVal == null && endVal == null;
        }
        if (startVal.length != endVal.length) return false;
        for (int i = 0; i < startVal.length; i++) {
            if (Math.abs(startVal[i] - endVal[i]) > 0.001f) return false;
        }
        return true;
    }

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
        return new TransitionQueueElement(newStart, newEnd, duration, endVal, startVal, easing,
            toExplicit, fromExplicit);
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
