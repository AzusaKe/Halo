package network.azusake.halo.shape;

import network.azusake.halo.animation.LayerAnimation;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.util.Optional;

/**
 * A single layer within a {@link HaloModel}.
 *
 * <p>Each layer has its own local transform (position / rotation / scale)
 * relative to the halo definition's origin and default orientation.
 * Layers are rendered in order; the first layer draws first (behind).</p>
 *
 * @param id         optional name for animation-group binding (future)
 * @param position   offset from the definition origin in local space
 * @param rotation   orientation relative to the definition default
 *                   (stored as a quaternion; converted from Euler at load time)
 * @param scale      uniform scale multiplier for this layer (default 1.0)
 * @param primitive  the renderable primitive (billboard, future 3D model)
 * @param glowing    whether the glow layer renders for this layer (default true)
 * @param animation  optional per-layer visual animation (offset + rotation over time)
 */
public record HaloLayer(
    Optional<String> id,
    Vec3d position,
    Quaternionf rotation,
    float scale,
    HaloPrimitive primitive,
    boolean glowing,
    Optional<LayerAnimation> animation
) {
    /** Convenience constructor with identity rotation and unit scale. */
    public HaloLayer(Vec3d position, HaloPrimitive primitive) {
        this(Optional.empty(), position, new Quaternionf(), 1.0f, primitive, true, Optional.empty());
    }

    /** Convenience constructor with explicit rotation. */
    public HaloLayer(Vec3d position, Quaternionf rotation, HaloPrimitive primitive) {
        this(Optional.empty(), position, rotation, 1.0f, primitive, true, Optional.empty());
    }
}
