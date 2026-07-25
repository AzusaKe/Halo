package network.azusake.halo.animation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration for a halo startup or shutdown transition animation.
 *
 * <p>Contains default segments that apply to all groups, plus optional
 * per-group-id overrides that allow different groups to animate
 * differently.</p>
 *
 * @param segments     default transition segments for all groups
 * @param idOverrides  per-group-id segment overrides (keyed by group id)
 */
public record StartupAnimationConfig(
    List<TransitionAnimation.TransitionSegment> segments,
    Map<String, List<TransitionAnimation.TransitionSegment>> idOverrides
) {

    /**
     * Return the segments that apply to the given group, falling back to the
     * global default if no per-group override exists.
     *
     * @param groupId the group id (may be {@code Optional.empty()} for unnamed groups)
     * @return the applicable segment list, or an empty list if nothing is configured
     */
    public List<TransitionAnimation.TransitionSegment> getSegmentsForGroup(Optional<String> groupId) {
        if (groupId.isPresent()) {
            List<TransitionAnimation.TransitionSegment> override = idOverrides.get(groupId.get());
            if (override != null && !override.isEmpty()) {
                return override;
            }
        }
        return segments != null ? segments : List.of();
    }

    /**
     * Maximum total duration across all segment lists (default + all overrides).
     * Used to determine the overall transition duration for visibility checks.
     */
    public double maxDuration() {
        double max = 0;
        if (segments != null && !segments.isEmpty()) {
            max = Math.max(max, new TransitionAnimation(segments).totalDuration());
        }
        if (idOverrides != null) {
            for (List<TransitionAnimation.TransitionSegment> override : idOverrides.values()) {
                if (override != null && !override.isEmpty()) {
                    max = Math.max(max, new TransitionAnimation(override).totalDuration());
                }
            }
        }
        return max;
    }
}
