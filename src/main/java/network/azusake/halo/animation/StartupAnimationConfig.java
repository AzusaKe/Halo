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
    private final TransitionQueueBuilder.BackfillDirection direction;

    // Cache: groupId → built animation
    private final ConcurrentHashMap<String, TransitionAnimationResult> cache = new ConcurrentHashMap<>();

    // Cache: groupId → reversed animation (the fallback shutdown queue).
    // Built lazily from the forward queues; safe to share globally because it
    // is pure — per-instance endpoint alignment is applied on a copy later.
    private final ConcurrentHashMap<String, TransitionAnimationResult> reversedCache =
        new ConcurrentHashMap<>();

    // Global total duration (max across all segment lists)
    private final double globalTotalDuration;

    public StartupAnimationConfig(List<TransitionAnimation.TransitionSegment> segments,
                                   Map<String, List<TransitionAnimation.TransitionSegment>> idOverrides) {
        this(segments, idOverrides, TransitionQueueBuilder.BackfillDirection.STARTUP);
    }

    public StartupAnimationConfig(List<TransitionAnimation.TransitionSegment> segments,
                                   Map<String, List<TransitionAnimation.TransitionSegment>> idOverrides,
                                   TransitionQueueBuilder.BackfillDirection direction) {
        this.segments = segments != null ? segments : List.of();
        this.idOverrides = idOverrides != null ? idOverrides : Map.of();
        this.direction = direction != null ? direction : TransitionQueueBuilder.BackfillDirection.STARTUP;
        this.globalTotalDuration = computeGlobalTotal();
    }

    /** Backfill direction: STARTUP (end-anchored) or SHUTDOWN (head-anchored). */
    public TransitionQueueBuilder.BackfillDirection direction() {
        return direction;
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
     * Get or build the reversed copy of the resolved animation for a group.
     *
     * <p>This is the fade-out fallback used when a definition has a startup but
     * no shutdown: the startup timeline played backwards.  It is cached at the
     * definition level so per-instance shutdown playback only needs to patch
     * the derived head values (see {@code TransitionAnimationResult#withHead}).</p>
     *
     * @param groupId the group id (null or empty for unnamed groups)
     * @return the reversed animation, or null if no segments are configured
     */
    public TransitionAnimationResult getReversedAnimationForGroup(Optional<String> groupId) {
        String key = groupId.orElse("");
        return reversedCache.computeIfAbsent(key, k -> {
            TransitionAnimationResult base = getAnimationForGroup(groupId);
            return base != null ? base.reversed() : null;
        });
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

        TransitionQueue offQ = TransitionQueueBuilder.forOffset(segs, globalTotalDuration, direction).build();
        TransitionQueue sclQ = TransitionQueueBuilder.forScale(segs, globalTotalDuration, direction).build();
        TransitionQueue aQ   = TransitionQueueBuilder.forAlpha(segs, globalTotalDuration, direction).build();

        return new TransitionAnimationResult(offQ, sclQ, aQ);
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
