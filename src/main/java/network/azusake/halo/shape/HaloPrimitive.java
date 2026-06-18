package network.azusake.halo.shape;

/**
 * A renderable primitive inside a {@link HaloLayer}.
 *
 * <p>Sealed for now to {@link BillboardPrimitive}; future phases will
 * add {@code Model3dPrimitive} for glTF/Blockbench model support.</p>
 */
public sealed interface HaloPrimitive permits BillboardPrimitive {
}
