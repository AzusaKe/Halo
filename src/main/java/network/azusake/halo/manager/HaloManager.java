package network.azusake.halo.manager;

import network.azusake.halo.HaloMod;
import network.azusake.halo.config.HaloConfig;
import network.azusake.halo.data.HaloEntityData;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.lifecycle.HaloWorldSaveData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
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

        HaloInstance instance = new HaloInstance(entity.getUUID(), defId);
        activeHalos.put(entity.getUUID(), instance);

        // Persist to entity NBT so the halo survives world reload
        HaloEntityData.attachHalo(entity, defId);

        // Record ownership in the world-level persistent state — the single
        // authoritative record of who owns which halo.  Only /halo show and
        // /halo hide may modify it; death/respawn/dimension-travel only read it.
        MinecraftServer server = entity.level().getServer();
        if (server != null) {
            HaloWorldSaveData.get(server.overworld()).set(entity.getUUID(), defId);

            // Broadcast to all players on a dedicated server
            network.azusake.halo.network.HaloNetwork.sendHaloAttach(server, entity.getUUID(), defId);
        }

        HaloMod.LOGGER.debug("Halo '{}' shown on entity {} (uuid={})", defId, entity.getName().getString(), entity.getUUID());
    }

    /**
     * Remove the halo from a living entity (no-op if none was attached).
     * Removes immediately from the active map and clears persisted NBT.
     * Clients handle shutdown animations via their own state machine.
     *
     * @param entity the target living entity
     */
    public void hideHaloOn(LivingEntity entity) {
        HaloInstance instance = activeHalos.remove(entity.getUUID());
        if (instance == null) return;

        HaloEntityData.removeHalo(entity);

        MinecraftServer server = entity.level().getServer();
        if (server != null) {
            // Revoke ownership in the world-level persistent state
            HaloWorldSaveData.get(server.overworld()).remove(entity.getUUID());
            network.azusake.halo.network.HaloNetwork.sendHaloRemove(server, entity.getUUID(), instance.getDefinitionId());
        }

        HaloMod.LOGGER.debug("Halo hidden on entity {} (uuid={})", entity.getName().getString(), entity.getUUID());
    }

    /**
     * Silently remove a halo by entity UUID — clears only the runtime map,
     * without broadcasting a removal to clients and without touching the
     * world-level ownership record.
     *
     * <p>Used by entity unload, disconnect, and death cleanup.  Silence matters
     * here: a broadcast would make clients play the shutdown (ENDING) animation
     * on death/unload, which is deliberately NOT wanted — only an explicit
     * {@code /halo hide} should trigger the shutdown animation.  Ownership in
     * {@link HaloWorldSaveData} is intentionally preserved so a player's halo
     * survives respawn / reconnect.</p>
     *
     * @param entityUuid the entity UUID
     * @param server     the current Minecraft server (unused — kept for signature stability)
     */
    public void removeHalo(UUID entityUuid, MinecraftServer server) {
        HaloInstance removed = activeHalos.remove(entityUuid);
        if (removed != null) {
            HaloMod.LOGGER.debug("Halo silently removed for uuid={}", entityUuid);
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
     * Insert a halo instance with a specific transition state.  Called by the
     * client network layer when receiving an incremental attach (which should
     * trigger a startup animation).
     *
     * @param entityUuid the entity UUID
     * @param defId      the halo definition identifier
     * @param state      the initial transition state (e.g. {@code STARTING})
     */
    public void putClientHalo(UUID entityUuid, Identifier defId, network.azusake.halo.data.HaloTransitionState state) {
        HaloInstance inst = new HaloInstance(entityUuid, defId);
        inst.setTransitionState(state);
        // STARTING/ENDING instances need the transition timer started, otherwise
        // isTransitioning() returns false immediately and the animation is skipped.
        if (state == network.azusake.halo.data.HaloTransitionState.STARTING
                || state == network.azusake.halo.data.HaloTransitionState.ENDING) {
            inst.startTransition(0.0);
        }
        activeHalos.put(entityUuid, inst);
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
     * Force-remove a halo from the active map, bypassing any shutdown animation.
     * Called by the renderer after a shutdown animation completes.
     *
     * @param entityUuid the entity UUID
     */
    public void forceRemoveHalo(UUID entityUuid) {
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
                // Silently drop the runtime instance.  No broadcast: death/unload
                // must not play the shutdown animation — that is reserved for an
                // explicit /halo hide.  World-level ownership is preserved (a dead
                // player's entry survives for respawn; non-players are pruned by
                // EntityHaloTracker.cleanup).
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
     * Clear all halo instances from the active map. Used on disconnect to
     * prevent data from a previous server session leaking into the next one.
     */
    public void clearAllClientHalos() {
        activeHalos.clear();
    }

    /**
     * How many halos are currently active.
     */
    public int getActiveCount() {
        return activeHalos.size();
    }

    /**
     * Get a specific halo instance by entity UUID.
     * Returns {@code null} if no halo is attached to that entity.
     */
    public HaloInstance getInstance(UUID entityUuid) {
        return activeHalos.get(entityUuid);
    }

    /**
     * Return all active halo instances (no defensive copy — for internal iteration).
     */
    public Collection<HaloInstance> getAllInstances() {
        return activeHalos.values();
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
        for (var world : server.getAllLevels()) {
            var entity = world.getEntity(uuid);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }
}
