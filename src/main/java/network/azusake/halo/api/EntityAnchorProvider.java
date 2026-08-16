package network.azusake.halo.api;

import net.minecraft.world.entity.LivingEntity;

/**
 * Provides a world-space {@link HeadAnchor} for a given {@link LivingEntity}.
 *
 * <p>Implementations are called once per render frame on the render thread
 * (not per tick).  Each provider is responsible for interpolating between the
 * previous and current tick state using {@code tickDelta}.  The returned
 * {@link HeadAnchor} carries the full 6 DOF: head center position (world
 * coordinates) plus yaw/pitch/roll orientation (degrees, Minecraft
 * convention).</p>
 *
 * <p>This is the public extension point for other mods: register an
 * implementation via {@link EntityAnchorProviderRegistry}, typically inside
 * {@link AnchorProviderSetupEvent}.  A provider decides itself whether the
 * head orientation comes from the camera or from pure animation.</p>
 *
 * <p><strong>Frame-ordering contract:</strong> Halo may invoke {@link #resolve}
 * before the current frame's camera/head orientation has been computed by the
 * provider's animation or camera system.  The provider must therefore cache
 * the previous frame's {@link HeadAnchor} and return it whenever the
 * current-frame input is not ready yet.</p>
 *
 * <p><strong>Never return {@code null}</strong> — Halo treats a {@code null}
 * result as a provider bug, logs an error and falls back to
 * {@link FallbackAnchorProvider}.  All {@link HeadAnchor} components must be
 * finite numbers.</p>
 */
@FunctionalInterface
public interface EntityAnchorProvider {

    /**
     * Compute the world-space head anchor for this entity at this render frame.
     *
     * @param entity    the living entity (never null, guaranteed alive when called)
     * @param tickDelta partial-tick progress (0.0–1.0; may exceed 1.0 at low
     *                  frame rates) used to interpolate between tick states
     * @return a fully resolved {@link HeadAnchor}
     */
    HeadAnchor resolve(LivingEntity entity, float tickDelta);
}
