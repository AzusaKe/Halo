package network.azusake.halo.physics;

import net.minecraft.util.math.Vec3d;

/**
 * Resolved world-space head anchor for one entity frame.
 *
 * <p>Produced by {@link EntityAnchorProvider#resolve} each render frame,
 * consumed by {@link AnchorFrameCalculator#calculate} to determine the
 * halo target position and orientation.</p>
 *
 * @param headCenter  world-space head visual center (after pose-aware pivot + rotation)
 * @param yaw         interpolated head yaw in degrees (from entity.headYaw)
 * @param pitch       interpolated head pitch in degrees (from entity.getPitch)
 */
public record HeadAnchor(
    Vec3d headCenter,
    float yaw,
    float pitch
) {}
