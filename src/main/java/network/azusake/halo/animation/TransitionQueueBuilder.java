package network.azusake.halo.animation;

import java.util.ArrayList;
import java.util.List;

import network.azusake.halo.animation.TransitionAnimation.TransitionSegment;
import network.azusake.halo.animation.TransitionAnimation.TransitionProperty;

/**
 * Builds {@link TransitionQueue}s from parsed {@link TransitionSegment}s.
 *
 * <h3>Build Phases</h3>
 * <ol>
 *   <li><b>Parse</b>: Walk segments sequentially, create raw elements with
 *       computed start/end times and from/to values (to may be null)</li>
 *   <li><b>Gap fill</b>: Insert identity elements to cover gaps between
 *       consecutive elements, creating a continuous timeline</li>
 *   <li><b>Null backfill</b>: Walk backwards to fill null startVal/endVal
 *       using the steady-state value and inter-element inheritance</li>
 * </ol>
 *
 * <p>After building, every element has concrete startVal and endVal.
 * The resulting queue is ready for rendering with no further null handling.</p>
 */
public class TransitionQueueBuilder {

    /** Which endpoint anchors the null-fill pass. */
    public enum BackfillDirection {
        /** End-anchored backward pass (fade-in as written). */
        STARTUP,
        /** Head-anchored forward pass (fade-out as written). */
        SHUTDOWN
    }

    private final List<TransitionSegment> segments;
    private final float[] steadyStateValue;
    private final double globalTotalDuration;
    private final BackfillDirection direction;

    /**
     * @param segments           parsed segments from JSON (may have null from/to)
     * @param steadyStateValue   the default value for this property type
     *                           (offset=[0,0,0], scale=[1,1,1], alpha=[1])
     * @param globalTotalDuration the total duration across ALL property queues
     *                           (max of all queues' last elements' end times).
     *                           Used for gap filling to ensure continuous timeline.
     */
    public TransitionQueueBuilder(List<TransitionSegment> segments, float[] steadyStateValue,
                                   double globalTotalDuration) {
        this(segments, steadyStateValue, globalTotalDuration, BackfillDirection.STARTUP);
    }

    /**
     * @param segments           parsed segments from JSON (may have null from/to)
     * @param steadyStateValue   the default value for this property type
     *                           (offset=[0,0,0], scale=[1,1,1], alpha=[1])
     * @param globalTotalDuration the total duration across ALL property queues
     *                           (max of all queues' last elements' end times).
     *                           Used for gap filling to ensure continuous timeline.
     * @param direction          which endpoint anchors the null-fill pass
     *                           (STARTUP = backward/end-anchored,
     *                           SHUTDOWN = forward/head-anchored)
     */
    public TransitionQueueBuilder(List<TransitionSegment> segments, float[] steadyStateValue,
                                   double globalTotalDuration, BackfillDirection direction) {
        this.segments = segments;
        this.steadyStateValue = steadyStateValue;
        this.globalTotalDuration = globalTotalDuration;
        this.direction = direction;
    }

    /**
     * Build the fully-resolved queue.
     */
    public TransitionQueue build() {
        if (segments.isEmpty()) {
            return new TransitionQueue(List.of(), steadyStateValue);
        }

        // Phase 1: Parse segments into raw elements
        List<TransitionQueueElement> raw = parseSegments();

        // Phase 2: Fill gaps with identity elements
        List<TransitionQueueElement> filled = fillGaps(raw);

        // Phase 3: Backfill null startVal/endVal
        backfillNulls(filled);

        // Phase 4: Apply the minimum-travel rule for elements that carry
        // degrees.  Idempotent — also re-run after every per-instance endpoint
        // patch (withHead/withTail), since derived endpoints depend on the idle
        // animation at the trigger phase.
        TransitionQueue.normalizeDegrees(filled);

        return new TransitionQueue(filled, steadyStateValue);
    }

    // ==================================================================
    // Phase 1: Parse segments
    // ==================================================================

    /**
     * Walk segments sequentially. For each segment's property, create an
     * element with:
     * - start = max(queue.tail.end, sum_of_previous_segment_durations)
     * - end = start + duration (segment or property-level)
     * - startVal = from (may be null)
     * - endVal = to (may be null)
     * - easing = segment or property-level
     */
    private List<TransitionQueueElement> parseSegments() {
        List<TransitionQueueElement> elements = new ArrayList<>();
        double segTimeAccum = 0; // sum of segment durations seen so far
        double prevEnd = 0;     // end time of previous element in this queue

        for (TransitionSegment seg : segments) {
            // Get the property extractor from the segment
            // This is called once per property type via the caller
            // For now, we use the segment's own fields
            float[] from = extractFrom(seg);
            float[] to = extractTo(seg);
            double dur = extractDuration(seg);
            EasingType easing = extractEasing(seg);

            if (from != null || to != null) {
                // This segment has animation for this property
                double start = Math.max(prevEnd, segTimeAccum);
                double end = start + dur;
                float[] degrees = extractDegrees(seg);
                elements.add(degrees != null
                    ? new TransitionQueueElement(start, end, dur, from, to, easing, degrees)
                    : new TransitionQueueElement(start, end, dur, from, to, easing));
                prevEnd = end;
            }
            // else: no animation for this property in this segment

            segTimeAccum += seg.duration();
        }

        return elements;
    }

    // These will be set by the per-property builder subclass
    protected float[] extractFrom(TransitionSegment seg) { return null; }
    protected float[] extractTo(TransitionSegment seg) { return null; }
    protected float[] extractDegrees(TransitionSegment seg) { return null; }
    protected double extractDuration(TransitionSegment seg) { return seg.duration(); }
    protected EasingType extractEasing(TransitionSegment seg) { return seg.easing(); }

    // ==================================================================
    // Phase 2: Gap filling
    // ==================================================================

    /**
     * Insert identity elements to cover gaps between consecutive elements.
     * Also covers the gap from 0 to the first element's start, and from
     * the last element's end to the total duration.
     */
    private List<TransitionQueueElement> fillGaps(List<TransitionQueueElement> raw) {
        if (raw.isEmpty()) return raw;

        double totalDuration = computeTotalDuration();

        List<TransitionQueueElement> filled = new ArrayList<>();
        double cursor = 0;

        for (TransitionQueueElement elem : raw) {
            if (elem.startTime() > cursor + 1e-6) {
                // Gap: insert identity element (from=to=null, will be backfilled)
                double gapDur = elem.startTime() - cursor;
                filled.add(new TransitionQueueElement(
                    cursor, elem.startTime(), gapDur, null, null, EasingType.LINEAR));
            }
            filled.add(elem);
            cursor = elem.endTime();
        }

        // Trailing gap after last element
        if (cursor < totalDuration - 1e-6) {
            double gapDur = totalDuration - cursor;
            filled.add(new TransitionQueueElement(
                cursor, totalDuration, gapDur, null, null, EasingType.LINEAR));
        }

        return filled;
    }

    /**
     * Compute total duration for gap filling. Uses the global total
     * (max across all property queues) to ensure the timeline is continuous.
     */
    private double computeTotalDuration() {
        return globalTotalDuration;
    }

    // ==================================================================
    /**
     * Phase 3: Fill null startVal/endVal with a single backwards pass.
     * Only null values are modified — explicitly set from/to are NEVER touched.
     *
     * Algorithm (from last element to first):
     * <ol>
     *   <li>If endVal is null → use next element's startVal (already resolved
     *       since we process back to front), or steadyStateValue if last element</li>
     *   <li>If startVal is null → set to endVal</li>
     * </ol>
     */
    private void backfillNulls(List<TransitionQueueElement> elements) {
        if (direction == BackfillDirection.SHUTDOWN) {
            backfillNullsForward(elements);
        } else {
            backfillNullsBackward(elements);
        }
    }

    /**
     * Startup: end-anchored backward pass (unchanged).  Null end values
     * inherit the next element's start value (or the steady-state tail
     * anchor); null start values hold at their own end value.
     */
    private void backfillNullsBackward(List<TransitionQueueElement> elements) {
        for (int i = elements.size() - 1; i >= 0; i--) {
            TransitionQueueElement curr = elements.get(i);
            float[] startVal = curr.startVal();
            float[] endVal = curr.endVal();

            if (endVal == null) {
                endVal = (i + 1 < elements.size()) ? elements.get(i + 1).startVal() : steadyStateValue;
            }
            if (startVal == null) startVal = endVal;

            if (curr.startVal() != startVal || curr.endVal() != endVal) {
                elements.set(i, curr.withValues(startVal, endVal));
            }
        }
    }

    /**
     * Shutdown: head-anchored forward pass (mirror of startup).  Null start
     * values inherit the previous element's end value (or the steady-state
     * head anchor placeholder for the first element — patched per-instance by
     * {@link TransitionQueue#withHeadStart}); null end values hold at their
     * own start value.
     */
    private void backfillNullsForward(List<TransitionQueueElement> elements) {
        for (int i = 0; i < elements.size(); i++) {
            TransitionQueueElement curr = elements.get(i);
            float[] startVal = curr.startVal();
            float[] endVal = curr.endVal();

            if (startVal == null) {
                startVal = (i > 0) ? elements.get(i - 1).endVal() : steadyStateValue;
            }
            if (endVal == null) endVal = startVal;

            if (curr.startVal() != startVal || curr.endVal() != endVal) {
                elements.set(i, curr.withValues(startVal, endVal));
            }
        }
    }

    // ==================================================================
    // Builder factory for property-specific extraction
    // ==================================================================

    /**
     * Create a builder that extracts the offset property from segments.
     */
    public static TransitionQueueBuilder forOffset(List<TransitionSegment> segments, double globalTotal,
                                                    BackfillDirection direction) {
        return new TransitionQueueBuilder(segments, new float[]{0f, 0f, 0f}, globalTotal, direction) {
            @Override protected float[] extractFrom(TransitionSegment seg) {
                return seg.offset() != null ? seg.offset().from() : null;
            }
            @Override protected float[] extractTo(TransitionSegment seg) {
                return seg.offset() != null ? seg.offset().to() : null;
            }
            @Override protected double extractDuration(TransitionSegment seg) {
                return seg.offset() != null && seg.offset().propertyDuration() != null
                    ? seg.offset().propertyDuration() : seg.duration();
            }
            @Override protected EasingType extractEasing(TransitionSegment seg) {
                return seg.offset() != null && seg.offset().propertyEasing() != null
                    ? seg.offset().propertyEasing() : seg.easing();
            }
        };
    }

    /**
     * Create a builder that extracts the scale property from segments.
     */
    public static TransitionQueueBuilder forScale(List<TransitionSegment> segments, double globalTotal,
                                                   BackfillDirection direction) {
        return new TransitionQueueBuilder(segments, new float[]{1f, 1f, 1f}, globalTotal, direction) {
            @Override protected float[] extractFrom(TransitionSegment seg) {
                return seg.scale() != null ? seg.scale().from() : null;
            }
            @Override protected float[] extractTo(TransitionSegment seg) {
                return seg.scale() != null ? seg.scale().to() : null;
            }
            @Override protected double extractDuration(TransitionSegment seg) {
                return seg.scale() != null && seg.scale().propertyDuration() != null
                    ? seg.scale().propertyDuration() : seg.duration();
            }
            @Override protected EasingType extractEasing(TransitionSegment seg) {
                return seg.scale() != null && seg.scale().propertyEasing() != null
                    ? seg.scale().propertyEasing() : seg.easing();
            }
        };
    }

    /**
     * Create a builder that extracts the rotation property from segments.
     * Rotation values are YXZ Euler degrees; steady state is identity [0, 0, 0].
     */
    public static TransitionQueueBuilder forRotation(List<TransitionSegment> segments, double globalTotal,
                                                      BackfillDirection direction) {
        return new TransitionQueueBuilder(segments, new float[]{0f, 0f, 0f}, globalTotal, direction) {
            @Override protected float[] extractFrom(TransitionSegment seg) {
                return seg.rotation() != null ? seg.rotation().from() : null;
            }
            @Override protected float[] extractTo(TransitionSegment seg) {
                return seg.rotation() != null ? seg.rotation().to() : null;
            }
            @Override protected float[] extractDegrees(TransitionSegment seg) {
                return seg.rotation() != null ? seg.rotation().degrees() : null;
            }
            @Override protected double extractDuration(TransitionSegment seg) {
                return seg.rotation() != null && seg.rotation().propertyDuration() != null
                    ? seg.rotation().propertyDuration() : seg.duration();
            }
            @Override protected EasingType extractEasing(TransitionSegment seg) {
                return seg.rotation() != null && seg.rotation().propertyEasing() != null
                    ? seg.rotation().propertyEasing() : seg.easing();
            }
        };
    }

    /**
     * Create a builder that extracts the alpha property from segments.
     */
    public static TransitionQueueBuilder forAlpha(List<TransitionSegment> segments, double globalTotal,
                                                   BackfillDirection direction) {
        return new TransitionQueueBuilder(segments, new float[]{1f}, globalTotal, direction) {
            @Override protected float[] extractFrom(TransitionSegment seg) {
                return seg.alpha() != null ? seg.alpha().from() : null;
            }
            @Override protected float[] extractTo(TransitionSegment seg) {
                return seg.alpha() != null ? seg.alpha().to() : null;
            }
            @Override protected double extractDuration(TransitionSegment seg) {
                return seg.alpha() != null && seg.alpha().propertyDuration() != null
                    ? seg.alpha().propertyDuration() : seg.duration();
            }
            @Override protected EasingType extractEasing(TransitionSegment seg) {
                return seg.alpha() != null && seg.alpha().propertyEasing() != null
                    ? seg.alpha().propertyEasing() : seg.easing();
            }
        };
    }
}
