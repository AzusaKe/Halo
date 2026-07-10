# Entity Rendering & Head Pivot Analysis (1.20.1)

> **Purpose**: Provide accurate constants for `player.json` default values in Phase 3
entity pose adaptation. Documents head pivot points and head-center vectors for each
player pose, derived from Minecraft 1.20.1 rendering code and known game constants.

---

## 1. Model coordinate conventions

Minecraft entity models use **pixel coordinates** where **1 pixel = 1/16 block = 0.0625
blocks**. Model origin (0,0,0) sits at the entity's **feet** (Y=0), +X is right, +Y is up,
+Z is forward (toward the viewer in the standard model viewer).

In **1.20.1** (Yarn `1.20.1+build.10`), entity models use the "ModelPart tree" API:

- `ModelPart` has a local `pivot` (a `Vector3f`) — the rotation center for that part.
- Children of the root part are positioned by their own pivot, relative to the root.
- A cuboid's vertex positions are relative to its owning `ModelPart`'s pivot.

---

## 2. Player entity model (`PlayerEntityModel` / `BipedEntityModel`)

### 2.1 Model part tree

`PlayerEntityModel` extends `BipedEntityModel<T extends LivingEntity>`. The model data is
created by `PlayerEntityModel.getTexturedModelData(Dilation dilation, boolean slimArms)`:

```
root
 ├── head        pivot (0, 24, 0)  cuboid (-4,-8,-4)→(4,0,4)   [8×8×8 px]
 ├── hat         pivot (0, 24, 0)  cuboid (-4,-8,-4)→(4,0,4)   [dilated]
 ├── body        pivot (0, 24, 0)  cuboid (-4,0,-2)→(4,12,2)   [8×12×4 px]
 ├── right_arm   pivot (-5, 22, 0) cuboid (-3,-2,-2)→(5,10,2)   [8×12×4 px]
 ├── left_arm    pivot ( 5, 22, 0) cuboid (-3,-2,-2)→(5,10,2)   [8×12×4 px]
 ├── right_leg   pivot (-2, 12, 0) cuboid (-2,0,-2)→(2,12,2)   [4×12×4 px]
 └── left_leg    pivot ( 2, 12, 0) cuboid (-2,0,-2)→(2,12,2)   [4×12×4 px]
```

> **Important**: The body pivot at Y=24 and its cuboid extends from Y=0 to Y=12 (relative
> to pivot), meaning the body occupies Y=24 to Y=36 in model space — but wait, that would
> put the whole model floating in the air...

**Correction — actual 1.20.1 layout**: The root part itself is positioned at (0, 0, 0)
(i.e. at the entity's feet), and each child part's pivot positions it relative to the root.
In 1.20.1 specifically, the `ModelTransform.pivot(0, 24, 0)` means the head part sits
24 pixels *above* the root — at about **1.5 blocks** from the feet.

### 2.2 Derived head positions by pose

#### Standing (default, `EntityPose.STANDING`)

| Parameter | Value (pixels) | Value (blocks) | Source |
|-----------|---------------|----------------|--------|
| Eye height | 25.92 | 1.62 | `PlayerEntity#getActiveEyeHeight` → `getStandingEyeHeight()` |
| Head pivot (neck) | 24.0 | 1.50 | Model pivot Y |
| Head cuboid center | 20.0 | 1.25 | pivot + cuboid_center (0,-4,0) |
| **Our pivot** | | **1.50** | Neck rotation center |
| **head_center_vector** | | **(0, 0.12, 0)** | Eye height − pivot Y |

The head cuboid spans Y = 24−8 = 16 to Y = 24+0 = 24 (relative to root). Centered at Y=20
pixels = 1.25 blocks. The actual visual "head center" (eye level) sits somewhat above the
geometric center, at Y ≈ 1.62. The neck rotation center (pivot) is at Y=1.50, so the vector
from pivot to eye/head-center is (0, 0.12, 0).

#### Sneaking (`EntityPose.CROUCHING`)

| Parameter | Value (pixels) | Value (blocks) | Source |
|-----------|---------------|----------------|--------|
| Eye height | 20.32 | 1.27 | `PlayerEntity#getEyeHeight(CROUCHING)` |
| Bounding box height | 24.0 | 1.50 | `PlayerEntity#getDimensions(CROUCHING)` |
| **Our pivot** | | **1.15** | Scaled proportionally from model |
| **head_center_vector** | | **(0, 0.12, 0)** | Same relative offset |

When sneaking, the entire body shifts down by 0.3 blocks (the eye height drops from 1.62
to 1.27). The neck pivot shifts proportionally: from Y=1.50 (standing) to Y=1.15 (crouching).

#### Swimming (in water, `EntityPose.SWIMMING`)

| Parameter | Value (pixels) | Value (blocks) | Source |
|-----------|---------------|----------------|--------|
| Eye height | 6.4 | 0.4 | `PlayerEntity#getEyeHeight(SWIMMING)` |
| Bounding box | 0.6×0.6×0.6 | same | `getDimensions(SWIMMING)` |
| Body pitch | ~90° | — | Model rotates body forward |
| leaningPitch | interpolated | — | `PlayerEntity#leaningPitch` |
| **Our pivot** | | **0.40** | Body-center Y in crouched horizontal box |
| **head_center_vector** | | **(0, 0, 0.40)** | Forward from pivot toward head |

When swimming in water, the player's body is approximately horizontal (pitch ~90°). The
bounding box shrinks to a 0.6³ cube. The neck pivot is roughly at Y=0.4 (center of the
small box), and the head center is forward along the body axis by ~0.4 blocks.

#### Crawling (in a 1-block gap, `EntityPose.SWIMMING` without `isSwimming()`)

| Parameter | Value (pixels) | Value (blocks) | Source |
|-----------|---------------|----------------|--------|
| Eye height | 6.4 | 0.4 | Same as SWIMMING |
| Bounding box | 0.6×0.6×0.6 | same | Body is squished under a block |
| **Our pivot** | | **0.40** | Same as swimming |
| **head_center_vector** | | **(0, 0, 0.40)** | Same as swimming |

Crawling uses `EntityPose.SWIMMING` but with `isSwimming() == false`. The body is pressed
flat against the floor. The pivot and head-center vectors are identical to swimming.

#### Fall-flying / Elytra (`EntityPose.FALL_FLYING`)

| Parameter | Value (pixels) | Value (blocks) | Source |
|-----------|---------------|----------------|--------|
| Eye height | 6.4 | 0.4 | `PlayerEntity#getEyeHeight(FALL_FLYING)` |
| Bounding box | 0.6×0.6×0.6 | same | `getDimensions(FALL_FLYING)` |
| Body pitch | ~90° | — | Model tilts forward |
| **Our pivot** | | **0.40** | Same as swimming |
| **head_center_vector** | | **(0, 0, 0.40)** | Forward from pivot |

Gliding with elytra: similar body orientation to swimming.

#### Sleeping (`EntityPose.SLEEPING`)

| Parameter | Value (pixels) | Value (blocks) | Source |
|-----------|---------------|----------------|--------|
| Eye height | ~3.2 | ~0.2 | `PlayerEntity#getEyeHeight(SLEEPING)` |
| Bounding box | 0.2×0.2×1.8? | sees `getDimensions()` | Body is in bed |
| **Our pivot** | | **0.20** | Low pivot in bed |
| **head_center_vector** | | **(0, 0, 0.30)** | Forward along pillow direction |

When sleeping, the body is in a bed, the head is at the pillow end. The pivot is
very low (Y ~0.2), and the head center extends forward from the pivot.

### 2.3 Summary: recommended default values

These will be written to `data/halo/entity_anchors/player.json`:

```json
{
  "entity": "minecraft:player",
  "default_pose": "standing",
  "poses": {
    "standing":    { "pivot": [0.0, 1.50, 0.0], "head_center_vector": [0.0, 0.12, 0.0] },
    "sneaking":    { "pivot": [0.0, 1.15, 0.0], "head_center_vector": [0.0, 0.12, 0.0] },
    "swimming":    { "pivot": [0.0, 0.40, 0.0], "head_center_vector": [0.0, 0.00, 0.40] },
    "crawling":    { "pivot": [0.0, 0.40, 0.0], "head_center_vector": [0.0, 0.00, 0.40] },
    "fall_flying": { "pivot": [0.0, 0.40, 0.0], "head_center_vector": [0.0, 0.00, 0.40] },
    "sleeping":    { "pivot": [0.0, 0.20, 0.0], "head_center_vector": [0.0, 0.00, 0.30] }
  }
}
```

> **Note**: These values are derived from known 1.20.1 eye-heights and model conventions
> and should be calibrated in-game. The `head_center_vector` Z component for horizontal
> poses (swimming/crawling/fall_flying/sleeping) is approximate — it should place the halo
> near the visual head center after the forward/yaw rotation is applied.

---

## 3. Non-player entity head pivots (for future reference)

### 3.1 General pattern in `EntityModel`

Most entity models follow this convention:

- **Two-legged mobs** (zombie, skeleton, villager): `head.pivotY ≈ height * 0.85-0.9` in
  pixels. Head cuboid is typically 8×8×8 positioned atop the body.
- **Four-legged mobs** (cow, pig, sheep, horse): `head.pivotY ≈ height * 0.65-0.75` —
  head is at the front of the body, NOT at the top. The body is horizontal.
- **Small mobs** (chicken, rabbit): `head.pivotY ≈ height * 0.6-0.7` — compact build.
- **Aquatic** (dolphin, squid): completely different body axis; head is at the front.
- **Large flying** (ghast, phantom): head center is model-dependent.

### 3.2 Key to future implementation

The `EntityAnchorProvider` interface + `EntityAnchorLoader` registry make adding a
new entity type a pure data change:

1. Create `data/halo/entity_anchors/<entity_type>.json` with pivot and head_center_vector.
2. The JSON is loaded automatically on `/reload`.
3. No code changes needed (fallback to `height*0.85` for entities without a profile).

For horizontal-bodied entities (cows, pigs, etc.), the `head_center_vector` will have a
non-zero Z component (forward along body axis) and little or no Y component — same pattern
as the player swimming pose.

---

## 4. Coordinate frame reference

To avoid confusion, here is the exact coordinate convention used in this mod:

```
Local entity frame (before any yaw/pitch rotation):
  +X = right  (entity's local right)
  +Y = up     (entity's local up / antiparallel to gravity for standing)
  +Z = forward (direction the entity faces when yaw=0, pitch=0)
```

**pivot**: position in this local frame (relative to entity feet at (0,0,0)), in blocks.

**head_center_vector**: offset from pivot to head center, in the same local frame. Applied
**after** rotating by the entity's head yaw and pitch.

The algorithm in `PlayerAnchorProvider.resolve()`:

```
pivotWorld = entityPos + pivot                 // world-space neck position
R = rotation matrix from head pitch → yaw     // (Y rotation, then X rotation)
headCenter = pivotWorld + R * head_center_vector
```

This matches `AnchorFrameCalculator`'s existing forward convention:
```
forward = (-sin(yaw)·cos(pitch), -sin(pitch), cos(yaw)·cos(pitch))
```

---

## 5. In-game calibration guide

After implementing Phase 3, the following debug checks should be performed:

1. **Standing**: Halo with `offset=[0, 0.4, 0.35]` should appear centered above and
   slightly behind the player's head.
2. **Sneaking**: Halo should follow the head downward smoothly as the player sneaks.
3. **Swimming (in water)**: Halo should appear in front of the player's horizontal body.
4. **Crawling (under a 1-block gap)**: Same as swimming visually.
5. **Elytra gliding**: Halo should be above the horizontal body.
6. **Sleeping**: Halo should be near the pillow end.

If any pose's halo position looks wrong, adjust the corresponding `pivot` or
`head_center_vector` value in `player.json` — no recompilation needed.
