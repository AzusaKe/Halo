package network.azusake.halo.data;

import network.azusake.halo.animation.LayerAnimation;
import network.azusake.halo.animation.StartupAnimationConfig;
import network.azusake.halo.shape.HaloModel;
import java.util.Optional;
import net.minecraft.resources.Identifier;

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
 * @param displayInInvisible when false (default), the halo stops rendering while the entity is invisible
 * @param schemaVersion    the schema version this definition was written for (determines parsing behavior)
 * @param startupAnimation optional startup transition animation config
 * @param shutdownAnimation optional shutdown transition animation config
 */
public record HaloDefinition(
    Identifier id,
    HaloModel model,
    Optional<LayerAnimation> animation,
    HaloPositioning positioning,
    HaloDampingConfig damping,
    boolean hideOnSleep,
    boolean displayInInvisible,
    SchemaVersion schemaVersion,
    Optional<StartupAnimationConfig> startupAnimation,
    Optional<StartupAnimationConfig> shutdownAnimation
) {}