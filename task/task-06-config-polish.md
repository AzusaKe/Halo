# Task 06: Config GUI & Polish

## Agent Type: `general-purpose`

## Goal
Add in-game configuration with sliders, create multiple example halo definitions, implement keyframe animation support, and handle all edge cases (death, respawn, dimension change, disconnect). After this task, the mod is beta-ready.

## Dependencies
- `task-05-depth-glow` complete

## Input
- Working rendering with depth + glow
- `HaloJsonLoader` registry
- `HaloCommand` infrastructure
- All previous systems operational

## Task

### 1. Cloth Config Integration

Add dependency to `build.gradle`:
```groovy
modImplementation "me.shedaniel.cloth:cloth-config-fabric:11.1.106"
```

Create `HaloConfig.java`:
```java
@Config(name = "halo")
public class HaloConfig implements ConfigData {
    // Default damping parameters (used when definition doesn't specify)
    @ConfigEntry.Gui.CollapsibleObject
    public DampingDefaults damping = new DampingDefaults();

    public static class DampingDefaults {
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        @ConfigEntry.Gui.Tooltip
        public int linearFactorPercent = 25;  // 0.25 as percentage for slider

        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int angularFactorPercent = 15;

        @ConfigEntry.BoundedDiscrete(min = 0, max = 1000)
        public double maxLinearDistance = 2.0;

        @ConfigEntry.BoundedDiscrete(min = 0, max = 180)
        public double maxAngularDegrees = 45.0;
    }

    // Per-player preferences
    public boolean showOwnHaloInFirstPerson = false;
    public float globalHaloScale = 1.0f;
}
```

Register config in `HaloMod.onInitialize()`:
```java
AutoConfig.register(HaloConfig.class, GsonConfigSerializer::new);
```

Add ModMenu integration (optional, Cloth Config auto-creates the button):
```groovy
modImplementation "com.terraformersmc:modmenu:7.2.2"
```

### 2. Create Example Halo Definitions

Create 4-5 JSON files under `assets/halo/halo_definitions/`:

| File | Description |
|------|-------------|
| `ring_default.json` | Simple white ring, slow yaw rotation, gentle Y oscillation |
| `ring_double.json` | Two concentric rings, different rotation speeds, each with own glow |
| `ring_cross.json` | Ring + cross pattern, oscillating scale on X axis |
| `ring_divine.json` | Golden ring with strong warm glow and pulse, faster rotation |
| `ring_geometric.json` | Geometric pattern, keyframe-based complex animation |

Each should demonstrate different features of the JSON schema.

### 3. Implement `KeyframeCurve.java` (`animation/builtin/`)

```java
public class KeyframeCurve implements AnimationCurve {
    record Keyframe(float time, float value, Easing easing);
    enum Easing { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }

    List<Keyframe> keyframes;

    @Override
    public float evaluate(float tickTime) {
        // Find surrounding keyframes, interpolate with easing
        // Clamp at boundaries
    }
}
```

Add `KeyframeCurve` to the polymorphic deserializer in JSON loader.

### 4. Handle Edge Cases

**Death:**
- Register `ServerEntityEvents.ENTITY_UNLOAD` → remove halo on death/unload
- On respawn: re-apply halo automatically (store a "pending" map of player UUID → definition ID that were removed on death)

**Dimension change:**
- Detect dimension change in `HaloPhysicsEngine` by tracking `lastDimension` in `HaloInstance`
- On change: set `needsSnap = true`, resync packet

**Disconnect:**
- `ServerPlayConnectionEvents.DISCONNECT` → remove from active halos, store in a "saved" map keyed by player UUID
- On reconnect (`JOIN` event) → restore from saved map, send sync

**Entity removal:**
- Each tick in `HaloManager.tick()`: check `entity.isRemoved()`, cleanup if so

### 5. Performance Polish

- **Object pooling**: Reuse temporary `Vec3d`/`Quaternionf` objects in hot paths (render loop, physics tick)
- **Avoid allocation in render**: Pre-allocate `Matrix4f` for billboard math, reuse each frame
- **Culling**: Skip halos whose world position is outside the camera frustum (already in task 04, verify it's working)
- **Limit active halos**: Config option for max halos per player / max total halos

### 6. `/halo` Command Enhancements

Add additional subcommands:
```
/halo preview <definition>         — Applies halo to self, client-only preview mode (no sync)
/halo config <param> <value>       — Change config values via command
/halo reload                       — Reload halo definitions from datapacks
/halo info                         — Show info about own current halo
```

### 7. Verify

- Cloth Config screen accessible via ModMenu, sliders work
- All 5 example halos load and render correctly
- KeyframeCurve animations play correctly
- Die and respawn → halo reappears
- Change dimension → halo snaps to new position
- Disconnect and reconnect → halo persists
- Multiple players with different halos → all render correctly
- 50+ entities with halos → no significant FPS drop (verify with F3 debug screen)

## Output Artifacts
- `HaloConfig.java`
- Updated `build.gradle` (Cloth Config + ModMenu deps)
- `halo_definitions/ring_default.json` (updated)
- `halo_definitions/ring_double.json` (new)
- `halo_definitions/ring_cross.json` (new)
- `halo_definitions/ring_divine.json` (new)
- `halo_definitions/ring_geometric.json` (new)
- `animation/builtin/KeyframeCurve.java` (new)
- Updated `json/HaloDefinitionDeserializer.java` (KeyframeCurve support)
- Updated `HaloCommand.java` (new subcommands)
- Updated `HaloManager.java` (death/respawn/dimension edge cases)
