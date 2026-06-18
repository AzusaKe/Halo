package network.azusake.halo.lifecycle;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Static event bridge that connects Minecraft entity lifecycle events
 * (teleport, death, movement) to the {@link EntityHaloTracker}.
 *
 * <p>These methods are designed to be called from:</p>
 * <ul>
 *   <li>Fabric API event callbacks (no mixin required for basic events)</li>
 *   <li>Mixin injection points (when Fabric events don't cover the trigger)</li>
 *   <li>Command handlers that cause entity teleports</li>
 * </ul>
 *
 * <p>All methods are safe to call from any thread — the tracker uses
 * concurrent data structures internally.</p>
 */
public final class HaloEntityEventHandler {

    /** Distance threshold (blocks) beyond which movement is treated as a teleport. */
    private static final double TELEPORT_DISTANCE_THRESHOLD = 20.0;

    private HaloEntityEventHandler() {
        // utility class — no instances
    }

    /**
     * Notify the tracker that an entity has teleported (dimension change,
     * {@code /tp} command, portal, etc.).
     *
     * @param entity the living entity that teleported
     */
    public static void onEntityTeleport(LivingEntity entity) {
        EntityHaloTracker.markTeleport(entity);
    }

    /**
     * Notify the tracker that an entity has been removed (death, despawn,
     * player disconnect, etc.).
     *
     * @param entity the living entity being removed
     */
    public static void onEntityRemove(LivingEntity entity) {
        EntityHaloTracker.cleanup(entity);
    }

    /**
     * Inspect entity movement to detect teleports via position discontinuity.
     *
     * <p>Compares the entity's current position against the last known position
     * stored in the tracker.  A jump larger than {@value #TELEPORT_DISTANCE_THRESHOLD}
     * blocks in a single tick is treated as a teleport and triggers a damping snap.</p>
     *
     * <p>This is a lightweight guard — most movement will fall well below the
     * threshold and return immediately.</p>
     *
     * @param entity     the living entity that moved
     * @param currentPos the entity's current world-space position
     */
    public static void onEntityMove(LivingEntity entity, Vec3d currentPos) {
        Vec3d lastPos = EntityHaloTracker.getLastKnownPosition(entity.getUuid());

        if (lastPos != null && currentPos.squaredDistanceTo(lastPos) > TELEPORT_DISTANCE_THRESHOLD * TELEPORT_DISTANCE_THRESHOLD) {
            EntityHaloTracker.markTeleport(entity);
        }

        // Always update the stored position so the next tick can compare
        EntityHaloTracker.updatePosition(entity.getUuid(), currentPos);
    }
}
