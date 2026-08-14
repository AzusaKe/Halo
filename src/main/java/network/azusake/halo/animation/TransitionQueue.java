package network.azusake.halo.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An ordered list of {@link TransitionQueueElement}s for a single property
 * of a single group, representing the complete resolved animation timeline.
 *
 * <p>After the build phase (gap filling + null backfill), every element has
 * concrete startVal and endVal — no nulls remain.  Rendering is a simple
 * binary search + lerp.</p>
 *
 * <p>The queue can be reversed for shutdown animations via {@link #reversed()}.</p>
 */
public class TransitionQueue {

    private final List<TransitionQueueElement> elements;
    private final float[] steadyStateValue;
    private final double totalDuration;

    /**
     * Create a queue from an already-resolved element list.
     *
     * @param elements       sorted by startTime, no gaps, no null values
     * @param steadyStateValue the steady-state (default) value for this property
     */
    public TransitionQueue(List<TransitionQueueElement> elements, float[] steadyStateValue) {
        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
        this.steadyStateValue = steadyStateValue;
        this.totalDuration = elements.isEmpty() ? 0
            : elements.get(elements.size() - 1).endTime();
    }

    /** Whether this queue has any elements. */
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    /** Total duration of this queue in seconds. */
    public double totalDuration() {
        return totalDuration;
    }

    /** The steady-state value for this property. */
    public float[] steadyStateValue() {
        return steadyStateValue;
    }

    /**
     * Evaluate the queue at the given time.
     *
     * @param time absolute time in seconds since transition start
     * @return interpolated value; before t=0 returns first startVal,
     *         after totalDuration returns last endVal
     */
    public float[] evaluate(double time) {
        if (elements.isEmpty()) {
            return steadyStateValue;
        }
        if (time <= elements.get(0).startTime()) {
            return elements.get(0).startVal();
        }
        TransitionQueueElement last = elements.get(elements.size() - 1);
        if (time >= last.endTime()) {
            return last.endVal();
        }
        // Binary search for the element containing time.
        // Use < (not <=): at exact boundaries (time == endTime),
        // stay on the current element. Discontinuities between
        // consecutive elements are the user's responsibility.
        int lo = 0, hi = elements.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (elements.get(mid).endTime() < time) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return elements.get(lo).evaluate(time);
    }

    /**
     * Create a reversed copy of this queue for shutdown animations.
     * Each element's startVal/endVal are swapped and times are remapped.
     *
     * @return a new TransitionQueue with reversed elements
     */
    public TransitionQueue reversed() {
        if (elements.isEmpty()) {
            return new TransitionQueue(List.of(), steadyStateValue);
        }
        List<TransitionQueueElement> reversed = new ArrayList<>(elements.size());
        for (int i = elements.size() - 1; i >= 0; i--) {
            reversed.add(elements.get(i).reversed(totalDuration));
        }
        normalizeDegrees(reversed);
        return new TransitionQueue(reversed, steadyStateValue);
    }

    /**
     * Return a copy whose leading values (trailing gap holds and the first
     * active element's start value, where authored {@code from} is absent)
     * are replaced by {@code headValue}.  Explicitly authored {@code from}
     * values are never touched.  Used for shutdown alignment: the fade-out
     * starts from the idle animation's value at the hide moment.
     */
    public TransitionQueue withHeadStart(float[] headValue) {
        if (elements.isEmpty()) return this;
        List<TransitionQueueElement> patched = new ArrayList<>(elements);
        boolean changed = false;
        int i = 0;
        while (i < patched.size() && !patched.get(i).fromExplicit()) {
            TransitionQueueElement e = patched.get(i);
            if (e.isHold()) {
                // Leading gap / derived hold — clamp the whole hold to headValue.
                patched.set(i, e.withValues(headValue, headValue));
                changed = true;
                i++;
            } else {
                // First active element with derived from — ramp from headValue.
                patched.set(i, e.withStartVal(headValue));
                changed = true;
                break;
            }
        }
        normalizeDegrees(patched);
        return changed ? new TransitionQueue(patched, steadyStateValue) : this;
    }

    /**
     * Return a copy whose trailing values (trailing gap holds and the last
     * active element's end value, where authored {@code to} is absent) are
     * replaced by {@code tailValue}.  Explicitly authored {@code to} values
     * are never touched.  Used for startup alignment: the fade-in ends on
     * the idle animation's value at the resume phase.
     */
    public TransitionQueue withTailEnd(float[] tailValue) {
        if (elements.isEmpty()) return this;
        List<TransitionQueueElement> patched = new ArrayList<>(elements);
        boolean changed = false;
        int i = patched.size() - 1;
        while (i >= 0 && !patched.get(i).toExplicit()) {
            TransitionQueueElement e = patched.get(i);
            if (e.isHold()) {
                // Trailing gap / derived hold — clamp the whole hold to tailValue.
                patched.set(i, e.withValues(tailValue, tailValue));
                changed = true;
                i--;
            } else {
                // Last active element with derived end — ramp to tailValue.
                patched.set(i, e.withEndVal(tailValue));
                changed = true;
                break;
            }
        }
        normalizeDegrees(patched);
        return changed ? new TransitionQueue(patched, steadyStateValue) : this;
    }

    /**
     * Idempotent pass applying each element's {@code degrees} minimum-travel
     * rule: the effective end becomes {@code from + d} (see
     * {@link RotationTravel}).  Elements without degrees are untouched.
     * Runs after build and after every per-instance endpoint patch, because
     * derived from/to depend on the idle animation at the trigger phase.
     */
    static void normalizeDegrees(List<TransitionQueueElement> elements) {
        for (int i = 0; i < elements.size(); i++) {
            TransitionQueueElement e = elements.get(i);
            float[] degrees = e.degrees();
            if (degrees == null || e.startVal() == null || e.endVal() == null) {
                continue;
            }
            float[] end = RotationTravel.effectiveEnds(e.startVal(), e.endVal(), degrees);
            if (!java.util.Arrays.equals(e.endVal(), end)) {
                elements.set(i, e.withEndVal(end));
            }
        }
    }

    /**
     * Raw element list (for serialization / debug).
     */
    public List<TransitionQueueElement> elements() {
        return elements;
    }
}
