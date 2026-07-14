package network.azusake.halo.render;

import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.manager.HaloManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side halo visibility manager.
 *
 * <p>In single-player (integrated server) the client shares the JVM with the
 * server, so we can read {@link HaloManager} directly.  For dedicated-server
 * support a networking layer will be needed in a future phase.</p>
 */
public final class HaloClientManager {

    private static final HaloClientManager INSTANCE = new HaloClientManager();

    /** Render-distance cutoff (chunks).  16 chunks = 256 blocks. */
    private static final int RENDER_DISTANCE_CHUNKS = 16;
    private static final double RENDER_DIST_SQ =
        (double) RENDER_DISTANCE_CHUNKS * RENDER_DISTANCE_CHUNKS * 16.0 * 16.0;

    /** Cached entity references for fast lookup. */
    private final Map<UUID, LivingEntity> entityCache = new ConcurrentHashMap<>();

    /** Timestamp of last entity-cache refresh. */
    private long lastCacheRebuild;

    private HaloClientManager() {
        // singleton
    }

    public static HaloClientManager getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Visibility query
    // ------------------------------------------------------------------

    /**
     * Return every halo instance whose attached entity is within render
     * distance of the camera.  Called once per frame from the render thread.
     */
    public Collection<HaloInstance> getVisibleHalos(Camera camera) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return Collections.emptyList();
        }

        Collection<HaloInstance> allInstances = HaloManager.getInstance().getAllInstances();
        if (allInstances.isEmpty()) {
            return Collections.emptyList();
        }

        // Periodically rebuild the entity cache (every 2 seconds)
        long now = System.currentTimeMillis();
        if (now - lastCacheRebuild > 2000) {
            rebuildEntityCache(client);
            lastCacheRebuild = now;
        }

        Vec3d camPos = camera.getPos();
        List<HaloInstance> visible = new ArrayList<>();

        for (HaloInstance instance : allInstances) {
            if (!instance.isActive()) {
                continue;
            }

            LivingEntity entity = entityCache.get(instance.getEntityUuid());
            if (entity == null) {
                // Cache miss — try a fresh lookup
                entity = findEntityInWorld(client, instance.getEntityUuid());
                if (entity != null) {
                    entityCache.put(instance.getEntityUuid(), entity);
                } else {
                    continue;
                }
            }

            // Verify the cached entity is still valid
            if (!entity.isAlive() || !entity.getUuid().equals(instance.getEntityUuid())) {
                entityCache.remove(instance.getEntityUuid());
                continue;
            }

            if (entity.getPos().squaredDistanceTo(camPos) <= RENDER_DIST_SQ) {
                visible.add(instance);
            }
        }

        return visible;
    }

    // ------------------------------------------------------------------
    // Entity cache
    // ------------------------------------------------------------------

    private void rebuildEntityCache(MinecraftClient client) {
        entityCache.clear();
        if (client.world == null) return;

        for (Entity e : client.world.getEntities()) {
            if (e instanceof LivingEntity living) {
                entityCache.put(e.getUuid(), living);
            }
        }
    }

    private static LivingEntity findEntityInWorld(MinecraftClient client, UUID uuid) {
        if (client.world == null) return null;
        for (Entity e : client.world.getEntities()) {
            if (e.getUuid().equals(uuid) && e instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    /**
     * Remove a cached entity reference (called when an entity is unloaded).
     */
    public void onEntityUnloaded(UUID uuid) {
        entityCache.remove(uuid);
    }

    /**
     * Clear the entity cache entirely (called on world unload).
     */
    public void clearCache() {
        entityCache.clear();
    }
}
