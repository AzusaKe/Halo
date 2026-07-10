# Anchor Provider API

> Extension interface for head-anchor computation — designed to accept model-driven replacements without touching framework code.

---

## 1. Core Interface

`EntityAnchorProvider` (`src/main/java/network/azusake/halo/physics/EntityAnchorProvider.java`) is a `@FunctionalInterface` called once per render frame: entity + tickDelta → world-space head centre + orientation.

```java
@FunctionalInterface
public interface EntityAnchorProvider {
    HeadAnchor resolve(LivingEntity entity, float tickDelta);
}
```

`HeadAnchor` carries three fields and nothing else:

```java
public record HeadAnchor(Vec3d headCenter, float yaw, float pitch) {}
```

The sole consumer is `AnchorFrameCalculator.calculate()`. It depends only on those three fields — it has no knowledge of how they were computed. Swapping the provider implementation therefore requires no changes outside `AnchorFrameCalculator`.

---

## 2. Current Implementations

| Class | Scope | Approach |
|---|---|---|
| `PlayerAnchorProvider` | Player entities | Pose-aware JSON data-driven (pivot + head_center_vector) |
| `FallbackAnchorProvider` | All other entities | height × 0.85 rough approximation |

Dispatch logic at `AnchorFrameCalculator.calculate()` lines 100–103:

```java
EntityAnchorProvider provider = (entity instanceof PlayerEntity)
    ? playerProvider
    : fallbackProvider;
HeadAnchor ha = provider.resolve(entity, tickDelta);
```

---

## 3. Future Extension Points

### 3.1 Model-Driven Provider

When you want exact head-centre positions computed by replaying Mojang's own rendering code with identical inputs, write one new `EntityAnchorProvider` implementation:

```java
public class ModelBasedAnchorProvider implements EntityAnchorProvider {

    @Override
    public HeadAnchor resolve(LivingEntity entity, float tickDelta) {
        // 1. EntityRenderDispatcher → EntityModel (PlayerEntityModel / ZombieEntityModel / …)
        // 2. Call setAngles(entity, limbAngle, limbDistance, age, headYaw, pitch)
        //    — identical inputs to the Mojang render path
        // 3. Read the head ModelPart's final world-space transform
        //    (pivot + parent chain concatenation)
        // 4. Project to world-space headCenter
        // 5. return new HeadAnchor(headCenter, yaw, pitch)
    }
}
```

Replacement is a single-line change in `AnchorFrameCalculator`:

```java
// Old:
EntityAnchorProvider provider = (entity instanceof PlayerEntity)
    ? playerProvider : fallbackProvider;

// New:
EntityAnchorProvider provider = modelBasedProvider; // or per-entity-type dispatch
```

### 3.2 Non-Player Entity JSON Profiles

For cows, chickens, endermen, etc. — create `data/halo/entity_anchors/<entity_id>.json`. No code changes needed. Example `minecraft:cow.json`:

```json
{
  "entity": "minecraft:cow",
  "default_pose": "standing",
  "poses": {
    "standing": {
      "pivot": [0.0, 1.0, 0.8],
      "head_center_vector": [0.0, 0.15, 0.0]
    }
  }
}
```

`EntityAnchorLoader` auto-loads them on `/reload`. To wire them in, promote the dispatch from `instanceof PlayerEntity` to a generalised profile lookup:

```java
EntityAnchorProvider provider = profileBasedProvider;
// Inside: EntityAnchorLoader.getProfile(entity.getType()) → JSON-driven
//         → absent? instanceof PlayerEntity → player JSON
//         → still absent? → fallback
```

### 3.3 Hybrid Strategy

A single provider can layer strategies — e.g. JSON first, model-driven second, fallback third. The single-method signature imposes no limit on internal complexity.

---

## 4. Backward Compatibility

| Change | Affected Files | Breaks JSON Profiles? |
|---|---|---|
| New `EntityAnchorProvider` impl | 1 line in `AnchorFrameCalculator` | No |
| New `entity_anchors/*.json` | Zero code files | No (auto-loaded) |
| Extend `HeadAnchor` record | `AnchorFrameCalculator` + providers | Only if fields are removed |

The JSON schema (`PoseAnchor` + `EntityAnchorProfile`) is stable. When you switch to a model-driven provider, existing `player.json` automatically becomes the fallback — the model path provides primary data, the profile JSON catches anything that's missing.

---

## 5. Key Invariants

1. **Code downstream of `calculate()` never observes a provider switch.** Damping, orientation modes, and camera-relative coordinates consume only `HeadAnchor`.
2. **JSON profiles are optional overlays.** Present → used. Absent → the provider's built-in default takes over.
3. **Provider resolution runs on the render thread.** Safe to access `MinecraftClient`, model instances, and other client-side resources. No threading concerns.
4. **All pose discrimination lives inside providers.** `AnchorFrameCalculator` contains zero `EntityPose` or `isSneaking()` calls.
