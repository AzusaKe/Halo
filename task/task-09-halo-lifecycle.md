# Task 09: Halo Lifecycle Manager

## Agent Type: `general-purpose`

## Goal

Implement per-entity halo state tracking, persistence (NBT), and cleanup. Halos survive world reload, but are cleaned up when entities despawn. Support for marking teleports to trigger damping snap.

## Dependencies

- `task-08-halo-config-cli` complete (HaloManager exists)
- `task-07-damping-physics` complete (physics working)

## Input

- `HaloManager` from task-08
- `HaloInstance` with damping from task-07
- Fabric entity event system

## Task

### 1. Create `HaloEntityData.java` (NBT wrapper)

```java
public class HaloEntityData {
    private static final String NBT_KEY = "HaloInstance";
    private static final String ID_KEY = "HaloId";
    private static final String DEF_KEY = "Definition";
    private static final String SCALE_KEY = "Scale";
    private static final String OFFSET_KEY = "Offset";

    public static void attachHalo(LivingEntity entity, Identifier definitionId) {
        NbtCompound nbt = entity.getPersistentData();
        NbtCompound haloData = new NbtCompound();

        haloData.putString(ID_KEY, entity.getUuid().toString());
        haloData.putString(DEF_KEY, definitionId.toString());
        haloData.putDouble(SCALE_KEY, 1.0);
        haloData.putDoubleArray(OFFSET_KEY, new double[]{0, 0.3, 0});

        nbt.put(NBT_KEY, haloData);
    }

    public static boolean hasHalo(LivingEntity entity) {
        return entity.getPersistentData().contains(NBT_KEY);
    }

    public static Identifier getHaloDefinition(LivingEntity entity) {
        NbtCompound nbt = entity.getPersistentData().getCompound(NBT_KEY);
        if (!nbt.contains(DEF_KEY)) return null;

        try {
            return new Identifier(nbt.getString(DEF_KEY));
        } catch (InvalidIdentifierException e) {
            return null;
        }
    }

    public static void removeHalo(LivingEntity entity) {
        entity.getPersistentData().remove(NBT_KEY);
    }
}
```

### 2. Create `EntityHaloTracker.java` (event listener)

```java
public class EntityHaloTracker {
    private static final Map<UUID, Long> recentlyTeleported = new ConcurrentHashMap<>();
    private static final long TELEPORT_GRACE_PERIOD_MS = 100;

    public static void register() {
        // On entity death → remove halo
        ServerEntityEvents.EQUIPMENT_CHANGE.register((livingEntity, equipmentSlot, oldItem, newItem) -> {
            // This is for tracking entity events - might need ServerLivingEntityEvents if available
        });

        // Register custom entity event to track position changes
        ServerTickEvents.END.register(server -> {
            recentlyTeleported.entrySet().removeIf(entry ->
                System.currentTimeMillis() - entry.getValue() > TELEPORT_GRACE_PERIOD_MS
            );
        });
    }

    /**
     * Call when entity teleports to enable damping snap.
     */
    public static void markTeleport(LivingEntity entity) {
        recentlyTeleported.put(entity.getUuid(), System.currentTimeMillis());

        HaloInstance instance = HaloManager.getInstance().getHaloInstance(entity.getUuid());
        if (instance != null) {
            instance.markTeleported();
        }
    }

    public static boolean isTeleporting(UUID entityUuid) {
        return recentlyTeleported.containsKey(entityUuid);
    }

    /**
     * Called when entity dies or leaves world
     */
    public static void cleanup(LivingEntity entity) {
        HaloManager.getInstance().hideHaloOn(entity);
        recentlyTeleported.remove(entity.getUuid());
    }
}
```

### 3. Update `HaloInstance.java` (extend with more state)

```java
public class HaloInstance {
    private final UUID entityUuid;
    private final Identifier definitionId;
    private Vec3d relativePosition;
    private Quaternionf relativeRotation;
    private Vec3d prevRelativePosition;
    private Quaternionf prevRelativeRotation;

    private final HaloDampingState dampingState;
    private boolean active = true;
    private long createdAtTime;

    public HaloInstance(UUID uuid, Identifier defId, HaloPositioning positioning, HaloDampingConfig damping) {
        this.entityUuid = uuid;
        this.definitionId = defId;
        this.relativePosition = new Vec3d(0, 0.3, 0);
        this.relativeRotation = new Quaternionf();
        this.prevRelativePosition = this.relativePosition.copy();
        this.prevRelativeRotation = new Quaternionf(this.relativeRotation);
        this.dampingState = new HaloDampingState();
        this.createdAtTime = System.currentTimeMillis();
    }

    public void markTeleported() {
        dampingState.markTeleport();
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        this.active = false;
    }

    public long getCreatedAtTime() {
        return createdAtTime;
    }

    // Getters for rendering
    public UUID getEntityUuid() { return entityUuid; }
    public Identifier getDefinitionId() { return definitionId; }
    public Vec3d getRelativePosition() { return relativePosition; }
    public Quaternionf getRelativeRotation() { return relativeRotation; }
    public Vec3d getInterpolatedPosition(double tickDelta) {
        return prevRelativePosition.lerp(relativePosition, tickDelta);
    }
    public Quaternionf getInterpolatedRotation(double tickDelta) {
        return new Quaternionf(prevRelativeRotation).slerp(relativeRotation, (float)tickDelta);
    }
}
```

### 4. Create `HaloEntityEventHandler.java` (mixins bridge)

```java
public class HaloEntityEventHandler {
    /**
     * Called when player changes dimension (nether portal, teleport, etc.)
     */
    public static void onEntityTeleport(LivingEntity entity) {
        EntityHaloTracker.markTeleport(entity);
    }

    /**
     * Called on entity death or removal
     */
    public static void onEntityRemove(LivingEntity entity) {
        EntityHaloTracker.cleanup(entity);
    }

    /**
     * Track player position changes to detect teleports
     */
    public static void onEntityMove(LivingEntity entity) {
        if (entity instanceof ServerPlayerEntity player) {
            // Check if player moved too far in one tick (> 20 blocks)
            // if so, it's likely a teleport
            Vec3d currentPos = player.getPos();
            Vec3d lastPos = player.lastRenderPos;  // approximate
            if (currentPos.distanceTo(lastPos) > 20.0) {
                EntityHaloTracker.markTeleport(player);
            }
        }
    }
}
```

### 5. Create `HaloWorldSaveData.java` (world persistence)

```java
public class HaloWorldSaveData extends PersistentState {
    private static final String NAME = "halo_world_data";

    public static HaloWorldSaveData getPersistentState(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
            HaloWorldSaveData::new,
            HaloWorldSaveData::createFromNbt,
            NAME
        );
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        // Save active halos to world NBT
        NbtList haloList = new NbtList();

        HaloManager.getInstance().getAllInstances().forEach(instance -> {
            NbtCompound haloTag = new NbtCompound();
            haloTag.putString("UUID", instance.getEntityUuid().toString());
            haloTag.putString("Definition", instance.getDefinitionId().toString());
            haloList.add(haloTag);
        });

        tag.put("Halos", haloList);
        return tag;
    }

    public static HaloWorldSaveData createFromNbt(NbtCompound tag) {
        HaloWorldSaveData data = new HaloWorldSaveData();

        NbtList haloList = tag.getList("Halos", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < haloList.size(); i++) {
            NbtCompound haloTag = haloList.getCompound(i);
            UUID uuid = UUID.fromString(haloTag.getString("UUID"));
            Identifier defId = new Identifier(haloTag.getString("Definition"));

            // Will be restored on entity load
            // (stored for reference)
        }

        return data;
    }
}
```

### 6. Update `HaloManager.java` (extend with cleanup & tracking)

```java
public class HaloManager {
    // ... existing fields ...

    private final Map<UUID, LivingEntity> trackedEntities = new ConcurrentHashMap<>();

    public void registerEntity(LivingEntity entity) {
        trackedEntities.put(entity.getUuid(), entity);
    }

    public void unregisterEntity(UUID uuid) {
        trackedEntities.remove(uuid);
        activeHalos.remove(uuid);
    }

    public Collection<HaloInstance> getAllInstances() {
        return activeHalos.values();
    }

    public void cleanup() {
        // Called on world unload
        HaloWorldSaveData.getPersistentState(getCurrentWorld()).setDirty();
    }
}
```

### 7. Register Event Handlers

In `HaloMod.onInitialize()`:

```java
public void onInitialize() {
    // ... existing ...
    EntityHaloTracker.register();

    // Mixins or entity event callbacks
    // (may need to create mixins if Fabric doesn't provide direct events)
}
```

### 8. Unit Tests

```
src/test/java/com/example/halo/lifecycle/EntityHaloTrackerTest.java
- testTeleportMarking(): teleport marks snap correctly
- testCleanup(): dead entity removes halo
- testPersistence(): halo survives world reload
- testNBTRoundtrip(): NBT encode/decode preserves data
```

### 9. In-Game Verification

```
/halo show @s ring_default
→ Halo on player

/tp @s ~ ~10 ~
→ Halo snaps to new position (no sliding)

/save-all
→ Save successful, halo data written

/reload
→ Restart game in same world
→ Halo still on player (persisted)

/kill @s
→ Halo disappears, no errors
```

## Output Artifacts

- `src/main/java/com/example/halo/data/HaloEntityData.java`
- `src/main/java/com/example/halo/lifecycle/EntityHaloTracker.java`
- `src/main/java/com/example/halo/lifecycle/HaloEntityEventHandler.java`
- `src/main/java/com/example/halo/lifecycle/HaloWorldSaveData.java`
- Updated: `src/main/java/com/example/halo/data/HaloInstance.java` (extend with lifecycle methods)
- Updated: `src/main/java/com/example/halo/manager/HaloManager.java` (add cleanup & tracking)
- `src/test/java/com/example/halo/lifecycle/EntityHaloTrackerTest.java`

## Success Criteria

✓ Teleports trigger damping snap  
✓ Dead entities clean up halos  
✓ Halos persist across `/save-all` and world reload  
✓ NBT data consistent across game sessions  
✓ All unit tests pass  
✓ No memory leaks with 100+ active halos over 1 hour

## Assigned to: **Dev-1 (Backend Lead)**

## Reviewer: **QA Lead**
