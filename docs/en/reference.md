# Halo Definition JSON Field Reference

This document provides a complete description of the Halo mod's halo definition JSON format, covering every field, optional value, default, and usage pattern.

> **Prerequisite**: If you haven't read the quickstart tutorial yet, we recommend starting with the [Quickstart](quickstart.md) to learn the basic structure.

---

## Table of Contents

- [Top-Level Structure](#top-level-structure)
- [Group (Layer)](#group-layer)
- [Primitive](#primitive)
- [Animation System](#animation-system)
- [Orientation Mode](#orientation-mode)
- [Positioning](#positioning)
- [Damping](#damping)
- [Common Patterns & Techniques](#common-patterns--techniques)
- [Appendix: Legacy Format](#appendix-legacy-format)

---

## Top-Level Structure

A halo definition JSON file contains the following top-level fields:

```json
{
  "id": "halo:example",
  "version": "1.0.10",
  "orientation_mode": "locked",
  "allow_angular_momentum": true,
  "sync_offset": [0.0, 0.0, 0.0],
  "layers": [ ... ],
  "animation": { ... },
  "hide_on_sleep": false,
  "display_in_invisible": false,
  "positioning": { ... },
  "damping": { ... }
}
```

### `version`

- **Type**: String
- **Required**: No
- **Default**: `"1.1.0"` (when absent; older versions remain supported)
- **Format**: Semantic version `major.minor.patch`, e.g. `"1.0.10"`
- **Description**: The schema version this definition was written for. The parser uses this to handle format changes across mod versions. If the definition's version is higher than the mod supports, a warning is logged and unrecognized fields are skipped — rendering may fail. You can safely omit this field for definitions written for the current mod version.

### `id`

- **Type**: String
- **Required**: Yes
- **Format**: `namespace:name`, e.g. `"halo:yourhalo"`
- **Description**: The halo's unique identifier. The namespace maps to a folder under `assets/` — `halo:yourhalo` means the definition file lives under `assets/halo/`. You can use your own namespace (e.g. `mypack:myhalo`), in which case the definition file should be placed under `assets/mypack/`. When referencing via `/halo show <entity> <name>`, the mod automatically fills in the `halo:` prefix.

### `orientation_mode`

- **Type**: String enum
- **Required**: No
- **Values**: `"locked"` (default), `"free"`, `"sync"`
- **Description**: Controls how the halo rotates relative to the entity's head. See [Orientation Mode](#orientation-mode) for details.

### `allow_angular_momentum`

- **Type**: Boolean
- **Required**: No
- **Default**: `false`
- **Description**: When set to `true`, adds angular momentum inertia to the halo's orientation. When enabled, the orientation computed by `locked` and `free` modes becomes a target — the actual orientation chases it with inertial damping, producing a visible physical "lag" when the player turns quickly. Only effective in `locked` and `free` modes; has no effect in `sync` mode. The damping speed and angle clamp are controlled by `damping.angularMomentumFactor` and `damping.maxAngularMomentumDegrees`.

### `sync_offset`

- **Type**: `[yaw, pitch, roll]` triple (degrees)
- **Required**: No
- **Default**: `[0, 0, 0]`
- **Description**: Only meaningful when `orientation_mode` is `"sync"`. Defines a fixed rotational offset between the halo and the entity's head. Uses Euler YXZ order.

### `layers`

- **Type**: Array of group objects
- **Required**: Yes (or provide legacy `shape`)
- **Description**: Defines the halo's visual composition. Each element in the array is a **group** that can contain multiple primitives and nested child groups, sharing a common transform. See [Group (Layer)](#group-layer) for details. The old single-`primitive` format is still supported for backward compatibility.

### `animation`

- **Type**: Animation object
- **Required**: No
- **Description**: Halo-level overall animation. Has the same structure as per-layer animation. Set to `{}` for no animation. This animation acts as an **implicit root group**: its `offset`/`rotation`/`scale` apply to the whole halo, while its `alpha`/`glow` become the initial inherited values for every top-level group (whole-halo fades and overall scale animations can be written directly here). See [Animation System](#animation-system) for details.

### `hide_on_sleep`

- **Type**: Boolean
- **Required**: No
- **Default**: `false`
- **Description**: When set to `true`, the halo temporarily stops rendering while the entity is sleeping (player in bed, fox resting, etc.). Rendering resumes automatically when the entity wakes up. When `false`, the halo always renders regardless of sleep state.

### `display_in_invisible`

- **Type**: Boolean
- **Required**: No
- **Default**: `false`
- **Description**: When set to `true`, the halo continues rendering while the entity is invisible (e.g. invisibility potion, invisibility command). When `false` (default), the halo temporarily stops rendering while the entity is invisible and automatically resumes when the invisibility effect ends. The invisibility check is performed once per game tick (20 TPS) for performance, so there may be up to one tick of latency when the effect is applied or removed.

### `positioning`

- **Type**: Object
- **Required**: Yes
- **Description**: The halo's overall position offset and scale. See [Positioning](#positioning) for details.

### `damping`

- **Type**: Object
- **Required**: Yes
- **Description**: Physics follow parameters. See [Damping](#damping) for details.

---

## Group (Layer)

Each element in the `layers` array is a **group** — a transform node that can contain multiple primitives and nested child groups. This is a scene-graph structure: child groups inherit their parent's position, rotation, scale, and animation.

```json
{
  "id": "optional_group_name",
  "position": [0.0, 0.0, 0.0],
  "rotation": [0.0, 0.0, 0.0],
  "scale": 1.0,
  "glowing": true,
  "inherit_alpha": true,
  "inherit_glow": true,
  "animation": { ... },
  "primitives": [ ... ],
  "children": [ ... ]
}
```

### `id`

- **Type**: String
- **Required**: No
- **Description**: Optional name for the group, reserved for future animation grouping. Currently has no effect.

### `position`

- **Type**: `[x, y, z]` triple (blocks)
- **Required**: No
- **Default**: `[0, 0, 0]`
- **Description**: This group's position offset relative to its parent group (or the halo origin for top-level groups). `[0, 0, 0]` means no offset from the parent.

  Child groups inherit their parent's transform — a child's `[0, 0.02, 0]` offset is applied in the parent's rotated/scaled coordinate space.

### `rotation`

- **Type**: `[yaw, pitch, roll]` triple (degrees)
- **Required**: No
- **Default**: `[0, 0, 0]`
- **Description**: This group's initial rotation relative to its parent, using Euler YXZ order.

### `scale`

- **Type**: Float
- **Required**: No
- **Default**: `1.0`
- **Description**: Uniform scale multiplier for this group. Multiplies with the parent group's scale — final scale = this group's `scale` × all ancestor `scale` values × `positioning.scale`.

### `glowing`

- **Type**: Boolean
- **Required**: No
- **Default**: `true` (when absent)
- **Description**: Controls the self-illumination mode for primitives in this group. When `true` (default), primitives render at full brightness and their brightness can be modulated by the `animation.glow` channel (self-illumination intensity). When `false`, this group's primitives follow the in-game ambient light (brighter during daytime, darker at night) and `animation.glow` does not affect this group's own brightness. Regardless of `glowing`, the glow value is **inherited multiplicatively** down the scene tree (a descendant's effective glow = ancestor value × its own value).

### `inherit_alpha`

- **Type**: Boolean
- **Required**: No
- **Default**: `true`
- **Description**: Whether descendant groups inherit this group's animated alpha. When `true` (default), a descendant's effective alpha = inherited parent value × own alpha (during a transition the transition's `alpha` overrides the own animated alpha for groups with a transition segment, while groups without one freeze their own alpha at the trigger phase), flowing down the tree layer by layer. When `false`, this group's alpha only applies to its own primitives and the subtree restarts from 1.0 (fully opaque).

### `inherit_glow`

- **Type**: Boolean
- **Required**: No
- **Default**: `true`
- **Description**: Whether descendant groups inherit this group's animated glow. When `true` (default), a descendant's effective glow = inherited parent value × own glow, flowing down the tree layer by layer. When `false`, this group's glow only applies to its own primitives and the subtree restarts from 1.0 (full glow).

### `animation`

- **Type**: Animation object
- **Required**: No
- **Description**: This group's independent animation. Has the same structure as top-level animation. Child groups inherit this animation — a child's own animation is additive on top of the parent's. See [Animation System](#animation-system) for details.

### `primitives`

- **Type**: Array of primitive objects
- **Required**: No (but the group should have at least `primitives` or `children` to be meaningful)
- **Description**: The rendering primitives within this group. All primitives share the group's transform. See [Primitive](#primitive) for details on each primitive type.

  **Backward compatibility**: The old single-`primitive` field (an object instead of an array) is still supported and automatically treated as a one-element `primitives` array.

### `children`

- **Type**: Array of group objects
- **Required**: No
- **Description**: Nested child groups. Each child group inherits the parent's full transform (position, rotation, scale, animation). Child groups have the same structure as top-level groups — they can contain their own `primitives`, `animation`, and further nested `children`.

  This enables building hierarchical scene graphs. For example, a parent group can define a shared bobbing animation, and child groups add their own rotation on top without redefining the bobbing.

---

## Primitive

Three primitive types are currently supported:

- **`billboard`**: A flat quad with no thickness
- **`ring`**: A cylindrical ring with radius and axial width, supporting separate inner and outer textures

- **`mesh`**: Static resource-pack OBJ geometry with an optional grayscale alpha mask

### Mesh Primitive

`mesh` requires `model` and `texture`. By default, required `size:[x,y,z]` fits local bounds in blocks by axis.
With `preserve_proportions: true` (default false), `size` is optional and ignored; primitive `scale` (default 1)
uniformly multiplies the authored coordinates. Both modes preserve the exported origin; groups control transforms and animation.
Optional `material.effects` supports one `alpha_mask` with LINEAR/STEP transfer and U/V animation;
base/mask dimensions must be equal or uniform integer multiples/divisors.
See [OBJ meshes and alpha masks](mesh.md) for the complete JSON, defaults, coordinates, export and reload rules.

### Billboard Primitive

```json
{
  "type": "billboard",
  "texture": "halo:textures/halo/example.png",
  "size": [0.5, 0.5],
  "face_camera": false
}
```

| Field | Type | Required | Description |
|------|------|------|------|
| `type` | String | **Yes** | Fixed value `"billboard"` |
| `texture` | String | **Yes** | Texture resource path. Format: `namespace:textures/halo/filename.png`. The path is relative to `assets/`, where the **namespace** corresponds to a folder name under `assets/`. For example, in `halo:textures/halo/example.png`, `halo` is the namespace and maps to the `assets/halo/` folder. When using your own namespace (e.g. `mypack`), place files under `assets/mypack/` and write the path as `mypack:textures/halo/example.png`. |
| `size` | `[width, depth]` | **Yes** | The quad's dimensions `[width, depth]` on the XZ plane (in blocks). Usually square, e.g. `[0.5, 0.5]`. |
| `face_camera` | Boolean | No | When `true`, the quad is always drawn fully facing the camera (plane perpendicular to the view direction); no animation rotation (idle or transition) can override this orientation. Offset and scale animations still apply. Default `false`. |

### Ring Primitive

```json
{
  "type": "ring",
  "texture": "halo:textures/halo/example_outer.png",
  "inner_texture": "halo:textures/halo/example_inner.png",
  "size": [0.35, 0.08],
  "segments": 32
}
```

| Field | Type | Required | Description |
|------|------|------|------|
| `type` | String | **Yes** | Fixed value `"ring"` |
| `texture` | String | **Yes** | Outer surface texture resource path. May also be written as `outer_texture`. |
| `inner_texture` | String | No | Inner surface texture resource path. When omitted, both surfaces share `texture`. When a single texture is provided, both sides are visible; when separate textures are provided, each texture is only visible from its corresponding side (face culling). |
| `size` | `[radius, width]` | **Yes** | `[radius, cylinder_width]` (blocks). `radius` is the ring radius; `width` is the axial cylinder width (Y-axis height). |
| `segments` | Integer | No | Polygon segment count, default `32`. Higher values produce a smoother ring but increase vertex count. |

**Rotation convention**: When `rotation` is `[0, 0, 0]`, the ring lies flat on the XZ plane (symmetry axis along -Y), matching the billboard default orientation. The texture seam is at the +X axis direction.

**Texture mapping**: U wraps around the circumference (0→1 = one full revolution, seam at +X). V spans the cylinder width (0 = top `+width/2`, 1 = bottom `-width/2`). The texture connects end-to-end in the U direction to form a closed loop.

---

## Animation System

The animation system uses mathematical functions to describe how position offsets and rotation angles change over time. Animations can be defined at the top level (`animation`, affecting the entire halo) or at the layer level (`layers[i].animation`, affecting only that layer). The top-level `alpha`/`glow` act as an **implicit root group** that every top-level group inherits multiplicatively.

> **The Power of Fourier Series**: Since multiple animation terms on the same axis are summed together, and `sin` and `cos` form the basis of Fourier series, you can theoretically approximate **any periodic motion** by superimposing sine/cosine terms with different frequencies, amplitudes, and phases — from simple bobbing to complex trajectories, even a tiny stick figure dancing.

### Structure

```json
{
  "offset": {
    "x": [ ... ],
    "y": [ ... ],
    "z": [ ... ]
  },
  "rotation": {
    "yaw": [ ... ],
    "pitch": [ ... ],
    "roll": [ ... ]
  },
  "scale": {
    "x": [ ... ],
    "y": [ ... ],
    "z": [ ... ]
  },
  "alpha": [ ... ],
  "glow": [ ... ]
}
```

- **`offset`**: Position offset animation, organized into three optional axis arrays: `x`, `y`, `z`
- **`rotation`**: Rotation animation, organized into three optional axis arrays: `yaw`, `pitch`, `roll`
- **`scale`**: Scale animation, organized into three optional axis arrays: `x`, `y`, `z`. Terms are **delta factors** added to a base of 1.0 (e.g. `sin(A=0.1)` oscillates between 0.9 and 1.1). Omitted axes default to 1.0 (no scaling). Scale animation is **multiplicative** — in a child group, it compounds with the parent's scale: final scale = parent scale × child scale × animated scale.
- **`alpha`**: Opacity animation (**scalar channel**). Omitted → 1.0 (fully opaque); when terms are present, the result is `clamp(sum(terms), 0, 1)` — 0 means fully transparent (layer invisible), 1 means fully opaque. The value is **multiplied** with the alpha inherited from the parent (final alpha = parent alpha × own alpha; during a transition the transition's `alpha` overrides it for groups with a transition segment, while groups without one freeze their own alpha at the trigger phase). Use it for fades and overall brightness pulses.
- **`glow`**: Self-illumination intensity animation (**scalar channel**). Omitted → 1.0 (full glow); when terms are present, the result is `clamp(sum(terms), 0, 1)` — 0 means no glow (dark), 1 means full glow. The value is **multiplied** with the glow inherited from the parent (`final glow = parent glow × own glow`). When the group's `glowing` is `true` (default), the final glow directly becomes the primitive's own brightness; when `glowing=false` the group's brightness follows ambient light, but its glow value is still passed to descendants multiplicatively.

Each axis value is an **array of animation term objects**; `alpha`/`glow` are scalar channels whose values are flat term arrays. Multiple terms on the same channel are **summed together** (linear superposition), so you can combine multiple functions to produce complex motion. The entire animation block, each group, and each channel are all optional — omit what you don't need.

> **Group inheritance**: `offset`/`rotation`/`scale` compose down the scene tree (child groups inherit the parent's transform). `alpha` and `glow` are **inherited multiplicatively** as well — each group's effective value = inherited value × its own animated value (during a transition the transition's `alpha` overrides the own animated alpha for groups with a transition segment — `final alpha = parent alpha × transition alpha` — while groups without one freeze their own alpha at the trigger phase — `final alpha = parent alpha × own alpha`; glow: `final glow = parent glow × own glow`). Groups that omit a channel use 1.0 (no change to the inherited value), so defining an `alpha` animation on a parent group fades the whole subtree together. Glow flows down the tree regardless of the `glowing` flag — the flag only selects whether a group's own primitives use glow or ambient brightness. For finer control, set `inherit_alpha`/`inherit_glow` to `false` on any group to cut the chain at that boundary (the subtree restarts from 1.0); for fully independent parts, simply define two sibling trees.

### Units

| Animation Type | Axes | Unit |
|----------|-----|------|
| `offset` | `x`, `y`, `z` | **Blocks** (meters) |
| `rotation` | `yaw`, `pitch`, `roll` | **Degrees** |
| `scale` | `x`, `y`, `z` | **Delta factor** (1.0 = no change) |
| `alpha` | — (scalar) | **Factor** (1.0 = opaque, range [0, 1]) |
| `glow` | — (scalar) | **Factor** (1.0 = full glow, range [0, 1]) |

### Animation Functions

Three animation functions are available:

#### `sin` — Sine Oscillation

```json
{ "function": "sin", "A": 0.01, "omega": 0.5, "phi": 0.0 }
```

| Parameter | Required | Default | Description |
|------|------|--------|------|
| `A` | **Yes** | — | Amplitude. |
| `omega` | **Yes** | — | Angular frequency (unit: π). `omega = 1` gives a period of 2 seconds. Larger values = faster oscillation. |
| `phi` | No | `0.0` | Phase offset (radians). Used to stagger oscillations across multiple layers. |

**Formula**: `value(t) = A · sin(omega · π · t + phi)`

#### `cos` — Cosine Oscillation

```json
{ "function": "cos", "A": 0.01, "omega": 0.5, "phi": 0.0 }
```

Parameters and formula are the same as `sin`, with `sin` replaced by `cos`. `cos` starts at the amplitude peak at `t=0`, while `sin` starts from zero.

**Formula**: `value(t) = A · cos(omega · π · t + phi)`

#### `linear` — Linear Change

```json
{ "function": "linear", "start": 0.0, "speed": 45.0 }
```

| Parameter | Required | Default | Description |
|------|------|--------|------|
| `start` | No | `0.0` | Initial value at `t=0`. |
| `speed` | **Yes** | — | Rate of change (units/second). Positive = forward, negative = reverse. |

**Formula**: `value(t) = start + speed · t`

**Typical usage**:
- Continuous rotation: `{ "function": "linear", "speed": 45 }` → rotates clockwise 45° per second
- Slow reversal: `{ "function": "linear", "speed": -6 }` → rotates counter-clockwise 6° per second

---

## Orientation Mode

Orientation mode controls how the halo rotates relative to the entity in space. Understanding orientation modes requires first distinguishing between two sets of concepts:

**Conceptual tools within the halo definition** (only apply in the halo definition's relative space):
- **Halo Origin**: The reference center for layer `position` offsets. Each layer's `position` is defined relative to this origin.
- **Halo Normal**: Default direction is straight down (-Y). Used to determine the halo's "front" facing.

**Computed final pose** (determined jointly by damping physics and orientation mode):
- **Halo Position**: The halo's final spatial coordinates in the game world.
- **Halo Orientation**: The halo's final 3D rotation direction in the game world.

**The alignment process during rendering**: First, the halo position and orientation are computed. Then the halo origin is moved to the halo position, and the entire halo is rotated until the halo normal is parallel to the halo orientation — producing the final object to be rendered. Origin/normal and position/orientation are two different layers of abstraction and should not be conflated.

### `locked` (default)

- The halo orientation **always points toward the player's head center**
- Uses the player head's **spherical poles** (upper and lower poles) to determine the halo's rotation around its normal — the halo points stably toward these poles like a compass and won't rotate freely around its normal axis
- When `allow_angular_momentum` is `true`, the orientation toward the head center becomes a target — the actual orientation chases it with angular momentum inertia
- Suitable for halos that need a stable orientation, such as the default `ring_default`

### `free`

- The halo orientation **also always points toward the player's head center**
- However, the halo's **rotation around its normal axis is uncontrolled** — it can freely rotate around the normal
- When `allow_angular_momentum` is `true`, angular momentum inertia is also applied to the orientation
- Suitable for halos where the specific rotation angle doesn't matter

### `sync`

- The **initial pose** is determined by the first frame of `locked` mode
- After that, the halo's rotation **fully follows the entity's head** — when the player turns their head, the halo rotates in sync
- A fixed rotational offset from the head can be set via `sync_offset`
- `allow_angular_momentum` has no effect on this mode
- Suitable for halos that need to maintain a fixed relative angle to the entity (inspired by Blue Archive)

### Mode Comparison

| Property | `locked` | `free` | `sync` |
|------|----------|--------|--------|
| Points toward head center | ✓ | ✓ | ✗ (follows head rotation) |
| Rotation around normal | Stabilized by spherical poles | Uncontrolled | Follows head |
| Angular momentum inertia | ✓ | ✓ | ✗ |
| Initial pose source | — | — | First frame of `locked` |
| `sync_offset` applies | ✗ | ✗ | ✓ |

---

## Positioning

```json
{
  "offset": [0.0, 0.4, 0.35],
  "scale": 1.5
}
```

| Field | Type | Required | Default | Description |
|------|------|------|--------|------|
| `offset` | `[x, y, z]` | **Yes** | — | The halo's overall position offset from the entity's head anchor point (blocks). Positive `y` = up, positive `z` = behind. |
| `scale` | Float | No | `1.0` | Overall scale multiplier for the halo. Multiplies with each layer's `scale`. |

**Typical value**: `offset: [0, 0.4, 0.35]` places the halo 0.4 blocks above the head and 0.35 blocks behind — the recommended default for humanoid entities.

---

## Damping

Damping controls how smoothly the halo follows the entity's movement. The mod uses a frame-rate-independent exponential decay algorithm.

```json
{
  "linearFactor": 0.5,
  "angularFactor": 0.1,
  "maxLinearDistance": 0.5,
  "maxAngularDegrees": 180.0,
  "angularMomentumFactor": 0.3,
  "maxAngularMomentumDegrees": 45.0
}
```

| Field | Type | Required | Default | Description |
|------|------|------|--------|------|
| `linearFactor` | Float | **Yes** | — | Linear follow speed. `0` = no follow (halo stays in place), `1` = instant follow (halo sticks tightly to the entity). Typical range: 0.3–0.8. |
| `angularFactor` | Float | **Yes** | — | Angular follow speed. `0` = no rotational follow, `1` = instant rotational follow. Usually set lower than `linearFactor` (e.g. 0.1) for softer rotation. |
| `maxLinearDistance` | Float | **Yes** | — | Linear clamp distance (blocks). When the halo reaches this distance from the entity, it is **clamped** at this value — it won't drift further. As the speed eases, the halo naturally rebounds to its normal follow position. |
| `maxAngularDegrees` | Float | **Yes** | — | Angular clamp value (degrees). When the halo's angle relative to the entity reaches this value, it is **clamped** at this angle — it won't rotate further. As the speed eases, it naturally rebounds. |
| `angularMomentumFactor` | Float | No | `0.3` | Angular momentum damping factor. Only effective when `allow_angular_momentum` is `true`. `0` = frozen (halo orientation doesn't chase the target), `1` = instant snap to target (no inertia). Typical range: 0.2–0.5. |
| `maxAngularMomentumDegrees` | Float | No | `45.0` | Maximum angular momentum deviation (degrees). When the angle between the halo's actual orientation and the target orientation exceeds this value, it is **clamped** — preventing the halo from deviating too far during rapid turns. |

> **Clamping is not teleporting**: Clamping means the halo is "stuck" at the maximum distance/angle, not that it jumps to the entity's side. Teleportation (e.g. cross-dimension) has a separate detection mechanism that instantly snaps the halo back. Clamping prevents the halo from drifting too far during normal fast movement.

**Recommended configurations**:

| Style | `linearFactor` | `angularFactor` | `maxLinearDistance` | Effect |
|------|---------------|-----------------|---------------------|------|
| Airy & floaty | 0.3–0.4 | 0.05–0.1 | 0.5–1.0 | Noticeable lag, drifting feel |
| Balanced | 0.5–0.6 | 0.1–0.15 | 0.5 | Moderate tracking |
| Snug & tight | 0.8–1.0 | 0.2 | 0.3–0.5 | Halo nearly glued to entity, good for HUD styles |

---

## Common Patterns & Techniques

### 1. Gentle Bobbing

Adding a Y-axis sine oscillation to every layer is the most basic and commonly used animation. It makes the halo feel "alive":

```json
"animation": {
  "offset": {
    "y": [{ "function": "sin", "A": 0.01, "omega": 0.5 }]
  }
}
```

### 2. Multi-Layer Halo + Phase Staggering

Stack multiple layers, each with a different `phi` to stagger their bobbing rhythms, creating a more organic composite motion:

```json
"layers": [
  {
    "position": [0, 0.000, 0],
    "animation": {
      "offset": {
        "y": [{ "function": "sin", "A": 0.01, "omega": 0.5 }]
      }
    },
    ...
  },
  {
    "position": [0, 0.015, 0],
    "animation": {
      "offset": {
        "y": [{ "function": "sin", "A": 0.01, "omega": 0.5, "phi": 0.5 }]
      }
    },
    ...
  },
  {
    "position": [0, 0.045, 0],
    "animation": {
      "offset": {
        "y": [{ "function": "sin", "A": 0.01, "omega": 0.5, "phi": 1.0 }]
      }
    },
    ...
  }
]
```

> Each layer's Y `position` increases gradually (0 → 0.015 → 0.045), creating spatial depth. Incrementing `phi` across layers makes their bobbing out of sync.

### 3. Continuous Rotation

Use the `linear` function to make a layer rotate continuously around its normal. Different layers with different speeds and directions produce complex visual effects:

```json
"animation": {
  "rotation": {
    "yaw": [{ "function": "linear", "speed": -0.1 }]
  }
}
```

> Negative `speed` means counter-clockwise. Combine multiple layers each rotating at different speeds for a multi-layered spinning halo.

### 4. HUD-Style Halo

Use layer `position` offsets to place elements at different locations on the halo plane (rather than all at the origin), combined with different animations, to create a sci-fi HUD-like effect:

```json
{
  "position": [0.22, 0.0, 0.205],
  "animation": {
    "rotation": {
      "yaw": [{ "function": "linear", "speed": 45 }]
    }
  },
  ...
}
```

> Offset the element to `[0.22, 0, 0.205]` (a corner area of the halo plane) and have it spin rapidly.

### 5. Texture Orientation

A quick reminder of the texture orientation rules: the up-down direction of a texture is always up-down in-game (never flips). When viewed from the player's head outward, the texture appears correct; when viewed from the outside, it is horizontally mirrored. Symmetric designs are unaffected.

### 6. Grouping and Transform Inheritance

Use groups to share transforms across multiple primitives, and `children` to build hierarchical scene graphs where child groups inherit their parent's transform:

```json
"layers": [
  {
    "id": "outer-rings",
    "position": [0, 0, 0],
    "scale": 1.5,
    "animation": {
      "offset": {
        "y": [{ "function": "sin", "A": 0.01, "omega": 0.5 }]
      }
    },
    "primitives": [
      { "type": "billboard", "texture": "halo:textures/halo/ring_00.png", "size": [0.5, 0.5] },
      { "type": "billboard", "texture": "halo:textures/halo/ring_01.png", "size": [0.5, 0.5] }
    ],
    "children": [
      {
        "id": "inner-ring",
        "position": [0, 0.02, 0],
        "animation": {
          "rotation": {
            "pitch": [{ "function": "linear", "speed": 0.05 }]
          }
        },
        "primitives": [
          { "type": "billboard", "texture": "halo:textures/halo/ring_2.png", "size": [0.5, 0.5] }
        ]
      }
    ]
  }
]
```

> The two outer ring primitives share the parent group's position, scale, and bobbing animation. The inner ring child inherits all of those, then adds its own Y offset and pitch rotation on top — without needing to redefine the bobbing animation.

### 7. Startup / Shutdown Transition Animations

Halos can play a multi-segment fade-in animation when appearing (`/halo show`, wake up, become visible) and a fade-out when disappearing (`/halo hide`, sleep, become invisible). The derived endpoints of a transition are automatically aligned to the actual phase of the idle animation (the `animation` field) at the trigger moment, so neither hand-off jumps.

```json
{
  "startup": {
    "segments": [
      { "duration": 0.5, "easing": "ease_out_cubic", "scale": { "from": [0, 0, 0] } }
    ],
    "id_overrides": {
      "inner-ring": {
        "segments": [
          { "duration": 0.3, "easing": "ease_out_cubic" },
          { "duration": 0.5, "easing": "ease_out_cubic", "scale": { "from": [0, 0, 0] } }
        ]
      }
    }
  },
  "shutdown": {
    "segments": [
      { "duration": 0.5, "easing": "ease_in_out_cubic", "scale": { "to": [0, 0, 0] } }
    ]
  }
}
```

| Field | Description |
|------|------|
| `startup` | Startup (fade-in) transition config. Every property must author `from` on the **first segment declaring it**; `to` may be omitted (aligned to the idle animation at the resume phase) |
| `shutdown` | Shutdown (fade-out) transition config. Every property must author `to` on the **last segment declaring it**; `from` may be omitted (inherits the idle state at the hide moment). If omitted entirely, startup is automatically reversed as the fade-out |
| `segments` | Default segments applied to all groups without an `id_overrides` entry |
| `id_overrides` | Per-group-id segment overrides, keyed by the group's `id` field |
| `segments[].duration` | Duration of this segment in seconds |
| `segments[].easing` | Easing curve: `linear`, `ease_out_cubic`, `ease_in_out_cubic` |
| `segments[].offset` | Offset animation `{ "from": [x,y,z], "to": [x,y,z] }` |
| `segments[].scale` | Scale animation `{ "from": [x,y,z] }` (`to` may be omitted, aligned to the idle value) |
| `segments[].alpha` | Opacity animation `{ "from": 0.0 }` (`to` may be omitted, aligned to the idle value). `opacity` is a deprecated alias kept for legacy packs |
| `segments[].rotation` | Rotation animation `{ "from": [yaw,pitch,roll], "to": [yaw,pitch,roll], "degrees": [dyaw,dpitch,droll] }` in YXZ Euler degrees. `degrees` forces a minimum signed travel: the element adds whole turns so the visual endpoint stays the authored `to` |

**Per-property duration/easing override**: Each property can override the segment's duration and easing:
```json
{ "duration": 0.5, "easing": "ease_out_cubic",
  "scale": { "from": [0,0,0], "duration": 0.8, "easing": "linear" } }
```

**Behavior**:
- Groups not listed in `id_overrides` and without a default `segments` array are not animated (instant appear/disappear)
- Leading gaps (before the first segment) hold at the next segment's `from` value; trailing gaps (after the last segment) hold at the animation's end value
- Derived transition endpoints are aligned to the idle animation's actual phase:
  - Startup: derived tail values (no explicit `to`) match `offset`/`scale`/`alpha`/`rotation` of the idle animation at the resume phase, so the transition hands off seamlessly
  - Shutdown: derived head values (no explicit `from`) match the idle animation's value at the hide phase, so hiding does not jump
  - Explicit `from`/`to` always win; alignment responsibility lies with the author
- If a halo is hidden mid-transition (e.g. `/halo hide`, sleep, or invisibility during its startup), the shutdown head is aligned to the exact per-group `offset`/`scale`/`alpha`/`rotation` values the renderer was drawing at the hide moment (recorded client-side by the renderer every frame — no server involvement) instead of the idle animation, so hiding mid-startup does not jump either
- Periodic animations (`animation` field) freeze at the trigger phase during the transition and resume at their actual phase afterwards; during a transition the transition's `alpha` overrides the layer's own alpha for groups with a transition segment, while groups without one freeze their own alpha at the trigger phase (glow always follows the frozen phase)
- An explicit `shutdown` plays exactly as written, never reversed; the startup queue is only reversed as a fade-out when no `shutdown` is defined
- A `shutdown` `from` left empty inherits the hide-moment idle state; a mid-segment `from` left empty inherits the previous segment's end value (forward cascade)
- Rotation interpolates per-axis in degrees — never quaternion slerp (which would take the shortest path and swallow whole turns). `degrees` (rotation only) sets a minimum signed travel per axis: `rotation: { "from": [0,0,0], "to": [30,0,0], "degrees": [90,0,0] }` spins +390°; a missing or zero `degrees` keeps plain `from`→`to` interpolation. The easing applies once across the whole travel, not per 360° segment
- During a transition every unoverridden channel freezes at the trigger phase: groups with a transition segment override the channels they define (rotation ending exactly at the frozen value, `degrees` adding whole turns) and hold the rest; groups without a transition segment (e.g. `ring_default`'s spinning hands) hold their whole idle pose — offset, rotation, scale and alpha — so the transition's last frame always equals the idle animation's first frame after the handoff, with no jump.  Children still inherit the parent's transition transform on top of their own frozen pose (`inherit_alpha` respected)

---

## Appendix: Legacy Format

Legacy definitions use the `shape` field instead of `layers`. The mod automatically converts them to the layer format on load. Two `shape.type` values are supported:

### `billboard`

```json
{
  "id": "halo:example",
  "shape": {
    "type": "billboard",
    "texture": "halo:textures/halo/example.png",
    "size": [0.5, 0.5]
  },
  "animation": { ... },
  "positioning": { ... },
  "damping": { ... }
}
```

Equivalent to the `layers` format with a single layer.

### `multi_billboard`

```json
{
  "id": "halo:example",
  "shape": {
    "type": "multi_billboard",
    "layers": [
      { "texture": "...", "size": [0.5, 0.5], "scale": 1.0 },
      { "texture": "...", "size": [0.5, 0.5], "scale": 1.0 }
    ]
  },
  ...
}
```

> **Note**: `multi_billboard` has no concept of position — it simply stacks all layers together. Unlike the new `layers` format, it does not support per-layer `position` offsets, independent animations, or other advanced features.

> **Recommendation**: New halo definitions should use the `layers` format, which is more feature-rich (supporting per-layer independent animations, rotation, etc.). The legacy format exists only for compatibility with existing definition files.

---

*Halo Mod v1.0.9 · [GitHub](https://github.com/AzusaKe/Halo)*
