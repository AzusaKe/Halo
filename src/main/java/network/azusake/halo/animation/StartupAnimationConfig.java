package network.azusake.halo.animation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for a halo startup or shutdown transition animation.
 *
 * <p>Contains default segments that apply to all groups, plus optional
 * per-group-id overrides.  Animations are built lazily and cached.</p>
 */
public class StartupAnimationConfig {

    private final List<TransitionAnimation.TransitionSegment> segments;
    private final Map<String, List<TransitionAnimation.TransitionSegment>> idOverrides;

    // Cache: groupId → built animation
    private final ConcurrentHashMap<String, TransitionAnimationResult> cache = new ConcurrentHashMap<>();

    // Global total duration (max across all segment lists)
    private final double globalTotalDuration;

    public StartupAnimationConfig(List<TransitionAnimation.TransitionSegment> segments,
                                   Map<String, List<TransitionAnimation.TransitionSegment>> idOverrides) {
        this.segments = segments != null ? segments : List.of();
        this.idOverrides = idOverrides != null ? idOverrides : Map.of();
        this.globalTotalDuration = computeGlobalTotal();
    }

    /** Default segments for all groups (may be empty). */
    public List<TransitionAnimation.TransitionSegment> segments() {
        return segments;
    }

    /** Per-group-id segment overrides. */
    public Map<String, List<TransitionAnimation.TransitionSegment>> idOverrides() {
        return idOverrides;
    }

    /**
     * Get or build the resolved animation for a specific group.
     *
     * @param groupId the group id (null or empty for unnamed groups)
     * @return the resolved animation, or null if no segments are configured
     */
    public TransitionAnimationResult getAnimationForGroup(Optional<String> groupId) {
        String key = groupId.orElse("");
        return cache.computeIfAbsent(key, k -> buildAnimation(groupId));
    }

    /**
     * Maximum total duration across all segment lists.
     * Used for visibility checks in HaloInstance.isTransitioning().
     */
    public double maxDuration() {
        return globalTotalDuration;
    }

    // ==================================================================
    // Internal
    // ==================================================================

    private TransitionAnimationResult buildAnimation(Optional<String> groupId) {
        List<TransitionAnimation.TransitionSegment> segs = getSegmentsForGroup(groupId);
        if (segs.isEmpty()) return null;

        TransitionQueue offQ = TransitionQueueBuilder.forOffset(segs, globalTotalDuration).build();
        TransitionQueue sclQ = TransitionQueueBuilder.forScale(segs, globalTotalDuration).build();
        TransitionQueue opQ  = TransitionQueueBuilder.forOpacity(segs, globalTotalDuration).build();

        return new TransitionAnimationResult(offQ, sclQ, opQ);
    }

    public List<TransitionAnimation.TransitionSegment> getSegmentsForGroup(Optional<String> groupId) {
        if (groupId.isPresent()) {
            List<TransitionAnimation.TransitionSegment> override = idOverrides.get(groupId.get());
            if (override != null && !override.isEmpty()) {
                return override;
            }
        }
        return !segments.isEmpty() ? segments : List.of();
    }

    /**
     * Compute the global total duration: max across all segment lists'
     * effective durations (sum of segment durations for each list).
     */
    private double computeGlobalTotal() {
        double max = 0;
        if (!segments.isEmpty()) {
            max = Math.max(max, sumDurations(segments));
        }
        if (idOverrides != null) {
            for (List<TransitionAnimation.TransitionSegment> override : idOverrides.values()) {
                if (override != null && !override.isEmpty()) {
                    max = Math.max(max, sumDurations(override));
                }
            }
        }
        return max;
    }

    private static double sumDurations(List<TransitionAnimation.TransitionSegment> segs) {
        double sum = 0;
        for (TransitionAnimation.TransitionSegment seg : segs) {
            sum += seg.duration();
        }
        return sum;
    }
}
