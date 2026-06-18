package network.azusake.halo.shape;

/**
 * Sealed hierarchy for halo visual shapes.
 * Every shape type must reside in this package (Java sealed-class restriction
 * for unnamed modules).
 *
 * <p>Current subtypes:
 * <ul>
 *   <li>{@link BillboardShape} — single textured quad</li>
 *   <li>{@link MultiBillboardShape} — multiple stacked billboard layers</li>
 * </ul>
 */
public sealed interface HaloShape permits BillboardShape, MultiBillboardShape {
}
