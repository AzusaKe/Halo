package network.azusake.halo.animation;

import net.minecraft.util.math.Vec3d;

/**
 * Fully resolved transition animation for a single group, consisting of
 * three {@link TransitionQueue}s (offset, scale, opacity).
 *
 * <p>Built once from parsed segments, then reused every frame.
 * Shutdown animations are created via {@link #reversed()}.</p>
 */
public class TransitionAnimationResult {

    private final TransitionQueue offsetQueue;
    private final TransitionQueue scaleQueue;
    private final TransitionQueue opacityQueue;
    private final double totalDuration;

    public TransitionAnimationResult(TransitionQueue offsetQueue,
                                      TransitionQueue scaleQueue,
                                      TransitionQueue opacityQueue) {
        this.offsetQueue = offsetQueue;
        this.scaleQueue = scaleQueue;
        this.opacityQueue = opacityQueue;
        this.totalDuration = Math.max(
            Math.max(offsetQueue.totalDuration(), scaleQueue.totalDuration()),
            opacityQueue.totalDuration()
        );
    }

    /** Total duration of this animation (max across all property queues). */
    public double totalDuration() {
        return totalDuration;
    }

    /** The scale property queue (for debug). */
    public TransitionQueue scaleQueue() {
        return scaleQueue;
    }

    /**
     * Evaluate the animation at the given time.
     *
     * @param time absolute time in seconds since transition start
     * @return the interpolated offset, scale, and opacity
     */
    public TransitionResult evaluate(double time) {
        float[] off = offsetQueue.isEmpty()
            ? offsetQueue.steadyStateValue()
            : offsetQueue.evaluate(time);
        float[] scl = scaleQueue.isEmpty()
            ? scaleQueue.steadyStateValue()
            : scaleQueue.evaluate(time);
        float[] op = opacityQueue.isEmpty()
            ? opacityQueue.steadyStateValue()
            : opacityQueue.evaluate(time);

        return new TransitionResult(
            new Vec3d(off[0], off[1], off[2]),
            scl,
            op[0]
        );
    }

    /**
     * Create a reversed copy for shutdown animations.
     * Each property queue is independently reversed.
     */
    public TransitionAnimationResult reversed() {
        return new TransitionAnimationResult(
            offsetQueue.reversed(),
            scaleQueue.reversed(),
            opacityQueue.reversed()
        );
    }

    /** The result of evaluating a transition at a specific time. */
    public record TransitionResult(Vec3d offset, float[] scale, float opacity) {
        public static final TransitionResult DEFAULT = new TransitionResult(
            Vec3d.ZERO, new float[]{1f, 1f, 1f}, 1.0f
        );
    }
}
