# Halo Definition JSON Field Reference

This document provides a complete description of the Halo mod's halo definition JSON format, covering every field, optional value, default, and usage pattern.

> **Prerequisite**: If you haven't read the quickstart tutorial yet, we recommend starting with the [Quickstart](quickstart.md) to learn the basic structure.

---

## Table of Contents

- [Top-Level Structure](#top-level-structure)
- [Layer](#layer)
- [Primitive & Glow](#primitive--glow)
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
  "orientation_mode": "locked",
  "allow_angular_momentum": true,
  "sync_offset": [0.0, 0.0, 0.0],
  "layers": [ ... ],
  "animation": { ... },
  "hide_on_sleep": false,
  "positioning": { ... },
  "damping": { ... }
}
```

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

- **Type**: Array of layer objects
- **Required**: Yes (or provide legacy `shape`)
- **Description**: Defines the halo's visual composition. Each layer is an independent rendering unit. See [Layer](#layer) for details.

### `animation`

- **Type**: Animation object
- **Required**: No
- **Description**: Halo-level overall animation. Has the same structure as per-layer animation. Set to `{}` for no animation. See [Animation System](#animation-system) for details.

### `hide_on_sleep`

- **Type**: Boolean
- **Required**: No
- **Default**: `false`
- **Description**: When set to `true`, the halo temporarily stops rendering while the entity is sleeping (player in bed, fox resting, etc.). Rendering resumes automatically when the entity wakes up. When `false`, the halo always renders regardless of sleep state.

### `positioning`

- **Type**: Object
- **Required**: Yes
- **Description**: The halo's overall position offset and scale. See [Positioning](#positioning) for details.

### `damping`

- **Type**: Object
- **Required**: Yes
- **Description**: Physics follow parameters. See [Damping](#damping) for details.

---

## Layer

Each layer is defined as follows:

```json
{
  "id": "optional_layer_name",
  "position": [0.0, 0.0, 0.0],
  "rotation": [0.0, 0.0, 0.0],
  "scale": 1.0,
  "glowing": true,
  "animation": { ... },
  "primitive": { ... }
}
```

### `id`

- **Type**: String
- **Required**: No
- **Description**: Optional name for the layer, reserved for future animation grouping. Currently has no effect.

### `position`

- **Type**: `[x, y, z]` triple (blocks)
- **Required**: No
- **Default**: `[0, 0, 0]`
- **Description**: This layer's position offset in the halo's local space. `[0, 0, 0]` represents the halo **origin** — this origin is aligned with the position computed by damping physics. The layer's actual position in space = damped follow position + the offset defined here.

  When designing multi-layer halos, adjusting each layer's Y value creates spatial depth — layers with larger Y values appear visually "higher".

### `rotation`

- **Type**: `[pitch, yaw, roll]` triple (degrees)
- **Required**: No
- **Default**: `[0, 0, 0]`
- **Description**: This layer's initial rotation, using Euler YXZ order.

### `scale`

- **Type**: Float
- **Required**: No
- **Default**: `1.0`
- **Description**: Uniform scale multiplier for this layer. `1.0` is the original size. This scale multiplies with `positioning.scale` — final size = layer `scale` × global `positioning.scale`.

### `glowing`

- **Type**: Boolean
- **Required**: No
- **Default**: `true` (when absent)
- **Description**: Controls whether this layer renders at full brightness (unaffected by ambient lighting). When set to `false`, the layer is affected by in-game lighting (brighter during daytime, darker at night).

### `animation`

- **Type**: Animation object
- **Required**: No
- **Description**: This layer's independent animation. Has the same structure as top-level animation. Set to `{}` for no animation on this layer. See [Animation System](#animation-system) for details.

### `primitive`

- **Type**: Object
- **Required**: Yes
- **Description**: This layer's rendering primitive. See [Primitive & Glow](#primitive--glow) for details.

---

## Primitive & Glow

Two primitive types are currently supported:

- **`billboard`**: A flat quad with no thickness
- **`ring`**: A cylindrical ring with radius and axial width, supporting separate inner and outer textures

More primitive types will be supported in the future:

- **`mesh`**: 3D mesh model

### Billboard Primitive

```json
{
  "type": "billboard",
  "texture": "halo:textures/halo/example.png",
  "size": [0.5, 0.5],
  "glow": { ... }
}
```

| Field | Type | Required | Description |
|------|------|------|------|
| `type` | String | **Yes** | Fixed value `"billboard"` |
| `texture` | String | **Yes** | Texture resource path. Format: `namespace:textures/halo/filename.png`. The path is relative to `assets/`, where the **namespace** corresponds to a folder name under `assets/`. For example, in `halo:textures/halo/example.png`, `halo` is the namespace and maps to the `assets/halo/` folder. When using your own namespace (e.g. `mypack`), place files under `assets/mypack/` and write the path as `mypack:textures/halo/example.png`. |
| `size` | `[width, depth]` | **Yes** | The quad's dimensions `[width, depth]` on the XZ plane (in blocks). Usually square, e.g. `[0.5, 0.5]`. |
| `glow` | Object | No | Optional glow overlay layer. No glow effect when absent. **Currently non-functional in this version; pending a fix.** |

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

### Glow Layer

> **⚠️ Note**: The glow layer currently has a known bug and is pending a fix.

The glow layer renders with additive blending on top of the base texture, producing a self-illumination effect:

```json
{
  "texture": "halo:textures/halo/example_glow.png",
  "size": [0.5, 0.5],
  "color": 16766720,
  "alpha": 0.8
}
```

| Field | Type | Required | Description |
|------|------|------|------|
| `texture` | String | **Yes** | Glow texture resource path. |
| `size` | `[width, depth]` | **Yes** | Glow quad dimensions (blocks). Usually matches the base texture. |
| `color` | Integer | **Yes** | Glow color as a packed 0xRRGGBB integer. E.g. `16766720` (0xFFCC00, gold). |
| `alpha` | Float | **Yes** | Base opacity, range 0.0–1.0. |

---

## Animation System

The animation system uses mathematical functions to describe how position offsets and rotation angles change over time. Animations can be defined at the top level (`animation`, affecting the entire halo) or at the layer level (`layers[i].animation`, affecting only that layer).

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
  }
}
```

- **`offset`**: Position offset animation, organized into three optional axis arrays: `x`, `y`, `z`
- **`rotation`**: Rotation animation, organized into three optional axis arrays: `yaw`, `pitch`, `roll`

Each axis value is an **array of animation term objects**. Multiple terms on the same axis are **summed together** (linear superposition), so you can combine multiple functions to produce complex motion. The entire animation block, each group, and each axis are all optional — omit what you don't need.

### Units

| Animation Type | Axes | Unit |
|----------|-----|------|
| `offset` | `x`, `y`, `z` | **Blocks** (meters) |
| `rotation` | `yaw`, `pitch`, `roll` | **Degrees** |

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

> **Note**: `multi_billboard` has no concept of position — it simply stacks all layers together. Unlike the new `layers` format, it does not support per-layer `position` offsets, independent animations, glow, or other advanced features.

> **Recommendation**: New halo definitions should use the `layers` format, which is more feature-rich (supporting per-layer independent animations, glow, rotation, etc.). The legacy format exists only for compatibility with existing definition files.

---

*Halo Mod v1.0.1 · [GitHub](https://github.com/AzusaKe/Halo)*
