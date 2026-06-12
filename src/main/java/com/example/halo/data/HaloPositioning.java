package com.example.halo.data;

import net.minecraft.util.math.Vec3d;

/**
 * Static offsets for halo placement relative to an entity's head anchor.
 *
 * @param offset position offset [x, y, z] from the head anchor point
 * @param scale  uniform scale multiplier applied to the halo
 */
public record HaloPositioning(
    Vec3d offset,
    double scale
) {}
