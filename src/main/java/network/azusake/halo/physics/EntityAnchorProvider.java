package network.azusake.halo.physics;

import net.minecraft.entity.LivingEntity;

/**
 * Provides a world-space {@link HeadAnchor} for a given {@link LivingEntity}
 * each render frame.
 *
 * <p>Implementations are responsible for entity-type-specific and
 * pose-specific head pivot/center logic.  This interface is the extension
 * point for adding non-player entity support in future phases.</p>
 */
@FunctionalInterface
public interface EntityAnchorProvider {

    /**
     * Compute the world-space head anchor for this entity at this tick.
     *
     * @param entity    the living entity (never null, guaranteed alive when called)
     * @param tickDelta partial-tick progress (0.0–1.0) for entity interpolation
     * @return a fully resolved {@link HeadAnchor}
     */
    HeadAnchor resolve(LivingEntity entity, float tickDelta);
}
