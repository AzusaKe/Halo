package network.azusake.halo.physics;

import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * World-space pose of a halo "volume" computed by {@link HaloPoseCalculator}.
 *
 * <p>This is a pure data carrier passed from the pose calculator to the
 * renderer.  The renderer uses it to align the halo definition's local
 * coordinate system (origin at definition centre, default orientation)
 * with the actual world-space placement.</p>
 *
 * <p>Future multi-layer halos will render each layer at its own relative
 * offset / rotation within this volume, making the pose the single
 * definition-to-world transform root.</p>
 *
 * @param worldPosition      world-space centre of the halo volume
 * @param cameraRelativePos  pre-computed camera-relative coordinates
 *                           ({@code worldPosition - camera.position})
 * @param orientation        world-space rotation quaternion (maps the
 *                           definition's default facing to the actual
 *                           world orientation)
 * @param scale              uniform scale multiplier
 * @param toHeadDirection    unit vector from the halo centre toward the
 *                           entity's head anchor (used for billboard
 *                           face-toward-camera alignment)
 */
public record HaloPose(
    Vec3d worldPosition,
    Vec3d cameraRelativePos,
    Quaternionf orientation,
    float scale,
    Vec3d toHeadDirection
) {}
