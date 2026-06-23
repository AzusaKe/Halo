package network.azusake.halo.data;

import network.azusake.halo.animation.LayerAnimation;
import network.azusake.halo.shape.HaloModel;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * A fully parsed halo definition loaded from a JSON resource file.
 * Immutable — all fields are final via the record contract.
 *
 * @param id          unique identifier for this definition (matches the resource path)
 * @param model       visual model with layers and orientation mode
 * @param animation   optional whole-body visual animation (offset + rotation over time)
 * @param positioning static offset and scale applied to the halo
 * @param damping         physics damping / interpolation parameters
 * @param hideOnSleep      when true, the halo stops rendering while the entity is sleeping
 */
public record HaloDefinition(
    Identifier id,
    HaloModel model,
    Optional<LayerAnimation> animation,
    HaloPositioning positioning,
    HaloDampingConfig damping,
    boolean hideOnSleep
) {}