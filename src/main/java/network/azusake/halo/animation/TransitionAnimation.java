package network.azusake.halo.animation;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-segment transition animation with easing curves and queue-based
 * per-property timelines.
 *
 * <h3>Queue Model</h3>
 * <p>Each property (offset, scale, alpha) has its own independent timeline.
 * Within a segment, a property starts at {@code max(segStart, prevPropEnd)},
 * ensuring no overlap.  If a property is null in a segment but defined in a
 * later segment, the gap is filled with a hold at the {@code from} value of
 * the next active segment.</p>
 *
 * <h3>Reversal</h3>
 * <p>When played in reverse (shutdown), the queue is reversed: the last group
 * to start animating becomes the first to start in reverse.  Idle gaps in the
 * original become active hold periods in the reversed timeline, and vice versa.
 * This produces symmetric staggered fade-out matching the original staggered
 * fade-in.</p>
 */
public record TransitionAnimation(List<TransitionSegment> segments) {

    // ------------------------------------------------------------------
    // Property & Segment records
    // ------------------------------------------------------------------

    /**
     * A from/to pair for a single animated property.
     *
     * @param from            starting values; for the first active segment of a
     *                        property this is required.  For subsequent segments it
     *                        may be {@code null} (inherits previous end value).
     * @param to              ending values ({@code null} = use default for this
     *                        property — see {@code defaultVal} in evaluation)
     * @param propertyDuration optional per-property duration override (seconds);
     *                        {@code null} = use the segment's duration
     * @param propertyEasing  optional per-property easing override;
     *                        {@code null} = use the segment's easing
     */
    public record TransitionProperty(float[] from, float[] to,
                                     Double propertyDuration,
                                     EasingType propertyEasing,
                                     float[] degrees) {

        /** Convenience constructor without per-property overrides. */
        public TransitionProperty(float[] from, float[] to) {
            this(from, to, null, null, null);
        }

        /** Convenience constructor without per-property overrides or degrees. */
        public TransitionProperty(float[] from, float[] to,
                                  Double propertyDuration, EasingType propertyEasing) {
            this(from, to, propertyDuration, propertyEasing, null);
        }

        /** Copy with a new degrees vector (or {@code null} to clear it). */
        public TransitionProperty withDegrees(float[] newDegrees) {
            return new TransitionProperty(from, to, propertyDuration, propertyEasing, newDegrees);
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
     * @param alpha    optional alpha property (1-component, stored as float[1])
     */
    public record TransitionSegment(
        double duration,
        EasingType easing,
        TransitionProperty offset,
        TransitionProperty scale,
        TransitionProperty alpha,
        TransitionProperty rotation
    ) {
        /** Convenience constructor without a rotation property. */
        public TransitionSegment(
            double duration, EasingType easing,
            TransitionProperty offset, TransitionProperty scale, TransitionProperty alpha
        ) {
            this(duration, easing, offset, scale, alpha, null);
        }
    }

    /**
     * The result of evaluating a {@link TransitionAnimation} at a specific time.
     */
    public record TransitionResult(Vec3 offset, float[] scale, float alpha,
                                   float[] rotationDegrees) {

        /** Default result (identity: no change). */
        public static final TransitionResult DEFAULT = new TransitionResult(
            Vec3.ZERO, new float[]{1f, 1f, 1f}, 1.0f, new float[]{0f, 0f, 0f}
        );
    }

    // ------------------------------------------------------------------
    // Static defaults
    // ------------------------------------------------------------------

    private static final float[] DEFAULT_OFFSET   = new float[]{0f, 0f, 0f};
    private static final float[] DEFAULT_SCALE    = new float[]{1f, 1f, 1f};
    private static final float[] DEFAULT_ROTATION = new float[]{0f, 0f, 0f};
    private static final float   DEFAULT_ALPHA    = 1.0f;

    // ------------------------------------------------------------------
    // Total duration
    // ------------------------------------------------------------------

    /**
     * Effective total duration — the maximum end time across all property queues.
     */
    public double totalDuration() {
        return totalDurationFor(segments);
    }

    // ------------------------------------------------------------------
    // Evaluate
    // ------------------------------------------------------------------

    /**
     * Evaluate the animation at the given elapsed time.
     *
     * @param elapsed  time in seconds since the transition started
     * @param reversed if {@code true}, reverse the queue order for shutdown
     * @return the interpolated result at the given time
     */
    public TransitionResult evaluate(double elapsed, boolean reversed) {
        if (segments.isEmpty()) {
            return TransitionResult.DEFAULT;
        }

        // Pre-compute reversed queue info if needed
        List<QueueEntry> revQueue = reversed ? buildReversedQueue() : null;
        double total = totalDurationFor(segments);
        double reversedEnd = reversed ? lastActiveEndTime(revQueue) : total;

        if (elapsed <= 0) {
            return reversed ? resolveRevFirst(revQueue) : resolveFinalOrFirst(segments, true);
        }
        if (elapsed >= reversedEnd) {
            // Evaluate at the end of the last active entry (before trailing idle)
            double evalTime = Math.max(0, reversedEnd - 1e-6);
            if (reversed) {
                float[] off = evaluateRevProperty(revQueue, evalTime, DEFAULT_OFFSET, e -> e.propOffset());
                float[] scl = evaluateRevProperty(revQueue, evalTime, DEFAULT_SCALE, e -> e.propScale());
                float[] rot = evaluateRevProperty(revQueue, evalTime, DEFAULT_ROTATION, e -> e.propRotation());
                float a = evaluateRevScalar(revQueue, evalTime, DEFAULT_ALPHA, e -> e.propAlpha());
                return new TransitionResult(new Vec3(off[0], off[1], off[2]), scl, a, rot);
            }
            return resolveFinalOrFirst(segments, false);
        }

        float[] offset = reversed
            ? evaluateRevProperty(revQueue, elapsed, DEFAULT_OFFSET, e -> e.propOffset())
            : evaluateProperty(segments, elapsed, DEFAULT_OFFSET, seg -> seg.offset());
        float[] scale = reversed
            ? evaluateRevProperty(revQueue, elapsed, DEFAULT_SCALE, e -> e.propScale())
            : evaluateProperty(segments, elapsed, DEFAULT_SCALE, seg -> seg.scale());
        float[] rotation = reversed
            ? evaluateRevProperty(revQueue, elapsed, DEFAULT_ROTATION, e -> e.propRotation())
            : evaluateProperty(segments, elapsed, DEFAULT_ROTATION, seg -> seg.rotation());
        float alpha = reversed
            ? evaluateRevScalar(revQueue, elapsed, DEFAULT_ALPHA, e -> e.propAlpha())
            : evaluatePropertyScalar(segments, elapsed, DEFAULT_ALPHA, seg -> seg.alpha());

        return new TransitionResult(
            new Vec3(offset[0], offset[1], offset[2]),
            scale, alpha, rotation
        );
    }

    // ==================================================================
    // Forward evaluation (original segments, queue model)
    // ==================================================================

    @FunctionalInterface
    private interface PropertyExtractor {
        TransitionProperty extract(TransitionSegment seg);
    }

    private float[] evaluateProperty(List<TransitionSegment> segs, double elapsed,
                                      float[] defaultVal, PropertyExtractor extractor) {
        double segStart = 0;
        double prevPropEnd = 0;

        for (TransitionSegment seg : segs) {
            TransitionProperty prop = extractor.extract(seg);

            if (prop == null) {
                // Gap: property not configured in this segment.
                // If there's a gap between prevPropEnd and segStart+segDur,
                // hold at the next active segment's from value (or default).
                double gapEnd = segStart + seg.duration();
                if (prevPropEnd < gapEnd && elapsed >= prevPropEnd && elapsed < gapEnd) {
                    float[] holdVal = findNextFrom(segs, segs.indexOf(seg) + 1, defaultVal, extractor);
                    return holdVal;
                }
                segStart += seg.duration();
                continue;
            }

            double propDur = propDuration(prop, seg.duration());
            double propStart = Math.max(segStart, prevPropEnd);
            double propEnd = propStart + propDur;

            if (elapsed < propEnd) {
                double localT = Math.max(0.0, Math.min(1.0, (elapsed - propStart) / propDur));
                EasingType easing = propEasing(prop, seg.easing());
                double easedT = easing.evaluate(localT);
                return interpolateProperty(prop, defaultVal, easedT);
            }

            prevPropEnd = propEnd;
            segStart += seg.duration();
        }

        return resolvePropertyFinal(segs, defaultVal, extractor);
    }

    private float evaluatePropertyScalar(List<TransitionSegment> segs, double elapsed,
                                          float defaultVal, PropertyExtractor extractor) {
        float[] result = evaluateProperty(segs, elapsed, new float[]{defaultVal}, extractor);
        return result[0];
    }

    /**
     * Find the from value of the next active segment for a property.
     * Used to hold during gaps.
     */
    private static float[] findNextFrom(List<TransitionSegment> segs, int startIndex,
                                         float[] defaultVal, PropertyExtractor extractor) {
        for (int i = startIndex; i < segs.size(); i++) {
            TransitionProperty prop = extractor.extract(segs.get(i));
            if (prop != null && prop.from() != null) return prop.from();
            if (prop != null && prop.to() != null) return prop.to();
        }
        // No more segments with this property — use the final value
        return resolvePropertyFinal(segs, defaultVal, extractor);
    }

    // ==================================================================
    // Reversed evaluation (queue reversal for shutdown)
    // ==================================================================

    /**
     * Entry in the reversed queue: a time-sorted list of when each property
     * is active in the reversed timeline.
     */
    private record QueueEntry(double startTime, double duration, EasingType easing,
                               TransitionProperty propOffset,
                               TransitionProperty propScale,
                               TransitionProperty propAlpha,
                               TransitionProperty propRotation) {}

    /**
     * Build the reversed queue: for each property, compute the original queue
     * timeline, then reverse it.  The result is a merged, time-sorted list of
     * entries covering the full reversed duration.
     */
    private List<QueueEntry> buildReversedQueue() {
        // Compute per-property queue timelines
        List<PropQueueEntry> offQ = computePropQueue(seg -> seg.offset(), DEFAULT_OFFSET);
        List<PropQueueEntry> sclQ = computePropQueue(seg -> seg.scale(), DEFAULT_SCALE);
        List<PropQueueEntry> rotQ = computePropQueue(seg -> seg.rotation(), DEFAULT_ROTATION);
        List<PropQueueEntry> aQ   = computePropQueue(seg -> seg.alpha(), new float[]{DEFAULT_ALPHA});

        double total = totalDurationFor(segments);

        // Reverse each queue
        List<PropQueueEntry> offR = reversePropQueue(offQ, total, DEFAULT_OFFSET);
        List<PropQueueEntry> sclR = reversePropQueue(sclQ, total, DEFAULT_SCALE);
        List<PropQueueEntry> rotR = reversePropQueue(rotQ, total, DEFAULT_ROTATION);
        List<PropQueueEntry> aR   = reversePropQueue(aQ, total, new float[]{DEFAULT_ALPHA});

        // Merge into unified entries
        return mergeQueues(offR, sclR, aR, rotR);
    }

    private record PropQueueEntry(double startTime, double duration, EasingType easing,
                                   float[] from, float[] to, float[] degrees) {}

    /**
     * Compute the forward queue for a single property.
     */
    private List<PropQueueEntry> computePropQueue(PropertyExtractor extractor, float[] defaultVal) {
        List<PropQueueEntry> queue = new ArrayList<>();
        double segStart = 0;
        double prevPropEnd = 0;

        for (TransitionSegment seg : segments) {
            TransitionProperty prop = extractor.extract(seg);
            if (prop != null) {
                double propDur = propDuration(prop, seg.duration());
                double propStart = Math.max(segStart, prevPropEnd);
                float[] from = (prop.from() != null) ? prop.from() : defaultVal;
                // When to is null, use defaultVal (steady-state), not from
                float[] to = (prop.to() != null) ? prop.to() : defaultVal;
                // degrees forces a minimum signed travel — the effective end
                // replaces `to` before the queue is built
                if (prop.degrees() != null) {
                    to = RotationTravel.effectiveEnds(from, to, prop.degrees());
                }
                EasingType easing = propEasing(prop, seg.easing());
                queue.add(new PropQueueEntry(propStart, propDur, easing, from, to, prop.degrees()));
                prevPropEnd = propStart + propDur;
            }
            segStart += seg.duration();
        }
        return queue;
    }

    /**
     * Reverse a property queue preserving the stagger pattern.
     *
     * <p>Reversed order: last forward entry → first reversed (no idle).
     * Each subsequent reversed entry has an idle gap equal to the difference
     * between the current and previous forward entry's start time.</p>
     *
     * <p>Forward: pointer[start=0], ring_inner[start=1], ring_outer[start=2]
     * Reversed: ring_outer[idle=0, 0-3], ring_inner[idle=1, 1-4], pointer[idle=2, 2-5]</p>
     */
    private List<PropQueueEntry> reversePropQueue(List<PropQueueEntry> forward, double total,
                                                   float[] defaultVal) {
        if (forward.isEmpty()) {
            return List.of();
        }

        // Build reversed entries with staggered idle gaps
        // Idle gap = difference in forward start times between this and previous reversed entry
        List<PropQueueEntry> reversed = new ArrayList<>();
        double prevFwdStart = forward.get(forward.size() - 1).startTime; // last forward's start

        for (int i = forward.size() - 1; i >= 0; i--) {
            PropQueueEntry e = forward.get(i);
            double idleGap = (i == forward.size() - 1) ? 0 : e.startTime - prevFwdStart;
            prevFwdStart = e.startTime;
            reversed.add(new PropQueueEntry(idleGap, e.duration, e.easing, e.to, e.from,
                e.degrees != null ? RotationTravel.negated(e.degrees) : null));
        }

        // Assign absolute start times based on cumulative idle
        List<PropQueueEntry> result = new ArrayList<>();
        double cursor = 0;

        for (PropQueueEntry rev : reversed) {
            double idle = rev.startTime; // startTime temporarily holds the idle gap
            if (idle > 0.0001) {
                result.add(new PropQueueEntry(cursor, idle,
                    EasingType.LINEAR, defaultVal, defaultVal, null));
            }
            double propStart = cursor + idle;
            result.add(new PropQueueEntry(propStart, rev.duration, rev.easing, rev.from, rev.to, rev.degrees));
            cursor = propStart + rev.duration;
        }

        if (cursor < total - 0.0001) {
            result.add(new PropQueueEntry(cursor, total - cursor,
                EasingType.LINEAR, defaultVal, defaultVal, null));
        }

        return result;
    }

    /**
     * Merge per-property reversed queues into unified QueueEntry list.
     */
    private List<QueueEntry> mergeQueues(List<PropQueueEntry> off, List<PropQueueEntry> scl,
                                          List<PropQueueEntry> a, List<PropQueueEntry> rot) {
        // Collect all unique time boundaries
        List<Double> boundaries = new ArrayList<>();
        for (PropQueueEntry e : off) { boundaries.add(e.startTime); boundaries.add(e.startTime + e.duration); }
        for (PropQueueEntry e : scl) { boundaries.add(e.startTime); boundaries.add(e.startTime + e.duration); }
        for (PropQueueEntry e : a)   { boundaries.add(e.startTime); boundaries.add(e.startTime + e.duration); }
        for (PropQueueEntry e : rot) { boundaries.add(e.startTime); boundaries.add(e.startTime + e.duration); }
        boundaries = boundaries.stream().sorted().distinct().toList();

        List<QueueEntry> result = new ArrayList<>();
        for (int i = 0; i < boundaries.size() - 1; i++) {
            double start = boundaries.get(i);
            double end = boundaries.get(i + 1);
            double dur = end - start;
            if (dur < 0.0001) continue;

            TransitionProperty oProp = findPropAt(off, start);
            TransitionProperty sProp = findPropAt(scl, start);
            TransitionProperty aProp = findPropAt(a, start);
            TransitionProperty rProp = findPropAt(rot, start);
            EasingType easing = EasingType.LINEAR;
            if (oProp != null) easing = oProp.propertyEasing() != null ? oProp.propertyEasing() : easing;
            if (sProp != null) easing = sProp.propertyEasing() != null ? sProp.propertyEasing() : easing;
            if (aProp != null) easing = aProp.propertyEasing() != null ? aProp.propertyEasing() : easing;
            if (rProp != null) easing = rProp.propertyEasing() != null ? rProp.propertyEasing() : easing;

            result.add(new QueueEntry(start, dur, easing, oProp, sProp, aProp, rProp));
        }
        return result;
    }

    private static TransitionProperty findPropAt(List<PropQueueEntry> queue, double time) {
        for (PropQueueEntry e : queue) {
            if (time >= e.startTime - 0.0001 && time < e.startTime + e.duration - 0.0001) {
                return new TransitionProperty(e.from, e.to, null, e.easing, e.degrees);
            }
        }
        return null;
    }

    private float[] evaluateRevProperty(List<QueueEntry> queue, double elapsed,
                                         float[] defaultVal, RevExtractor extractor) {
        for (QueueEntry entry : queue) {
            TransitionProperty prop = extractor.extract(entry);
            if (prop == null) continue;
            double entryEnd = entry.startTime() + entry.duration();
            if (elapsed < entryEnd) {
                double localT = Math.max(0.0, Math.min(1.0, (elapsed - entry.startTime()) / entry.duration()));
                EasingType easing = entry.easing() != null ? entry.easing() : EasingType.LINEAR;
                double easedT = easing.evaluate(localT);
                return interpolateProperty(prop, defaultVal, easedT);
            }
        }
        return defaultVal;
    }

    private float evaluateRevScalar(List<QueueEntry> queue, double elapsed,
                                     float defaultVal, RevExtractor extractor) {
        float[] result = evaluateRevProperty(queue, elapsed, new float[]{defaultVal}, extractor);
        return result[0];
    }

    @FunctionalInterface
    private interface RevExtractor {
        TransitionProperty extract(QueueEntry entry);
    }

    private TransitionResult resolveRevFirst(List<QueueEntry> queue) {
        if (queue.isEmpty()) return TransitionResult.DEFAULT;
        QueueEntry first = queue.get(0);
        float[] off = first.propOffset() != null ? (first.propOffset().from() != null ? first.propOffset().from() : DEFAULT_OFFSET) : DEFAULT_OFFSET;
        float[] scl = first.propScale() != null ? (first.propScale().from() != null ? first.propScale().from() : DEFAULT_SCALE) : DEFAULT_SCALE;
        float[] rot = first.propRotation() != null ? (first.propRotation().from() != null ? first.propRotation().from() : DEFAULT_ROTATION) : DEFAULT_ROTATION;
        float a = first.propAlpha() != null ? (first.propAlpha().from() != null ? first.propAlpha().from()[0] : DEFAULT_ALPHA) : DEFAULT_ALPHA;
        return new TransitionResult(new Vec3(off[0], off[1], off[2]), scl, a, rot);
    }

    /**
     * Find the end time of the last ACTIVE entry in the reversed queue.
     * Trailing idle/hold entries (from==to) are skipped.
     * This is the time when the reversed animation actually finishes
     * (all groups have faded out), not including the hold period.
     */
    private double lastActiveEndTime(List<QueueEntry> queue) {
        for (int i = queue.size() - 1; i >= 0; i--) {
            QueueEntry e = queue.get(i);
            if (!isIdleEntry(e)) {
                return e.startTime() + e.duration();
            }
        }
        return totalDurationFor(segments);
    }

    private static boolean isIdleEntry(QueueEntry e) {
        TransitionProperty prop = (e.propScale() != null) ? e.propScale()
            : (e.propOffset() != null) ? e.propOffset()
            : (e.propAlpha() != null) ? e.propAlpha()
            : e.propRotation();
        if (prop == null || prop.from() == null || prop.to() == null) return true;
        for (int i = 0; i < Math.max(prop.from().length, prop.to().length); i++) {
            float f = i < prop.from().length ? prop.from()[i] : 0;
            float t = i < prop.to().length ? prop.to()[i] : 0;
            if (Math.abs(f - t) > 0.001f) return false;
        }
        return true;
    }

    // ==================================================================
    // Shared helpers
    // ==================================================================

    private static double totalDurationFor(List<TransitionSegment> segs) {
        double maxEnd = 0;
        maxEnd = Math.max(maxEnd, computePropertyEnd(segs, seg -> seg.offset()));
        maxEnd = Math.max(maxEnd, computePropertyEnd(segs, seg -> seg.scale()));
        maxEnd = Math.max(maxEnd, computePropertyEnd(segs, seg -> seg.alpha()));
        maxEnd = Math.max(maxEnd, computePropertyEnd(segs, seg -> seg.rotation()));
        double segTotal = 0;
        for (TransitionSegment seg : segs) segTotal += seg.duration();
        return Math.max(maxEnd, segTotal);
    }

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

    private static double propDuration(TransitionProperty prop, double segDuration) {
        return (prop != null && prop.propertyDuration() != null) ? prop.propertyDuration() : segDuration;
    }

    private static EasingType propEasing(TransitionProperty prop, EasingType segEasing) {
        return (prop != null && prop.propertyEasing() != null) ? prop.propertyEasing() : segEasing;
    }

    private static float[] interpolateProperty(TransitionProperty prop, float[] defaultVal, double easedT) {
        float[] from = (prop.from() != null) ? prop.from() : defaultVal;
        float[] to   = (prop.to()   != null) ? prop.to()   : defaultVal;
        if (prop.degrees() != null) {
            to = RotationTravel.effectiveEnds(from, to, prop.degrees());
        }
        int len = Math.max(from.length, to.length);
        float[] result = new float[len];
        for (int i = 0; i < len; i++) {
            float f = i < from.length ? from[i] : defaultVal[i];
            float t = i < to.length   ? to[i]   : defaultVal[i];
            result[i] = lerp(f, t, (float) easedT);
        }
        return result;
    }

    private static float[] resolvePropertyFinal(List<TransitionSegment> segs, float[] defaultVal,
                                                  PropertyExtractor extractor) {
        float[] last = null;
        for (TransitionSegment seg : segs) {
            TransitionProperty prop = extractor.extract(seg);
            if (prop == null) continue;
            // When to is null, the steady-state value is defaultVal, not from.
            // The from value is only used during interpolation (where interpolateProperty
            // fills null to with defaultVal). For final resolution, defaultVal is correct.
            if (prop.to() != null) {
                last = prop.to();
                if (prop.degrees() != null && prop.from() != null) {
                    last = RotationTravel.effectiveEnds(prop.from(), last, prop.degrees());
                }
            }
            // else: to is null → steady-state = defaultVal, don't set last
        }
        return (last != null) ? last : defaultVal;
    }

    private TransitionResult resolveFinalOrFirst(List<TransitionSegment> segs, boolean isFirst) {
        if (segs.isEmpty()) return TransitionResult.DEFAULT;

        float[] offset = isFirst
            ? resolvePropertyFirst(segs, DEFAULT_OFFSET, seg -> seg.offset())
            : resolvePropertyFinal(segs, DEFAULT_OFFSET, seg -> seg.offset());
        float[] scale = isFirst
            ? resolvePropertyFirst(segs, DEFAULT_SCALE, seg -> seg.scale())
            : resolvePropertyFinal(segs, DEFAULT_SCALE, seg -> seg.scale());
        float[] rotation = isFirst
            ? resolvePropertyFirst(segs, DEFAULT_ROTATION, seg -> seg.rotation())
            : resolvePropertyFinal(segs, DEFAULT_ROTATION, seg -> seg.rotation());
        float[] a = isFirst
            ? resolvePropertyFirst(segs, new float[]{DEFAULT_ALPHA}, seg -> seg.alpha())
            : resolvePropertyFinal(segs, new float[]{DEFAULT_ALPHA}, seg -> seg.alpha());

        return new TransitionResult(new Vec3(offset[0], offset[1], offset[2]), scale, a[0], rotation);
    }

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
