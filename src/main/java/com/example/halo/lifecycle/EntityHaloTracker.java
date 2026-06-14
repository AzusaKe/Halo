package com.example.halo.lifecycle;

import com.example.halo.HaloMod;
import com.example.halo.data.HaloEntityData;
import com.example.halo.data.HaloInstance;
import com.example.halo.manager.HaloManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-entity halo lifecycle tracker.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Detect teleports (distance &gt; 20 blocks in one tick) and trigger damping snap.</li>
 *   <li>Clean up halos when entities die or unload.</li>
 *   <li>Restore halos from entity persistent NBT when an entity loads.</li>
 *   <li>Maintain a grace-period set so short-lived teleport markers don't leak.</li>
 * </ul>
 *
 * <p>Thread-safety: all shared maps use {@link ConcurrentHashMap}.  Fabric
 * events fire on the server thread, but the public static API is safe to
 * call from command handlers running on netty threads as well.</p>
 */
public final class EntityHaloTracker {

    // ------------------------------------------------------------------
    // Teleport tracking
    // ------------------------------------------------------------------

    /** Entities that have teleported recently, with their teleport timestamp (epoch ms). */
    private static final Map<UUID, Long> recentlyTeleported = new ConcurrentHashMap<>();

    /** How long (ms) after a teleport the entity is considered "still teleporting". */
    private static final long TELEPORT_GRACE_PERIOD_MS = 100;

    // ------------------------------------------------------------------
    // Position tracking (for teleport detection)
    // ------------------------------------------------------------------

    /** Last known world-space position of each tracked entity. */
    private static final Map<UUID, Vec3d> lastKnownPositions = new ConcurrentHashMap<>();

    /** Distance threshold squared (blocks²) for teleport detection. */
    private static final double TELEPORT_DISTANCE_SQ = 20.0 * 20.0;

    // ------------------------------------------------------------------
    // Registration flag (idempotent)
    // ------------------------------------------------------------------

    private static volatile boolean registered = false;

    /** When true, emit chat messages on teleport detection and snap correction. */
    private static volatile boolean debugMode = false;

    /** Server reference, refreshed each tick. Used by debug chat output. */
    private static volatile MinecraftServer currentServer;

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Enable or disable debug logging for teleport / snap events.
     * Messages appear in the in-game chat, not just the console.
     */
    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
        HaloMod.LOGGER.info("EntityHaloTracker: debug mode {}", enabled ? "ON" : "OFF");
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    /**
     * Returns the current server reference, or null if not yet set.
     */
    public static MinecraftServer getServer() {
        return currentServer;
    }

    /**
     * Refresh the server reference. Called by physics tick handler
     * (which runs before this tracker) so snap debug messages can reach chat.
     */
    public static void setCurrentServer(MinecraftServer server) {
        currentServer = server;
    }

    /**
     * Register all Fabric event listeners for entity lifecycle tracking.
     *
     * <p>Idempotent — subsequent calls are no-ops.</p>
     */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        // ---- Entity death → cleanup ----
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            cleanup(entity);
        });

        // ---- Entity load → restore halo from NBT ----
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity living) {
                restoreFromNbt(living);
            }
        });

        // ---- Server tick → expired teleport cleanup + position check ----
        ServerTickEvents.END_SERVER_TICK.register(EntityHaloTracker::onEndTick);

        HaloMod.LOGGER.info("EntityHaloTracker: registered lifecycle event handlers");
    }

    /**
     * Mark an entity as having teleported so its halo snaps on the next physics tick.
     *
     * @param entity the living entity that teleported
     */
    public static void markTeleport(LivingEntity entity) {
        UUID uuid = entity.getUuid();
        recentlyTeleported.put(uuid, System.currentTimeMillis());

        HaloInstance instance = HaloManager.getInstance().getHaloInstance(uuid);
        if (instance != null) {
            instance.markTeleported();
            if (debugMode && currentServer != null) {
                Vec3d pos = entity.getPos();
                var msg = Text.literal(
                    String.format("§e[HaloDebug] §fTELEPORT §edetected | §7%s §fpos=(§7%.1f, %.1f, %.1f§f) §a-> snap",
                        entity.getName().getString(), pos.x, pos.y, pos.z));
                currentServer.getPlayerManager().broadcast(msg, false);
            }
        }
    }

    /**
     * Check whether an entity is within the teleport grace period.
     *
     * @param entityUuid the entity UUID
     * @return {@code true} if the entity teleported within the last
     *         {@value #TELEPORT_GRACE_PERIOD_MS} ms
     */
    public static boolean isTeleporting(UUID entityUuid) {
        return recentlyTeleported.containsKey(entityUuid);
    }

    /**
     * Remove the halo from an entity and clear all tracking state.
     *
     * @param entity the living entity to clean up
     */
    public static void cleanup(LivingEntity entity) {
        UUID uuid = entity.getUuid();
        HaloManager.getInstance().hideHaloOn(entity);
        HaloEntityData.removeHalo(entity);
        recentlyTeleported.remove(uuid);
        lastKnownPositions.remove(uuid);
    }

    // ------------------------------------------------------------------
    // Position tracking (package-private for HaloEntityEventHandler)
    // ------------------------------------------------------------------

    /**
     * Update the stored position for an entity (called from the movement hook).
     *
     * @param entityUuid the entity UUID
     * @param pos        current world-space position
     */
    static void updatePosition(UUID entityUuid, Vec3d pos) {
        lastKnownPositions.put(entityUuid, pos);
    }

    /**
     * Retrieve the last known position for an entity.
     *
     * @param entityUuid the entity UUID
     * @return the last stored position, or {@code null} if never recorded
     */
    static Vec3d getLastKnownPosition(UUID entityUuid) {
        return lastKnownPositions.get(entityUuid);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Server-tick callback: purge expired teleport markers and run
     * position-based teleport detection on all active halo entities.
     */
    private static void onEndTick(MinecraftServer server) {
        currentServer = server;

        // ---- Expire old teleport markers ----
        long now = System.currentTimeMillis();
        recentlyTeleported.values().removeIf(timestamp ->
            now - timestamp > TELEPORT_GRACE_PERIOD_MS
        );

        // ---- Position-based teleport detection ----
        // Only check entities that currently have an active halo
        Map<UUID, HaloInstance> activeHalos = HaloManager.getInstance().getActiveHalos();
        if (activeHalos.isEmpty()) {
            return;
        }

        for (UUID uuid : activeHalos.keySet()) {
            LivingEntity entity = findEntity(server, uuid);
            if (entity == null) {
                continue;
            }

            Vec3d currentPos = entity.getPos();
            Vec3d lastPos = lastKnownPositions.get(uuid);

            if (lastPos != null && currentPos.squaredDistanceTo(lastPos) > TELEPORT_DISTANCE_SQ) {
                markTeleport(entity);
            }

            lastKnownPositions.put(uuid, currentPos);
        }
    }

    /**
     * Restore a halo from entity persistent NBT when an entity loads.
     *
     * @param entity the entity that just loaded
     */
    private static void restoreFromNbt(LivingEntity entity) {
        if (!HaloEntityData.hasHalo(entity)) {
            return;
        }

        Identifier defId = HaloEntityData.getHaloDefinition(entity);
        if (defId == null) {
            HaloMod.LOGGER.warn("EntityHaloTracker: entity {} has HaloInstance NBT but no valid Definition — removing",
                entity.getUuid());
            HaloEntityData.removeHalo(entity);
            return;
        }

        // Re-create the halo instance via HaloManager
        HaloManager.getInstance().showHaloOn(entity, defId);
        HaloManager.getInstance().registerEntity(entity);

        // Mark as teleported so the halo snaps to the entity immediately
        // rather than sliding in from the world origin
        HaloInstance instance = HaloManager.getInstance().getHaloInstance(entity.getUuid());
        if (instance != null) {
            instance.markTeleported();
        }

        HaloMod.LOGGER.debug("EntityHaloTracker: restored halo '{}' on entity {} from NBT",
            defId, entity.getUuid());
    }

    /**
     * Find a living entity by UUID across all server worlds.
     */
    private static LivingEntity findEntity(MinecraftServer server, UUID uuid) {
        for (var world : server.getWorlds()) {
            var entity = world.getEntity(uuid);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                return living;
            }
        }
        return null;
    }
}
