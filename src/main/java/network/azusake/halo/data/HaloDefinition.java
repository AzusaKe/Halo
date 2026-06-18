package network.azusake.halo.data;

import network.azusake.halo.animation.HaloAnimation;
import network.azusake.halo.shape.HaloShape;
import net.minecraft.util.Identifier;

/**
 * A fully parsed halo definition loaded from a JSON resource file.
 * Immutable — all fields are final via the record contract.
 *
 * @param id          unique identifier for this definition (matches the resource path)
 * @param shape       visual shape configuration (billboard, multi-billboard, etc.)
 * @param animation   animation curves driving position and rotation over time
 * @param positioning static offset and scale applied to the halo
 * @param damping     physics damping / interpolation parameters
 */
public record HaloDefinition(
    Identifier id,
    HaloShape shape,
    HaloAnimation animation,
    HaloPositioning positioning,
    HaloDampingConfig damping
) {}
