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
        // Use <= so that at exact boundaries (time == endTime), we advance
        // to the next element. This ensures that when a gap ends and an
        // active animation starts at the same time, the active animation
        // is selected (startVal takes precedence over gap's endVal).
        int lo = 0, hi = elements.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (elements.get(mid).endTime() <= time) {
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
        return new TransitionQueue(reversed, steadyStateValue);
    }

    /**
     * Raw element list (for serialization / debug).
     */
    public List<TransitionQueueElement> elements() {
        return elements;
    }
}
