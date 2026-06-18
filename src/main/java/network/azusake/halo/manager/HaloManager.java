package network.azusake.halo.manager;

import network.azusake.halo.HaloMod;
import network.azusake.halo.config.HaloConfig;
import network.azusake.halo.data.HaloDefinition;
import network.azusake.halo.data.HaloEntityData;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.json.HaloJsonLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global singleton that owns the runtime halo lifecycle.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Create / destroy {@link HaloInstance} objects via commands.</li>
 *   <li>Periodic entity cleanup via {@link #tickAll(MinecraftServer)} —
 *       removes halos whose attached entity has died or despawned.</li>
 *   <li>Hold the shared {@link HaloConfig} that can be tuned in-game.</li>
 * </ul>
 *
 * <p>All pose computation and damping physics have moved to
 * {@link network.azusake.halo.physics.HaloPoseCalculator} on the render
 * thread.  The server tick no longer performs any damping — halos are a
 * visual-only effect and do not need server-authoritative physics.</p>
 *
 * <p>Thread-safety: the active halo map uses {@link ConcurrentHashMap} so
 * command handlers (netty threads) and the server tick thread can both
 * access it safely.</p>
 */
public final class HaloManager {

    private static final HaloManager INSTANCE = new HaloManager();

    private final HaloConfig config = new HaloConfig();
    private final Map<UUID, HaloInstance> activeHalos = new ConcurrentHashMap<>();
    private final Map<UUID, LivingEntity> trackedEntities = new ConcurrentHashMap<>();

    private HaloManager() {
        // singleton — use getInstance()
    }

    /** Return the global singleton. */
    public static HaloManager getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Spawn (or replace) a halo on the given entity using the named definition.
     *
     * @param entity the target living entity
     * @param defId  identifier of a loaded {@link HaloDefinition}
     */
    public void showHaloOn(LivingEntity entity, Identifier defId) {
        Optional<HaloDefinition> optDef = HaloJsonLoader.getDefinition(defId);
        if (optDef.isEmpty()) {
            HaloMod.LOGGER.warn("Unknown halo definition: {}", defId);
            return;
        }

        HaloInstance instance = new HaloInstance(entity.getUuid(), defId);
        activeHalos.put(entity.getUuid(), instance);

        // Persist to entity NBT so the halo survives world reload
        HaloEntityData.attachHalo(entity, defId);

        HaloMod.LOGGER.debug("Halo '{}' shown on entity {} (uuid={})", defId, entity.getName().getString(), entity.getUuid());
    }

    /**
     * Remove the halo from a living entity (no-op if none was attached).
     *
     * @param entity the target living entity
     */
    public void hideHaloOn(LivingEntity entity) {
        HaloInstance removed = activeHalos.remove(entity.getUuid());
        HaloEntityData.removeHalo(entity);
        if (removed != null) {
            HaloMod.LOGGER.debug("Halo hidden on entity {} (uuid={})", entity.getName().getString(), entity.getUuid());
        }
    }

    /**
     * Remove a halo by entity UUID (e.g. on entity unload / player disconnect).
     *
     * @param entityUuid the entity UUID
     */
    public void removeHalo(UUID entityUuid) {
        HaloInstance removed = activeHalos.remove(entityUuid);
        if (removed != null) {
            HaloMod.LOGGER.debug("Halo removed for uuid={}", entityUuid);
        }
    }

    /**
     * Periodic entity cleanup — removes halos whose attached entity has
     * died or despawned.
     *
     * <p>Called once per server tick from {@code HaloTickHandler}.
     * No physics computation is performed here — halo pose is computed
     * entirely on the render thread by {@code HaloPoseCalculator}.</p>
     *
     * @param server the current Minecraft server instance
     */
    public void tickAll(MinecraftServer server) {
        if (activeHalos.isEmpty()) {
            return;
        }

        var iterator = activeHalos.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            UUID uuid = entry.getKey();

            LivingEntity entity = findEntityByUuid(server, uuid);
            if (entity == null || !entity.isAlive()) {
                iterator.remove();
                HaloMod.LOGGER.debug("Halo cleaned up: entity uuid={} is gone or dead", uuid);
            }
        }
    }

    /**
     * Return the shared runtime configuration (mutable — changes take effect immediately).
     */
    public HaloConfig getConfig() {
        return config;
    }

    /**
     * Look up the halo instance attached to an entity, if any.
     *
     * @param entityUuid the entity UUID
     * @return the halo instance, or {@code null}
     */
    public HaloInstance getHaloInstance(UUID entityUuid) {
        return activeHalos.get(entityUuid);
    }

    /**
     * Return an unmodifiable view of the active halo map.
     */
    public Map<UUID, HaloInstance> getActiveHalos() {
        return Map.copyOf(activeHalos);
    }

    /**
     * How many halos are currently active.
     */
    public int getActiveCount() {
        return activeHalos.size();
    }

    // ------------------------------------------------------------------
    // Entity tracking
    // ------------------------------------------------------------------

    /**
     * Register an entity for ongoing position tracking (teleport detection).
     *
     * @param entity the living entity to track
     */
    public void registerEntity(LivingEntity entity) {
        trackedEntities.put(entity.getUuid(), entity);
    }

    /**
     * Unregister an entity from position tracking and remove its halo.
     *
     * @param uuid the entity UUID
     */
    public void unregisterEntity(UUID uuid) {
        trackedEntities.remove(uuid);
        activeHalos.remove(uuid);
    }

    /**
     * Return all active halo instances (no defensive copy — for internal iteration).
     */
    public Collection<HaloInstance> getAllInstances() {
        return activeHalos.values();
    }

    /**
     * Persist halo state on world unload / server stop.
     * Syncs current assignments to world persistent state so they survive a restart.
     *
     * @param server the current Minecraft server instance
     */
    public void cleanup(MinecraftServer server) {
        var world = server.getOverworld();
        if (world != null) {
            network.azusake.halo.lifecycle.HaloWorldSaveData data =
                network.azusake.halo.lifecycle.HaloWorldSaveData.get(world);
            data.syncFromManager();
        }
        HaloMod.LOGGER.debug("HaloManager: cleanup complete — {} active halo(s) synced", activeHalos.size());
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Find a living entity by UUID across all server worlds.
     *
     * @param server the Minecraft server instance
     * @param uuid   the entity UUID to look up
     * @return the entity, or {@code null} if not found
     */
    private static LivingEntity findEntityByUuid(MinecraftServer server, UUID uuid) {
        for (var world : server.getWorlds()) {
            var entity = world.getEntity(uuid);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }
}
