# Task 02: Server Core

## Agent Type: `general-purpose`

## Goal
Implement the server-side halo management: entity → halo binding, per-tick damping physics, animation evaluation, and the `/halo` command. At the end, halos can be assigned to entities via command and their physics state updates each tick (verified via debug logging, not visually yet).

## Dependencies
- `task-01-data-model` complete (data classes, JSON loader, sample definition exist)

## Input
- `HaloDefinition`, `HaloDampingConfig`, `HaloInstance`, `HaloRenderState` from data package
- `HaloAnimation`, animation curve types from animation package
- `HaloJsonLoader` static registry from json package
- Fabric API events available
- `org.joml.Quaternionf` in Minecraft classpath

## Task

### 1. Implement `HaloPhysicsEngine.java` (`physics/` package)

Single class with one main method:

```java
public void applyDampingTick(Entity entity, HaloInstance halo, HaloDampingConfig config)
```

Algorithm:
1. Get anchor point: `entity.getEyePos()` (or `entity.getPos().add(0, entity.getEyeHeight(), 0)`)
2. Compute target position: anchor + configured offset (from definition positioning)
3. **Snap detection**: if `halo.needsSnap` is true OR entity moved > 64 blocks² since last tick OR entity dimension changed → set position to target, set rotation to identity, clear `needsSnap`, store state, return
4. **Linear damping**: 
   - `dist = halo.relativePosition.length()`
   - `newDist = dist * (1.0 - config.linearFactor)`
   - If `newDist > config.maxLinearDistance` → `newDist = config.maxLinearDistance`
   - Normalize direction, scale to `newDist`
5. **Angular damping**:
   - `halo.relativeRotation = Quaternionf.slerp(halo.relativeRotation, IDENTITY, (float)config.angularFactor)`
   - If rotation angle > `maxAngularDegrees` → clamp via slerp
6. Store previous state (`prevRelativePosition`, `prevRelativeRotation`) for interpolation
7. Update `lastTickTime`

### 2. Implement `HaloAnimator.java` (`animation/` package)

Evaluate animation curves given game time:

```java
public static Transform evaluate(HaloAnimation anim, long gameTime)
```

- Iterate position curves → produce a `Vec3d` displacement
- Iterate rotation curves → produce a `Quaternionf` rotation
- Return combined `Transform` (position offset + rotation)
- `Transform` is a simple record: `Vec3d position, Quaternionf rotation`

Animation is purely additive on top of the damped position — it runs independently.

### 3. Implement `HaloManager.java` (`data/` package)

Singleton managing all active halos:

```java
public class HaloManager {
    private final Map<UUID, HaloInstance> activeHalos = new ConcurrentHashMap<>();
    private final HaloPhysicsEngine physics = new HaloPhysicsEngine();
    private final HaloAnimator animator = new HaloAnimator();

    public void setHalo(Entity entity, Identifier definitionId) { ... }
    public void removeHalo(UUID entityUuid) { ... }
    public HaloInstance getHalo(UUID entityUuid) { ... }
    public Collection<HaloInstance> getActiveHalos() { ... } // unmodifiable view

    public void tick(MinecraftServer server) {
        for (var entry : activeHalos.entrySet()) {
            Entity entity = findEntityAcrossWorlds(server, entry.getKey());
            if (entity == null || entity.isRemoved()) {
                removeHalo(entry.getKey());
                continue;
            }
            HaloDefinition def = HaloJsonLoader.get(entry.getValue().definitionId);
            if (def == null) continue;
            physics.applyDampingTick(entity, entry.getValue(), def.damping());
            // Animation evaluation happens client-side for smoothness,
            // or server-side if we want multiplayer consistency
        }
    }
}
```

Key design decisions:
- `setHalo()` replaces existing halo for same entity
- `findEntityAcrossWorlds()` iterates all worlds since entities may be in different dimensions
- Cleanup on entity removal/dimension unload
- `needsSnap` flag set to `true` on initial creation
- Track `lastDimension` per halo instance for teleport detection

### 4. Implement `HaloCommand.java` (`command/` package)

Use Fabric Command API v2. Register on server start.

Commands:
```
/halo apply <entity> <definition>   — attach halo to entity, permission level 2
/halo remove <entity>               — remove halo from entity, permission level 2
/halo list                          — list all active halos (entity + definition), permission 0
/halo definitions                   — list all loaded definitions, permission 0
```

Implementation notes:
- Use `EntitySelector` or `EntityArgumentType` for entity targeting
- Use a custom argument type or string suggestion provider for definition IDs
- Feedback messages using `Text.literal()` with translation keys
- On `/halo apply`: call `HaloManager.setHalo()`, send sync packet (networking task)
- On `/halo remove`: call `HaloManager.removeHalo()`, send remove packet
- For now, networking calls can be stubbed until `task-03-networking` is done

### 5. Wire into `HaloMod.java`

```java
public void onInitialize() {
    HaloJsonLoader.register();  // from task-01
    HALO_MANAGER = new HaloManager();
    HaloCommand.register();
    ServerTickEvents.END_SERVER_TICK.register(server -> HALO_MANAGER.tick(server));
    // Cleanup events
    ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> HALO_MANAGER.removeHalo(entity.getUuid()));
    ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HALO_MANAGER.removeHalo(handler.player.getUuid()));
}
```

### 6. Verify (debug logging)

- Add `LOGGER.info()` in `applyDampingTick` to print position/rotation each tick for a test entity
- Launch integrated server, run `/halo apply @p halo:ring_default`
- Walk around — observe damping values changing in log
- Teleport far away — observe snap behavior in log
- `/halo remove @p` — confirm removal
- `/halo list` — confirm empty

## Output Artifacts
- `physics/HaloPhysicsEngine.java`
- `animation/HaloAnimator.java`
- `data/HaloManager.java`
- `command/HaloCommand.java`
- Updated `HaloMod.java` with tick + cleanup registrations
