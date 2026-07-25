package network.azusake.halo.animation;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-segment transition animation with easing curves.
 *
 * <p>A transition consists of one or more {@link TransitionSegment}s, each
 * specifying a default duration, easing curve, and per-property from/to values.
 * Each property can optionally override the segment's duration and easing,
 * allowing different axes to animate at different speeds.</p>
 *
 * <p>Properties with per-property duration overrides have their own independent
 * timeline — they may still be in an earlier segment while other properties
 * have moved on to a later one.</p>
 */
public record TransitionAnimation(List<TransitionSegment> segments) {

    // ------------------------------------------------------------------
    // Property & Segment records
    // ------------------------------------------------------------------

    /**
     * A from/to pair for a single animated property.
     *
     * @param from            starting values (required for the first segment; may be
     *                        {@code null} for subsequent segments which inherit the
     *                        previous segment's end value)
     * @param to              ending values ({@code null} = this property is not animated
     *                        in this segment and stays at its inherited value)
     * @param propertyDuration optional per-property duration override (seconds);
     *                        {@code null} = use the segment's duration
     * @param propertyEasing  optional per-property easing override;
     *                        {@code null} = use the segment's easing
     */
    public record TransitionProperty(float[] from, float[] to,
                                     Double propertyDuration,
                                     EasingType propertyEasing) {

        /** Convenience constructor without per-property overrides. */
        public TransitionProperty(float[] from, float[] to) {
            this(from, to, null, null);
        }
    }

    /**
     * A single segment of a transition animation.
     *
     * @param duration default duration of this segment in seconds (must be > 0);
     *                 individual properties may override this
     * @param easing   default easing curve for this segment;
     *                 individual properties may override this
     * @param offset   optional offset property (3-component: x, y, z)
     * @param scale    optional scale property (3-component: x, y, z)
     * @param opacity  optional opacity property (1-component, stored as float[1])
     */
    public record TransitionSegment(
        double duration,
        EasingType easing,
        TransitionProperty offset,
        TransitionProperty scale,
        TransitionProperty opacity
    ) {}

    /**
     * The result of evaluating a {@link TransitionAnimation} at a specific time.
     *
     * @param offset offset applied to the halo (default {@link Vec3d#ZERO})
     * @param scale  scale factors applied to the halo (default {1, 1, 1})
     * @param opacity opacity multiplier applied to the halo (default 1.0)
     */
    public record TransitionResult(Vec3d offset, float[] scale, float opacity) {

        /** Default result used when no animation is active or evaluation is at t=0 before start. */
        public static final TransitionResult DEFAULT = new TransitionResult(
            Vec3d.ZERO, new float[]{1f, 1f, 1f}, 1.0f
        );
    }

    // ------------------------------------------------------------------
    // Static defaults used when a property's from/to is null
    // ------------------------------------------------------------------

    private static final float[] DEFAULT_OFFSET = new float[]{0f, 0f, 0f};
    private static final float[] DEFAULT_SCALE  = new float[]{1f, 1f, 1f};
    private static final float   DEFAULT_OPACITY = 1.0f;

    // ------------------------------------------------------------------
    // Total duration
    // ------------------------------------------------------------------

    /**
     * Effective total duration — the maximum across all property timelines.
     * Properties with per-property duration overrides may extend beyond
     * the sum of segment durations.
     */
    public double totalDuration() {
        return totalDurationFor(segments);
    }

    // ------------------------------------------------------------------
    // Evaluate
    // ------------------------------------------------------------------

    /**
     * Evaluate the animation at the given elapsed time.
     * Each property (offset, scale, opacity) is evaluated independently
     * using its own timeline (respecting per-property duration/easing overrides).
     *
     * <p>Transition values are multiplicative/additive modifiers applied on top
     * of the group's static transform.  Defaults are identity values:
     * offset=[0,0,0], scale=[1,1,1], opacity=1.0 — meaning "no change".</p>
     *
     * @param elapsed  time in seconds since the transition started
     * @param reversed if {@code true}, play the segments in reverse order
     *                 with from/to swapped (used for shutdown animations
     *                 that reverse a startup)
     * @return the interpolated result at the given time
     */
    public TransitionResult evaluate(double elapsed, boolean reversed) {
        if (segments.isEmpty()) {
            return TransitionResult.DEFAULT;
        }

        List<TransitionSegment> effective = reversed ? buildReversedSegments() : segments;
        double total = totalDurationFor(effective);

        if (elapsed <= 0) {
            return resolveFinalOrFirst(effective, true);
        }
        if (elapsed >= total) {
            return resolveFinalOrFirst(effective, false);
        }

        // Per-property evaluation with independent timelines
        float[] offset = evaluateProperty(effective, elapsed, DEFAULT_OFFSET, seg -> seg.offset());
        float[] scale  = evaluateProperty(effective, elapsed, DEFAULT_SCALE,  seg -> seg.scale());
        float   opacity = evaluatePropertyScalar(effective, elapsed, DEFAULT_OPACITY, seg -> seg.opacity());

        return new TransitionResult(
            new Vec3d(offset[0], offset[1], offset[2]),
            scale,
            opacity
        );
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Build a reversed copy of the segments: reverse the list order and swap
     * from/to on each property.  Nulls are filled with static defaults.
     * Per-property duration/easing overrides are preserved.
     */
    private List<TransitionSegment> buildReversedSegments() {
        List<TransitionSegment> reversed = new ArrayList<>(segments.size());
        for (int i = segments.size() - 1; i >= 0; i--) {
            TransitionSegment seg = segments.get(i);
            reversed.add(new TransitionSegment(
                seg.duration(),
                seg.easing(),
                reverseProperty(seg.offset(), DEFAULT_OFFSET),
                reverseProperty(seg.scale(), DEFAULT_SCALE),
                reverseProperty(seg.opacity(), new float[]{DEFAULT_OPACITY})
            ));
        }
        return reversed;
    }

    /**
     * Swap from/to on a property, preserving per-property duration/easing overrides.
     */
    private static TransitionProperty reverseProperty(TransitionProperty prop, float[] defaultValue) {
        if (prop == null) {
            return null;
        }
        float[] newFrom = (prop.to() != null) ? prop.to() : defaultValue;
        float[] newTo = (prop.from() != null) ? prop.from() : defaultValue;
        return new TransitionProperty(newFrom, newTo, prop.propertyDuration(), prop.propertyEasing());
    }

    /**
     * Effective total duration — the maximum end time across all property queues.
     * Each property's end time is computed using the queue model:
     * {@code propStart = max(segStart, prevPropEnd)}.
     */
    private static double totalDurationFor(List<TransitionSegment> segs) {
        double maxEnd = 0;
        // Simulate the queue for each property type
        maxEnd = Math.max(maxEnd, computePropertyEnd(segs, seg -> seg.offset()));
        maxEnd = Math.max(maxEnd, computePropertyEnd(segs, seg -> seg.scale()));
        maxEnd = Math.max(maxEnd, computePropertyEnd(segs, seg -> seg.opacity()));
        // Also account for segments with no properties (just segment duration)
        double segTotal = 0;
        for (TransitionSegment seg : segs) segTotal += seg.duration();
        return Math.max(maxEnd, segTotal);
    }

    /**
     * Compute the end time of the last animation in a property's queue.
     */
    private static double computePropertyEnd(List<TransitionSegment> segs, PropertyExtractor extractor) {
        double segStart = 0;
        double prevPropEnd = 0;
        for (TransitionSegment seg : segs) {
            TransitionProperty prop = extractor.extract(seg);
            if (prop != null) {
                double propDur = propDuration(prop, seg.duration());
                double propStart = Math.max(segStart, prevPropEnd);
                prevPropEnd = propStart + propDur;
            }
            segStart += seg.duration();
        }
        return prevPropEnd;
    }

    // ------------------------------------------------------------------
    // Per-property evaluation
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface PropertyExtractor {
        TransitionProperty extract(TransitionSegment seg);
    }

    /**
     * Evaluate a multi-component property (offset or scale) across segments,
     * using per-property duration/easing when available.
     *
     * <p>Each property has its own independent timeline managed as a queue:
     * each property animation starts at {@code max(segStart, prevPropEnd)},
     * where {@code segStart} is the segment's base start time and
     * {@code prevPropEnd} is when the same property's previous animation ended.
     * This ensures no overlap — if a prior property animation extends beyond
     * the current segment's start, the current one is pushed back accordingly.</p>
     *
     * <p>Segments without this property are skipped (property stays at its
     * inherited value).</p>
     */
    private float[] evaluateProperty(List<TransitionSegment> segs, double elapsed,
                                      float[] defaultVal, PropertyExtractor extractor) {
        double segStart = 0;      // segment's base start time (cumulative segment durations)
        double prevPropEnd = 0;   // end time of the previous property animation in this queue

        for (TransitionSegment seg : segs) {
            TransitionProperty prop = extractor.extract(seg);
            if (prop == null) {
                // This segment has no animation for this property — skip it
                segStart += seg.duration();
                continue;
            }

            double propDur = propDuration(prop, seg.duration());
            // Queue: start at max of segment start or previous property end
            double propStart = Math.max(segStart, prevPropEnd);
            double propEnd = propStart + propDur;

            if (elapsed < propEnd) {
                // Active: interpolate within this property's time range
                double localT = Math.max(0.0, Math.min(1.0, (elapsed - propStart) / propDur));
                EasingType easing = propEasing(prop, seg.easing());
                double easedT = easing.evaluate(localT);
                return interpolateProperty(prop, defaultVal, easedT);
            }

            prevPropEnd = propEnd;
            segStart += seg.duration();
        }
        // Past all segments: return the last defined value (not default),
        // so all properties hold their final values until totalDuration expires
        return resolvePropertyFinal(segs, defaultVal, extractor);
    }

    /**
     * Evaluate a scalar property (opacity) across segments.
     */
    private float evaluatePropertyScalar(List<TransitionSegment> segs, double elapsed,
                                          float defaultVal, PropertyExtractor extractor) {
        float[] result = evaluateProperty(segs, elapsed, new float[]{defaultVal}, extractor);
        return result[0];
    }

    private static double propDuration(TransitionProperty prop, double segDuration) {
        return (prop != null && prop.propertyDuration() != null) ? prop.propertyDuration() : segDuration;
    }

    private static EasingType propEasing(TransitionProperty prop, EasingType segEasing) {
        return (prop != null && prop.propertyEasing() != null) ? prop.propertyEasing() : segEasing;
    }

    /**
     * Interpolate a property at the given eased progress.
     * Missing from/to are filled with defaultVal so the animation
     * always interpolates between two concrete values.
     */
    private static float[] interpolateProperty(TransitionProperty prop, float[] defaultVal, double easedT) {
        float[] from = (prop.from() != null) ? prop.from() : defaultVal;
        float[] to   = (prop.to()   != null) ? prop.to()   : defaultVal;
        int len = Math.max(from.length, to.length);
        float[] result = new float[len];
        for (int i = 0; i < len; i++) {
            float f = i < from.length ? from[i] : defaultVal[i];
            float t = i < to.length   ? to[i]   : defaultVal[i];
            result[i] = lerp(f, t, (float) easedT);
        }
        return result;
    }

    /**
     * Get the final value of a property after all segments complete.
     * Returns the last segment's {@code to} value for this property,
     * or the last {@code from} if no {@code to} exists.
     * Falls back to {@code defaultVal} only if the property was never defined.
     */
    private static float[] resolvePropertyFinal(List<TransitionSegment> segs, float[] defaultVal,
                                                  PropertyExtractor extractor) {
        float[] last = null;
        for (TransitionSegment seg : segs) {
            TransitionProperty prop = extractor.extract(seg);
            if (prop == null) continue;
            if (prop.to() != null) last = prop.to();
            else if (prop.from() != null) last = prop.from();
        }
        return (last != null) ? last : defaultVal;
    }

    /**
     * Return the final or first values for all properties, used at boundary times.
     * All properties are resolved together — they enter steady-state simultaneously.
     */
    private TransitionResult resolveFinalOrFirst(List<TransitionSegment> segs, boolean isFirst) {
        if (segs.isEmpty()) {
            return TransitionResult.DEFAULT;
        }

        float[] offset = isFirst
            ? resolvePropertyFirst(segs, DEFAULT_OFFSET, seg -> seg.offset())
            : resolvePropertyFinal(segs, DEFAULT_OFFSET, seg -> seg.offset());
        float[] scale = isFirst
            ? resolvePropertyFirst(segs, DEFAULT_SCALE, seg -> seg.scale())
            : resolvePropertyFinal(segs, DEFAULT_SCALE, seg -> seg.scale());
        float opacity;
        if (isFirst) {
            float[] op = resolvePropertyFirst(segs, new float[]{DEFAULT_OPACITY}, seg -> seg.opacity());
            opacity = op[0];
        } else {
            float[] op = resolvePropertyFinal(segs, new float[]{DEFAULT_OPACITY}, seg -> seg.opacity());
            opacity = op[0];
        }

        return new TransitionResult(
            new Vec3d(offset[0], offset[1], offset[2]),
            scale,
            opacity
        );
    }

    /**
     * Get the first defined value of a property across segments.
     * Skips null properties. Falls back to default if never defined.
     */
    private static float[] resolvePropertyFirst(List<TransitionSegment> segs, float[] defaultVal,
                                                  PropertyExtractor extractor) {
        for (TransitionSegment seg : segs) {
            TransitionProperty prop = extractor.extract(seg);
            if (prop != null) {
                if (prop.from() != null) return prop.from();
                if (prop.to() != null) return prop.to();
            }
        }
        return defaultVal;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
