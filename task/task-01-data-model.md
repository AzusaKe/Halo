# Task 01: Data Model & JSON Parsing

## Agent Type: `general-purpose`

## Goal
Define all data classes and implement a working JSON → Java deserialization pipeline. At the end, the mod can load halo definitions from JSON resource files and a test command can print their parsed content.

## Dependencies
- `task-00-project-init` complete (build compiles)

## Input
- Working Gradle project with entrypoints
- Empty directories: `data/`, `json/`, `animation/`, `animation/builtin/`, `shape/`
- Resource directory: `assets/halo/halo_definitions/` (empty)
- Data directory: `data/halo/halo_definition/` (empty)

## Task

### 1. Define Core Data Classes (`data/` package)

**`HaloDampingConfig.java`** — damping parameters:
```java
public record HaloDampingConfig(
    double linearFactor,      // k: 0=instant snap, 1=never move
    double angularFactor,
    double maxLinearDistance,  // S: clamp radius in blocks
    double maxAngularDegrees
) {}
```

**`HaloPositioning.java`** — static offsets:
```java
public record HaloPositioning(
    Vec3d offset,    // [x, y, z] from head anchor
    double scale     // uniform scale multiplier
) {}
```

**`HaloDefinition.java`** — full parsed definition:
```java
public record HaloDefinition(
    Identifier id,
    HaloShape shape,
    HaloAnimation animation,
    HaloPositioning positioning,
    HaloDampingConfig damping
) {}
```

**`HaloInstance.java`** — per-entity runtime state:
```java
public class HaloInstance {
    UUID entityUuid;
    Identifier definitionId;
    Vec3d relativePosition;       // physics output: offset from anchor
    Quaternionf relativeRotation; // angular state
    Vec3d prevRelativePosition;   // for tickDelta interpolation
    Quaternionf prevRelativeRotation;
    boolean needsSnap;
    // ... getters/setters
}
```

**`HaloRenderState.java`** — client-side interpolated state (can be a simple record).

### 2. Define Shape Types (`shape/` package)

Sealed interface `HaloShape` with subtypes:
- **`BillboardShape`**: single textured quad, fields: `Identifier texture`, `Vec2f size`, optional `GlowLayer glow`
- **`MultiBillboardShape`**: list of billboard layers (for future use)
- `GlowLayer` record: `Identifier texture`, `Vec2f size`, `int color` (hex RGB), `float alpha`, optional `PulseConfig pulse`

### 3. Define Animation Types (`animation/` package)

**`HaloAnimation.java`** — container:
```java
public record HaloAnimation(
    List<PositionCurve> positionCurves,
    List<RotationCurve> rotationCurves
) {}
```

**`AnimationCurve.java`** — sealed interface, subtypes in `builtin/`:
- **`ConstantCurve`**: static value
- **`LinearCurve`**: `value(t) = start + speed * t`
- **`OscillateCurve`**: `value(t) = amplitude * sin(frequency * t + phase)`
- **`KeyframeCurve`** (Phase 5): list of (time, value) pairs with easing

Each curve is per-axis: `PositionCurve` has `axis: x|y|z`, `RotationCurve` has `axis: yaw|pitch|roll`.

### 4. Implement JSON Deserialization (`json/` package)

**`HaloJsonLoader.java`**:
- Register as Fabric resource reload listener for `ResourceType.SERVER_DATA`
- On reload: scan `halo_definitions/` directory in all datapacks/resource packs
- Parse each `.json` file → `HaloDefinition`
- Store in a static `Map<Identifier, HaloDefinition>` registry
- Also register client-side listener for resource pack overrides

**`HaloDefinitionDeserializer.java`**:
- Gson `JsonDeserializer<HaloDefinition>` 
- Handle polymorphic shape types via a `type` discriminator field
- Handle polymorphic animation curve types
- Register custom type adapters for `Vec3d` (array → [x, y, z]), `Identifier`, `Vec2f`
- Graceful error handling: log and skip malformed definitions, don't crash

### 5. Write Sample JSON Definition

Create `src/main/resources/assets/halo/halo_definitions/ring_default.json` following the schema from the plan. A simple billboard ring with oscillating Y position and linear yaw rotation.

### 6. Wire into HaloMod

In `HaloMod.onInitialize()`:
- Call `HaloJsonLoader.register()` to hook the resource reload listener

### 7. Verify (manual)

- Add a temporary command `/halo dump` that prints all loaded definitions to chat/log
- Launch game, run `/reload`, run `/halo dump`
- Confirm `ring_default` appears with correct parsed values

## Output Artifacts
- `data/HaloDefinition.java`
- `data/HaloDampingConfig.java`
- `data/HaloPositioning.java`
- `data/HaloInstance.java`
- `data/HaloRenderState.java`
- `shape/HaloShape.java`
- `shape/BillboardShape.java`
- `shape/MultiBillboardShape.java`
- `shape/GlowLayer.java`
- `animation/HaloAnimation.java`
- `animation/AnimationCurve.java`
- `animation/PositionCurve.java`
- `animation/RotationCurve.java`
- `animation/builtin/ConstantCurve.java`
- `animation/builtin/LinearCurve.java`
- `animation/builtin/OscillateCurve.java`
- `json/HaloJsonLoader.java`
- `json/HaloDefinitionDeserializer.java`
- `resources/assets/halo/halo_definitions/ring_default.json`
