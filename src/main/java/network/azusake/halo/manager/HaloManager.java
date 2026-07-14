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
 * {@link network.azusake.halo.physics.AnchorFrameCalculator} on the render
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
     * <p>The server acts as a thin authority — it stores the (entity, definitionId)
     * mapping regardless of whether it has the definition JSON locally.  Clients are
     * responsible for providing the actual definition via their own resource packs.
     * This allows players to share custom halo definitions without the server
     * needing to install every resource pack.</p>
     *
     * @param entity the target living entity
     * @param defId  identifier of the halo definition to attach
     */
    public void showHaloOn(LivingEntity entity, Identifier defId) {
        // Accept any identifier — the server is a dumb authority.
        // If a client doesn't have the definition, it will log a warning
        // and skip rendering rather than crashing.
        if (!HaloJsonLoader.getDefinition(defId).isPresent()) {
            HaloMod.LOGGER.info("Halo definition '{}' not installed on server — accepting anyway (clients may have it)", defId);
        }

        HaloInstance instance = new HaloInstance(entity.getUuid(), defId);
        activeHalos.put(entity.getUuid(), instance);

        // Persist to entity NBT so the halo survives world reload
        HaloEntityData.attachHalo(entity, defId);

        // Broadcast to all players on a dedicated server
        MinecraftServer server = entity.getServer();
        if (server != null) {
            network.azusake.halo.network.HaloNetwork.sendHaloAttach(server, entity.getUuid(), defId);
        }

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

        // Broadcast to all players on a dedicated server
        MinecraftServer server = entity.getServer();
        if (server != null && removed != null) {
            network.azusake.halo.network.HaloNetwork.sendHaloRemove(server, entity.getUuid());
        }

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
     * Remove a halo by entity UUID and broadcast the removal to all online players.
     *
     * @param entityUuid the entity UUID
     * @param server     the current Minecraft server (for broadcasting)
     */
    public void removeHalo(UUID entityUuid, MinecraftServer server) {
        HaloInstance removed = activeHalos.remove(entityUuid);
        if (removed != null) {
            network.azusake.halo.network.HaloNetwork.sendHaloRemove(server, entityUuid);
            HaloMod.LOGGER.debug("Halo removed for uuid={}", entityUuid);
        }
    }

    // ------------------------------------------------------------------
    // Client-side write API (called from network receivers)
    // ------------------------------------------------------------------

    /**
     * Directly insert a halo instance into the local map without broadcasting
     * or NBT persistence.  Called by the client network layer when receiving
     * halo state from a dedicated server.
     *
     * @param entityUuid the entity UUID
     * @param defId      the halo definition identifier
     */
    public void putClientHalo(UUID entityUuid, Identifier defId) {
        activeHalos.put(entityUuid, new HaloInstance(entityUuid, defId));
    }

    /**
     * Directly remove a halo instance from the local map.  Called by the
     * client network layer on removal notifications from a dedicated server.
     *
     * @param entityUuid the entity UUID
     */
    public void removeClientHalo(UUID entityUuid) {
        activeHalos.remove(entityUuid);
    }

    /**
     * Replace the entire local halo map with a fresh snapshot from the server.
     * Called on join (full sync) to set the initial state.
     *
     * @param snapshot map of entity UUID → definition ID
     */
    public void replaceAllClientHalos(Map<UUID, Identifier> snapshot) {
        activeHalos.clear();
        for (var entry : snapshot.entrySet()) {
            activeHalos.put(entry.getKey(), new HaloInstance(entry.getKey(), entry.getValue()));
        }
    }

    /**
     * Periodic entity cleanup — removes halos whose attached entity has
     * died or despawned.
     *
     * <p>Called once per server tick from {@code HaloTickHandler}.
     * No physics computation is performed here — halo pose is computed
     * entirely on the render thread by {@code AnchorFrameCalculator}.</p>
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
                // Broadcast removal so all clients drop the dead halo
                network.azusake.halo.network.HaloNetwork.sendHaloRemove(server, uuid);
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
