package network.azusake.halo.shape;

import network.azusake.halo.data.OrientationMode;
import org.joml.Quaternionf;

import java.util.List;

/**
 * The visual model of a halo — a collection of {@link HaloLayer}s with
 * a shared {@link OrientationMode}.
 *
 * <p>This replaces the old {@code HaloShape} sealed hierarchy.  The
 * {@code orientationMode} controls whether rotation around the normal
 * axis follows the player's horizontal look direction ({@code LOCKED}),
 * is independently damped ({@code FREE}), or is slaved to the head's
 * full 3-D orientation ({@code SYNC}).</p>
 *
 * <p>In all modes the halo normal (definition -Y) always points toward
 * the entity's head — the halo's "bottom" faces the player.</p>
 *
 * @param orientationMode  how the spin around the normal axis behaves
 * @param layers           ordered layers (first = behind, last = front)
 * @param syncOffset       configurable angular offset for {@code SYNC} mode
 *                         (Euler YXZ, applied on the first frame).  Identity
 *                         for non-SYNC modes.
 */
public record HaloModel(
    OrientationMode orientationMode,
    List<HaloLayer> layers,
    Quaternionf syncOffset
) {
    /** A model with no layers (useful as a default / fallback). */
    public static final HaloModel EMPTY = new HaloModel(OrientationMode.LOCKED, List.of(), new Quaternionf());

    /** Convenience constructor for models that don't use SYNC mode. */
    public HaloModel(OrientationMode orientationMode, List<HaloLayer> layers) {
        this(orientationMode, layers, new Quaternionf());
    }
}
