package network.azusake.halo.render;

import network.azusake.halo.client.HaloLocalManager;
import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.manager.HaloManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side halo visibility manager.
 *
 * <p>In single-player (integrated server) the client shares the JVM with the
 * server, so we can read {@link HaloManager} directly.  In LOCAL phase
 * (server without the mod), data from {@link HaloLocalManager} is merged
 * into the render pipeline.  In MULTIPLAYER phase (server with the mod),
 * data arrives via the networking layer.</p>
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
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return Collections.emptyList();
        }

        Collection<HaloInstance> allInstances = HaloManager.getInstance().getAllInstances();

        // LOCAL phase: merge HaloLocalManager data into the render pipeline.
        // In MULTIPLAYER phase or singleplayer, shouldIntercept() returns false
        // and we only read from HaloManager (no overhead).
        if (HaloPhaseTracker.getInstance().shouldIntercept()) {
            String serverKey = getCurrentServerKey(client);
            if (serverKey != null) {
                List<HaloInstance> merged = new ArrayList<>(allInstances);
                for (UUID uuid : HaloLocalManager.getInstance().getHalosForServer(serverKey)) {
                    boolean exists = false;
                    for (HaloInstance inst : allInstances) {
                        if (inst.getEntityUuid().equals(uuid)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        HaloLocalManager.getInstance().getHalo(serverKey, uuid).ifPresent(defId -> {
                            // Put into HaloManager for AnchorFrameCalculator
                            // damping and animation state.
                            HaloManager.getInstance().putClientHalo(uuid, defId);
                            merged.add(new HaloInstance(uuid, defId));
                        });
                    }
                }
                allInstances = merged;
            }
        }

        if (allInstances.isEmpty()) {
            return Collections.emptyList();
        }

        // Periodically rebuild the entity cache (every 2 seconds)
        long now = System.currentTimeMillis();
        if (now - lastCacheRebuild > 2000) {
            rebuildEntityCache(client);
            lastCacheRebuild = now;
        }

        Vec3 camPos = camera.position();
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
            if (!entity.isAlive() || !entity.getUUID().equals(instance.getEntityUuid())) {
                entityCache.remove(instance.getEntityUuid());
                continue;
            }

            if (entity.position().distanceToSqr(camPos) <= RENDER_DIST_SQ) {
                visible.add(instance);
            }
        }

        return visible;
    }

    // ------------------------------------------------------------------
    // Entity cache
    // ------------------------------------------------------------------

    private void rebuildEntityCache(Minecraft client) {
        entityCache.clear();
        if (client.level == null) return;

        for (Entity e : client.level.entitiesForRendering()) {
            if (e instanceof LivingEntity living) {
                entityCache.put(e.getUUID(), living);
            }
        }
    }

    private static LivingEntity findEntityInWorld(Minecraft client, UUID uuid) {
        if (client.level == null) return null;
        for (Entity e : client.level.entitiesForRendering()) {
            if (e.getUUID().equals(uuid) && e instanceof LivingEntity living) {
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

    // ------------------------------------------------------------------
    // Per-tick entity state cache
    // ------------------------------------------------------------------

    /**
     * Update cached entity state (invisible, sleeping) for all active halo
     * instances.  Called once per client tick from the render thread so that
     * the per-frame render path reads cached values instead of querying the
     * entity every frame.
     */
    public void updateEntityStateCache() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        for (HaloInstance instance : HaloManager.getInstance().getAllInstances()) {
            if (!instance.isActive()) continue;

            LivingEntity entity = entityCache.get(instance.getEntityUuid());
            if (entity == null) {
                entity = findEntityInWorld(client, instance.getEntityUuid());
                if (entity != null) {
                    entityCache.put(instance.getEntityUuid(), entity);
                } else {
                    continue;
                }
            }

            instance.setEntityInvisible(entity.isInvisible());
            instance.setEntitySleeping(entity.isSleeping());
        }
    }

    /**
     * Derive a server key from the current connection for local-halo lookup.
     *
     * @return {@code "host:port"} or {@code null} if not connected
     */
    private static String getCurrentServerKey(Minecraft client) {
        if (client.getConnection() != null && client.getConnection().getConnection() != null) {
            return HaloLocalManager.serverKeyFromAddress(
                client.getConnection().getConnection().getRemoteAddress());
        }
        return null;
    }
}
