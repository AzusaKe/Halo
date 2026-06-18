package network.azusake.halo.shape;

import network.azusake.halo.data.OrientationMode;

import java.util.List;

/**
 * The visual model of a halo — a collection of {@link HaloLayer}s with
 * a shared {@link OrientationMode}.
 *
 * <p>This replaces the old {@code HaloShape} sealed hierarchy.  The
 * {@code orientationMode} controls whether rotation around the normal
 * axis follows the player's horizontal look direction ({@code LOCKED})
 * or is independently damped ({@code FREE}).</p>
 *
 * <p>In both modes the halo normal (definition -Y) always points toward
 * the entity's head — the halo's "bottom" faces the player.</p>
 *
 * @param orientationMode  how the spin around the normal axis behaves
 * @param layers           ordered layers (first = behind, last = front)
 */
public record HaloModel(
    OrientationMode orientationMode,
    List<HaloLayer> layers
) {
    /** A model with no layers (useful as a default / fallback). */
    public static final HaloModel EMPTY = new HaloModel(OrientationMode.LOCKED, List.of());
}
