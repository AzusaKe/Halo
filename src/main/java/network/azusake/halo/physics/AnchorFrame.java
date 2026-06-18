package network.azusake.halo.physics;

import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * World-space coordinate frame for a halo model, computed each frame by
 * {@link AnchorFrameCalculator}.
 *
 * <p>This is a pure data carrier representing the full 6-DOF placement
 * of the halo definition's local coordinate system in world space.
 * The renderer uses it to transform the definition origin, axes, and
 * scale into the world, then renders each layer with its own local
 * transform relative to this frame.</p>
 *
 * <h3>Coordinate mapping</h3>
 * <ul>
 *   <li>Definition origin (0,0,0) → {@link #worldPosition}</li>
 *   <li>Definition normal (-Y) → {@link #toHeadDirection} (always toward head)</li>
 *   <li>Definition forward (+Z) → {@link #worldForward} (player look in LOCKED, damped in FREE)</li>
 *   <li>Full orientation stored in {@link #worldOrientation} (definition → world quaternion)</li>
 * </ul>
 *
 * @param worldPosition      world-space centre of the halo origin
 * @param cameraRelativePos  pre-computed camera-relative coordinates
 * @param worldOrientation   definition-local → world-space rotation quaternion
 * @param worldForward       definition +Z axis mapped to world space
 * @param toHeadDirection    unit vector from halo centre toward entity head
 * @param scale              uniform scale multiplier
 */
public record AnchorFrame(
    Vec3d worldPosition,
    Vec3d cameraRelativePos,
    Quaternionf worldOrientation,
    Vec3d worldForward,
    Vec3d toHeadDirection,
    float scale
) {}
