package com.example.halo.manager;

import com.example.halo.HaloMod;
import com.example.halo.config.HaloConfig;
import com.example.halo.data.HaloDefinition;
import com.example.halo.data.HaloInstance;
import com.example.halo.json.HaloJsonLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

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
 *   <li>Drive per-tick damping physics via {@link #tickAll(MinecraftServer)}.</li>
 *   <li>Hold the shared {@link HaloConfig} that can be tuned in-game.</li>
 * </ul>
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

        HaloMod.LOGGER.debug("Halo '{}' shown on entity {} (uuid={})", defId, entity.getName().getString(), entity.getUuid());
    }

    /**
     * Remove the halo from a living entity (no-op if none was attached).
     *
     * @param entity the target living entity
     */
    public void hideHaloOn(LivingEntity entity) {
        HaloInstance removed = activeHalos.remove(entity.getUuid());
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
     * Drive per-tick damping physics for every active halo instance.
     *
     * <p>Called once per server tick from the tick handler.  Halos whose
     * attached entity is dead or missing are automatically cleaned up.</p>
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
            HaloInstance instance = entry.getValue();

            LivingEntity entity = findEntityByUuid(server, uuid);
            if (entity == null || !entity.isAlive()) {
                iterator.remove();
                HaloMod.LOGGER.debug("Halo cleaned up: entity uuid={} is gone or dead", uuid);
                continue;
            }

            Vec3d anchorPos = getHeadAnchorPosition(entity);
            // getRelativePosition() is stored as world-space inside tickDamping(),
            // so pass it directly as the halo centre (no need to re-add anchorPos).
            instance.tickDamping(anchorPos, instance.getRelativePosition(), config.toDampingConfig());
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
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Compute the world-space anchor point for a halo on the given entity.
     *
     * <p>Players use their eye position; other entities use 85 % of their
     * bounding-box height above their foot position.</p>
     *
     * @param entity the living entity
     * @return world-space position where the halo should be centred
     */
    private Vec3d getHeadAnchorPosition(LivingEntity entity) {
        if (entity instanceof PlayerEntity player) {
            return player.getEyePos();
        }
        return entity.getPos().add(0, entity.getHeight() * 0.85, 0);
    }

    /**
     * Find a living entity by UUID across all server worlds.
     *
     * @param server the Minecraft server instance
     * @param uuid   the entity UUID to look up
     * @return the entity, or {@code null} if not found
     */
    private LivingEntity findEntityByUuid(MinecraftServer server, UUID uuid) {
        for (var world : server.getWorlds()) {
            var entity = world.getEntity(uuid);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }
}
