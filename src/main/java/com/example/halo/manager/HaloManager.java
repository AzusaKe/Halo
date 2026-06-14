package com.example.halo.manager;

import com.example.halo.HaloMod;
import com.example.halo.config.HaloConfig;
import com.example.halo.data.HaloDampingConfig;
import com.example.halo.data.HaloDefinition;
import com.example.halo.data.HaloEntityData;
import com.example.halo.data.HaloInstance;
import com.example.halo.json.HaloJsonLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

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
    private final Map<UUID, LivingEntity> trackedEntities = new ConcurrentHashMap<>();

    /** Nanosecond timestamp of the previous tick, for delta-time calculation. */
    private long lastTickNanos;

    private HaloManager() {
        // singleton — use getInstance()
        this.lastTickNanos = System.nanoTime();
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
     * Drive per-tick damping physics for every active halo instance.
     *
     * <p>Called once per server tick from the tick handler.  Halos whose
     * attached entity is dead or missing are automatically cleaned up.</p>
     *
     * <p>Uses frame-rate-independent damping with delta-time measurement
     * so convergence speed is consistent regardless of server TPS.</p>
     *
     * @param server the current Minecraft server instance
     */
    public void tickAll(MinecraftServer server) {
        if (activeHalos.isEmpty()) {
            return;
        }

        // Compute delta time since last tick (frame-rate-independent damping)
        long now = System.nanoTime();
        double dt = (now - lastTickNanos) / 1_000_000_000.0;
        dt = Math.max(0.001, Math.min(dt, 0.5)); // 1ms–500ms guard
        lastTickNanos = now;

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

            // Look up definition for positioning offset and damping params
            HaloDefinition def = HaloJsonLoader.getDefinition(instance.getDefinitionId()).orElse(null);
            Vec3d defOffset = def != null ? def.positioning().offset() : config.getPositionOffset();

            // Merge runtime config overrides with definition defaults
            HaloDampingConfig damping = mergeDampingConfig(def != null ? def.damping() : null, config);

            // Compute absolute target: head anchor + head-relative offset
            Vec3d anchorPos = getHeadAnchorPosition(entity);
            Vec3d headRelOffset = computeHeadRelativeOffset(entity, defOffset);
            Vec3d target = anchorPos.add(headRelOffset);

            // Tick damping toward the absolute target position
            instance.tickDamping(target, instance.getRelativePosition(), damping, dt);
        }
    }

    /**
     * Merge runtime config overrides with definition defaults.
     * Runtime config values are consulted when the user has explicitly changed them
     * via {@code /halo config}; otherwise definition values are used as-is.
     *
     * <p>The runtime config is considered an <em>override</em>: if the user has
     * changed any value (detected by comparing against defaults), that override
     * wins.  Otherwise the definition's damping config is used untouched.</p>
     *
     * @param defDamping  the definition's damping config, or {@code null}
     * @param runtimeCfg  the runtime halo config (may have been changed by user)
     * @return a merged damping config
     */
    private static HaloDampingConfig mergeDampingConfig(
        HaloDampingConfig defDamping, HaloConfig runtimeCfg
    ) {
        if (defDamping == null) {
            return runtimeCfg.toDampingConfig();
        }
        // Use runtime config values if they differ from the HaloConfig defaults,
        // otherwise keep the definition's values.  The HaloConfig defaults are:
        //   linearDampingFactor = 0.3, angularDampingFactor = 0.3,
        //   maxLinearDistance = 1.0, maxAngularDegrees = 45.0
        boolean runtimeOverridden =
            Math.abs(runtimeCfg.getLinearDampingFactor() - 0.3) > 1e-9
            || Math.abs(runtimeCfg.getAngularDampingFactor() - 0.3) > 1e-9
            || Math.abs(runtimeCfg.getMaxLinearDistance() - 1.0) > 1e-9
            || Math.abs(runtimeCfg.getMaxAngularDegrees() - 45.0) > 1e-9;

        if (runtimeOverridden) {
            return new HaloDampingConfig(
                runtimeCfg.getLinearDampingFactor(),
                runtimeCfg.getAngularDampingFactor(),
                runtimeCfg.getMaxLinearDistance(),
                runtimeCfg.getMaxAngularDegrees()
            );
        }
        return defDamping;
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
            com.example.halo.lifecycle.HaloWorldSaveData data =
                com.example.halo.lifecycle.HaloWorldSaveData.get(world);
            data.syncFromManager();
        }
        HaloMod.LOGGER.debug("HaloManager: cleanup complete — {} active halo(s) synced", activeHalos.size());
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
     * Convert a definition offset from head-relative space to world space.
     * offset.x = right, offset.y = head-up, offset.z = behind.
     */
    private static Vec3d computeHeadRelativeOffset(LivingEntity entity, Vec3d offset) {
        float yawRad = (float) Math.toRadians(entity.getHeadYaw());
        float pitchRad = (float) Math.toRadians(entity.getPitch());

        Vec3d forward = new Vec3d(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        ).normalize();

        Vec3d worldUp = new Vec3d(0, 1, 0);
        Vec3d right;
        if (Math.abs(forward.dotProduct(worldUp)) > 0.999) {
            right = new Vec3d(Math.cos(yawRad), 0, Math.sin(yawRad));
        } else {
            right = worldUp.crossProduct(forward).normalize();
        }
        Vec3d headUp = forward.crossProduct(right).normalize();
        Vec3d behind = forward.multiply(-1);

        return right.multiply(offset.x)
            .add(headUp.multiply(offset.y))
            .add(behind.multiply(offset.z));
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
