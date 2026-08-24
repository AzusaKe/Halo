package network.azusake.halo.api;

import net.minecraft.world.phys.Vec3;

/**
 * Resolved world-space head anchor for one entity frame.
 *
 * <p>Produced by {@link EntityAnchorProvider#resolve} each render frame and
 * consumed by the halo anchor pipeline to determine the halo target position
 * and orientation.  The six degrees of freedom are: head center position
 * ({@link #headCenter}) and head orientation ({@link #yaw}, {@link #pitch},
 * {@link #roll}).</p>
 *
 * @param headCenter  world-space head visual center (after pose-aware pivot + rotation)
 * @param yaw         interpolated head yaw in degrees (from entity.headYaw)
 * @param pitch       interpolated head pitch in degrees (from entity.getPitch)
 * @param roll        interpolated head roll in degrees; vanilla entities have no
 *                    head roll (0); the local player may inherit the camera roll
 */
public record HeadAnchor(
    Vec3 headCenter,
    float yaw,
    float pitch,
    float roll
) {}
