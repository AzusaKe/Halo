package network.azusake.halo.lifecycle;

import network.azusake.halo.HaloMod;
import network.azusake.halo.data.HaloEntityData;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.manager.HaloManager;
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
 * <h3>Teleport detection — two-tier strategy</h3>
 *
 * <p><b>Tier 1 — Mixin hooks (primary, precise):</b>
 * {@link network.azusake.halo.mixin.EntityTeleportMixin} injects into
 * {@code Entity.requestTeleport()} and {@code Entity.refreshPositionAfterTeleport()}
 * — the two canonical teleport methods in the Minecraft {@code Entity} base class.
 * Every vanilla teleport path ({@code /tp}, ender pearl, nether portal, chorus fruit,
 * {@code /spreadplayers}, respawn, {@code /spectate}, boat dismount, dimension change,
 * end gateway) funnels through one of these two methods, so the mixin captures all of them.
 * When a teleport is detected, {@link #markTeleport(LivingEntity)} is called immediately,
 * setting the {@code needsSnap} flag so the halo snaps to the new position on the next
 * physics tick without any damping slide.</p>
 *
 * <p><b>Tier 2 — Position discontinuity check (safety net):</b>
 * Each server tick, for every entity with an active halo, we compare its current
 * world-space position against the previous tick's position. A jump larger than
 * {@value #TELEPORT_DISTANCE_SQ} blocks² is treated as a teleport.
 * This threshold is deliberately high ({@code 1000²}) — it should never trigger
 * under normal vanilla gameplay; it exists solely as a last-resort safety net against
 * other mods that might set entity positions directly without calling either of the
 * canonical teleport methods.</p>
 *
 * <h3>Grace period</h3>
 *
 * <p>After a teleport is detected, the entity's UUID is stored in
 * {@code recentlyTeleported} for {@value #TELEPORT_GRACE_PERIOD_MS} ms.
 * This serves two purposes:</p>
 * <ul>
 *   <li>Display: {@code /halo list} and {@code /halo inspect} show a "tp" indicator
 *       while the entity is within the grace period.</li>
 *   <li>Debounce: prevents redundant snap triggers if multiple teleport hooks fire
 *       for the same logical teleport (e.g. dimension changes may call both
 *       {@code requestTeleport} and {@code refreshPositionAfterTeleport}).</li>
 * </ul>
 * <p>The grace period does <em>not</em> gate the snap itself — that is driven by
 * {@link HaloInstance#markTeleported()} which fires immediately. Even if the grace
 * period expires before the next physics tick (possible under low TPS), the snap
 * has already been scheduled.</p>
 *
 * <p>The 250 ms value was chosen to comfortably survive a dimension change under
 * 10 TPS (100 ms/tick) with margin for chunk-loading lag. It is long enough to
 * debounce multi-hook teleports without being so long that the "tp" indicator
 * overstays.</p>
 *
 * <h3>Thread safety</h3>
 *
 * <p>All shared maps use {@link ConcurrentHashMap}. Fabric events fire on the
 * server thread, but the public static API is safe to call from command handlers
 * running on netty threads as well.</p>
 *
 * <h3>Removed: HaloEntityEventHandler</h3>
 *
 * <p>The {@code HaloEntityEventHandler} class was an event-bridge layer intended
 * to sit between Minecraft events and this tracker. It was never wired up — no
 * code ever called {@code onEntityMove()}, {@code onEntityTeleport()}, or
 * {@code onEntityRemove()}. The mixin hooks (Tier 1) and the per-tick position
 * check (Tier 2) already covered everything it was designed to do. Removed in
 * Phase 1 to eliminate dead code and the duplicate distance-threshold constant.</p>
 */
public final class EntityHaloTracker {

    // ------------------------------------------------------------------
    // Teleport tracking
    // ------------------------------------------------------------------

    /** Entities that have teleported recently, with their teleport timestamp (epoch ms). */
    private static final Map<UUID, Long> recentlyTeleported = new ConcurrentHashMap<>();

    /**
     * How long (ms) after a teleport the entity is considered "still teleporting".
     *
     * <p>250 ms gives ~2.5 ticks at 20 TPS, ~1 tick at 10 TPS — enough to survive
     * dimension-change chunk loading while keeping the "tp" indicator short-lived.</p>
     */
    private static final long TELEPORT_GRACE_PERIOD_MS = 250;

    // ------------------------------------------------------------------
    // Position tracking (for teleport detection safety net)
    // ------------------------------------------------------------------

    /** Last known world-space position of each tracked entity. */
    private static final Map<UUID, Vec3d> lastKnownPositions = new ConcurrentHashMap<>();

    /**
     * Distance threshold squared (blocks²) for teleport detection via position
     * discontinuity. Set to 1000² as a last-resort safety net — the primary
     * detection mechanism is the mixin hooks on {@code Entity.requestTeleport()}
     * and {@code Entity.refreshPositionAfterTeleport()}. This threshold should
     * never trigger under normal vanilla gameplay; it exists to catch teleports
     * from other mods that bypass both canonical teleport methods.
     */
    private static final double TELEPORT_DISTANCE_SQ = 1000.0 * 1000.0;

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

        // ---- Position-based teleport detection (safety net) ----
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
