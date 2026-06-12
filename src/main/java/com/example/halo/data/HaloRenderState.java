package com.example.halo.data;

import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.util.UUID;

/**
 * Client-side interpolated render state for one halo, computed each frame
 * by lerping between {@link HaloInstance#getPrevRelativePosition()} and
 * {@link HaloInstance#getRelativePosition()} using {@code tickDelta}.
 *
 * @param entityUuid            the entity this halo is attached to
 * @param interpolatedPosition  lerped position for this frame
 * @param interpolatedRotation  slerped rotation for this frame
 * @param definition            the parsed halo definition (shape, animation, etc.)
 * @param tickDelta             partial-tick progress used for interpolation
 */
public record HaloRenderState(
    UUID entityUuid,
    Vec3d interpolatedPosition,
    Quaternionf interpolatedRotation,
    HaloDefinition definition,
    float tickDelta
) {}
