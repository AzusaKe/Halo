package network.azusake.halo.data;

import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Optional;

/**
 * A per-entity-type set of pose-specific {@link PoseAnchor}s, loaded from
 * {@code data/halo/entity_anchors/*.json}.
 *
 * <p>Each profile maps pose keys (e.g. {@code "standing"}, {@code "swimming"})
 * to their anchor configuration.  Pose keys that are not present fall back to
 * the {@code defaultPose} entry.</p>
 *
 * @param entity      the Minecraft entity type identifier this profile applies to
 * @param defaultPose the fallback pose key when a requested key is absent
 * @param poses       map of pose key → {@link PoseAnchor} (non-empty)
 */
public record EntityAnchorProfile(
    Identifier entity,
    String defaultPose,
    Map<String, PoseAnchor> poses
) {
    /**
     * Resolve the {@link PoseAnchor} for a pose key.
     * Falls back to {@code defaultPose} if the key is absent,
     * and returns {@link Optional#empty()} only when the entire profile is
     * invalid (both the requested key and the default are missing, or the
     * poses map is empty).
     */
    public Optional<PoseAnchor> resolve(String poseKey) {
        PoseAnchor direct = poses.get(poseKey);
        if (direct != null) {
            return Optional.of(direct);
        }
        return Optional.ofNullable(poses.get(defaultPose));
    }

    // ------------------------------------------------------------------
    // Validation helpers
    // ------------------------------------------------------------------

    /**
     * @return true if this profile has at least one pose entry (minimally viable)
     */
    public boolean isValid() {
        return !poses.isEmpty() && poses.containsKey(defaultPose);
    }
}
