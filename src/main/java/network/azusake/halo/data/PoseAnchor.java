package network.azusake.halo.data;

import net.minecraft.util.math.Vec3d;

/**
 * A pose-specific anchor configuration for an entity type.
 *
 * <p>Stores the neck pivot (relative to the entity's feet, in local coordinates)
 * and the offset from that pivot to the head visual center, also in local
 * coordinates.  Applying the entity's head yaw/pitch rotation to
 * {@code headCenterVector} and adding it to the world-space pivot gives the
 * world-space head center.</p>
 *
 * <p>Coordinate frame (local, before rotation):
 *   +X = entity right, +Y = up, +Z = forward (yaw=0 direction).</p>
 *
 * @param pivot             local coordinates of the neck rotation center (blocks)
 * @param headCenterVector  local vector from pivot to head visual center (blocks)
 */
public record PoseAnchor(
    Vec3d pivot,
    Vec3d headCenterVector
) {}
