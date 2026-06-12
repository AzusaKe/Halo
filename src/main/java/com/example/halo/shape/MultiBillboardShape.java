package com.example.halo.shape;

import java.util.List;

/**
 * A halo shape composed of multiple billboard layers rendered back-to-front.
 * Intended for future use (complex halos with multiple rings or glow passes).
 *
 * @param layers ordered list of billboard layers
 */
public record MultiBillboardShape(
    List<BillboardShape> layers
) implements HaloShape {
}
