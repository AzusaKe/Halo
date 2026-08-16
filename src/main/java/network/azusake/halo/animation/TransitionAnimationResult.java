package network.azusake.halo.animation;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Fully resolved transition animation for a single group, consisting of
 * four {@link TransitionQueue}s (offset, scale, alpha, rotation).
 *
 * <p>Built once from parsed segments, then reused every frame.
 * Shutdown animations are created via {@link #reversed()}; endpoint
 * alignment with the group's idle animation is applied per-instance via
 * {@link #withHead(LayerAnimation, double)} / {@link #withTail(LayerAnimation, double)}.</p>
 */
public class TransitionAnimationResult {

    private final TransitionQueue offsetQueue;
    private final TransitionQueue scaleQueue;
    private final TransitionQueue alphaQueue;
    private final TransitionQueue rotationQueue;
    private final double totalDuration;

    public TransitionAnimationResult(TransitionQueue offsetQueue,
                                      TransitionQueue scaleQueue,
                                      TransitionQueue alphaQueue,
                                      TransitionQueue rotationQueue) {
        this.offsetQueue = offsetQueue;
        this.scaleQueue = scaleQueue;
        this.alphaQueue = alphaQueue;
        this.rotationQueue = rotationQueue;
        this.totalDuration = Math.max(
            Math.max(offsetQueue.totalDuration(), scaleQueue.totalDuration()),
            Math.max(alphaQueue.totalDuration(), rotationQueue.totalDuration())
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
     * Whether this animation drives the group's rotation (a non-empty rotation
     * queue).  Groups whose transition does not drive rotation keep their idle
     * rotation frozen at the trigger phase instead — see
     * {@code HaloRenderer#frozenIdleRotationDegrees}.
     */
    public boolean rotationAnimated() {
        return !rotationQueue.isEmpty();
    }

    /**
     * Evaluate the animation at the given time.
     *
     * @param time absolute time in seconds since transition start
     * @return the interpolated offset, scale, alpha, and rotation
     */
    public TransitionResult evaluate(double time) {
        float[] off = offsetQueue.isEmpty()
            ? offsetQueue.steadyStateValue()
            : offsetQueue.evaluate(time);
        float[] scl = scaleQueue.isEmpty()
            ? scaleQueue.steadyStateValue()
            : scaleQueue.evaluate(time);
        float[] a = alphaQueue.isEmpty()
            ? alphaQueue.steadyStateValue()
            : alphaQueue.evaluate(time);
        float[] rot = rotationQueue.isEmpty()
            ? rotationQueue.steadyStateValue()
            : rotationQueue.evaluate(time);

        return new TransitionResult(
            new Vec3(off[0], off[1], off[2]),
            scl,
            a[0],
            rot
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
            alphaQueue.reversed(),
            rotationQueue.reversed()
        );
    }

    /**
     * Return a copy aligned for shutdown: every property's leading value
     * (derived {@code from}) becomes the group's idle animation value at
     * {@code phase} (the hide moment).  Explicitly authored values win;
     * empty queues become a constant hold so the group freezes at its idle
     * state instead of snapping to the identity.
     */
    public TransitionAnimationResult withHead(LayerAnimation idle, double phase) {
        double total = totalDuration();
        return new TransitionAnimationResult(
            patchQueue(offsetQueue, offsetOf(idle, phase), total, false),
            patchQueue(scaleQueue, idle.evaluateScale(phase), total, false),
            patchQueue(alphaQueue, new float[]{idle.evaluateAlpha(phase)}, total, false),
            patchQueue(rotationQueue, idle.evaluateRotationDegrees(phase), total, false));
    }

    /**
     * Return a copy aligned for a shutdown triggered mid-transition: every
     * property's leading value (derived {@code from}) becomes the given
     * on-screen values the renderer was applying when the hide happened.
     * Explicitly authored values win; empty queues become a constant hold so
     * the group freezes at its on-screen state instead of snapping.
     *
     * @param offset          the applied offset (3 components)
     * @param scale           the applied scale (3 components)
     * @param alpha           the applied alpha multiplier
     * @param rotationDegrees the applied rotation (YXZ Euler degrees, 3 components)
     */
    public TransitionAnimationResult withHeadValues(float[] offset, float[] scale, float alpha,
                                                     float[] rotationDegrees) {
        double total = totalDuration();
        return new TransitionAnimationResult(
            patchQueue(offsetQueue, offset, total, false),
            patchQueue(scaleQueue, scale, total, false),
            patchQueue(alphaQueue, new float[]{alpha}, total, false),
            patchQueue(rotationQueue, rotationDegrees, total, false));
    }

    /**
     * Back-compat convenience: {@link #withHeadValues(float[], float[], float[], float[])}
     * with an identity rotation.
     */
    public TransitionAnimationResult withHeadValues(float[] offset, float[] scale, float alpha) {
        return withHeadValues(offset, scale, alpha, new float[]{0f, 0f, 0f});
    }

    /**
     * Return a copy aligned for startup: every property's trailing value
     * (derived {@code to}) becomes the group's idle animation value at
     * {@code phase} (the resume moment).  Explicitly authored values win;
     * empty queues become a constant hold at the idle value.
     */
    public TransitionAnimationResult withTail(LayerAnimation idle, double phase) {
        double total = totalDuration();
        return new TransitionAnimationResult(
            patchQueue(offsetQueue, offsetOf(idle, phase), total, true),
            patchQueue(scaleQueue, idle.evaluateScale(phase), total, true),
            patchQueue(alphaQueue, new float[]{idle.evaluateAlpha(phase)}, total, true),
            patchQueue(rotationQueue, idle.evaluateRotationDegrees(phase), total, true));
    }

    /** The result of evaluating a transition at a specific time. */
    public record TransitionResult(Vec3 offset, float[] scale, float alpha,
                                   float[] rotationDegrees) {
        public static final TransitionResult DEFAULT = new TransitionResult(
            Vec3.ZERO, new float[]{1f, 1f, 1f}, 1.0f, new float[]{0f, 0f, 0f}
        );
    }

    // ------------------------------------------------------------------
    // Endpoint patching helpers
    // ------------------------------------------------------------------

    private static float[] offsetOf(LayerAnimation idle, double phase) {
        Vec3 v = idle.evaluateOffset(phase);
        return new float[]{(float) v.x, (float) v.y, (float) v.z};
    }

    private static TransitionQueue patchQueue(TransitionQueue queue, float[] value,
                                              double total, boolean tail) {
        if (queue.isEmpty()) {
            return holdQueue(value, total);
        }
        return tail ? queue.withTailEnd(value) : queue.withHeadStart(value);
    }

    private static TransitionQueue holdQueue(float[] value, double total) {
        double duration = Math.max(total, 1e-3);
        TransitionQueueElement element = new TransitionQueueElement(
            0, duration, duration, value, value, EasingType.LINEAR);
        return new TransitionQueue(List.of(element), value);
    }
}
