# Task 07: Halo System Complete Planning

## Overview

Modular halo (ring) system with JSON-driven shape & animation, damping physics, and head-tracking positioning.

## Architecture Phases

### Phase A: Core System (Tasks 07-09)

- Data models for halo instances and damping state
- Damping physics engine (linear + angular)
- JSON configuration & command integration

### Phase B: Rendering & Animation (Tasks 10-12)

- Halo entity rendering (billboard quads + glow)
- Animation curve evaluation
- Head-tracking & interpolation

### Phase C: Polish & Integration (Tasks 13-14)

- GUI sliders for real-time config tweaks
- Model format compatibility layer
- Command system for arbitrary entity halos

---

## Detailed Task Breakdown

| Task ID     | Title                         | Depends On       | Complexity | Est. Lines |
| ----------- | ----------------------------- | ---------------- | ---------- | ---------- |
| 07          | Damping Physics Engine        | task-01, task-02 | Medium     | 200-300    |
| 08          | Halo Config & CLI             | task-07          | Medium     | 250-350    |
| 09          | Halo Lifecycle Manager        | task-08          | Medium     | 300-400    |
| 10          | Halo Renderer (Core)          | task-09, task-04 | High       | 400-600    |
| 11          | Animation Evaluator           | task-10          | Medium     | 250-350    |
| 12          | Head-Track Integration        | task-11          | Medium     | 200-300    |
| 13          | GUI Config Panel              | task-12          | Medium     | 300-400    |
| 14          | Model Format Support          | task-13          | Low-Medium | 150-250    |
| 15          | Arbitrary Entity Halo Command | task-14          | Low        | 100-150    |
| verify-halo | Integration Tests             | task-15          | Medium     | 200-300    |

---

## Full Dependency Graph

```
    task-01 (data-model) ──┐
    task-02 (server-core)  │
         │                 │
         v                 v
    task-07 (damping-physics)
         │
         v
    task-08 (config-cli)
         │
         v
    task-09 (lifecycle)
         │                      task-04 (client-render)
         └─────────────────────────┬──────────────────┘
                                   v
                             task-10 (renderer)
                                   │
                                   v
                             task-11 (animation)
                                   │
                                   v
                             task-12 (head-track)
                                   │
                                   v
                             task-13 (gui-config)
                                   │
                                   v
                             task-14 (model-format)
                                   │
                                   v
                             task-15 (arbitrary-entity)
                                   │
                                   v
                           verify-halo (integration)
```

## Topological Execution Order

| Level | Tasks (can run parallel)       | Notes                              |
| ----- | ------------------------------ | ---------------------------------- |
| 0     | project-init, data-model       | **Pre-requisites**                 |
| 1     | server-core, verify-data       | **Existing flow**                  |
| 2     | networking, client-render      | **Existing flow**                  |
| 3     | depth-glow, config-polish      | **Existing flow**                  |
| 4     | **task-07** (damping-physics)  | Starts halo system                 |
| 5     | **task-08** (config-cli)       | Depends on task-07                 |
| 6     | **task-09** (lifecycle)        | Depends on task-08                 |
| 7     | **task-10** (renderer)         | Depends on task-09 + client-render |
| 8     | **task-11** (animation)        | Depends on task-10                 |
| 9     | **task-12** (head-track)       | Depends on task-11                 |
| 10    | **task-13** (gui-config)       | Depends on task-12                 |
| 11    | **task-14** (model-format)     | Depends on task-13                 |
| 12    | **task-15** (arbitrary-entity) | Depends on task-14                 |
| 13    | **verify-halo**                | Final integration test             |

---

## Key Design Decisions

### 1. Damping Implementation Strategy

**Location**: Server-side tick processing + client-side frame interpolation

```
Per-tick (server):
  prevRelPos = currentRelPos
  distance = ||targetAnchor - currentPos||

  if (firstTick or teleported):
    currentRelPos = Vec3d(0, 0, 0)
  else:
    delta = targetAnchor - currentPos
    currentRelPos = currentPos + k * delta  // k = 1 - dampingFactor

    // Clamp check
    if (||currentRelPos - targetAnchor|| > maxDistance):
      currentRelPos = targetAnchor + normalized(currentRelPos - targetAnchor) * maxDistance

Per-frame (client, tickDelta [0,1]):
  renderPos = lerp(prevRelPos, currentRelPos, tickDelta)
  renderRot = slerp(prevRelRot, currentRelRot, tickDelta)
```

### 2. State Management

- `HaloInstance`: Per-entity active halo state (server)
- `HaloRenderState`: Per-entity interpolated state (client)
- `HaloManager`: Global registry + tick handler (server)
- `HaloClientManager`: Client-side tracking (client)

### 3. Animation Binding

- Animations are **model-local** (relative to halo center)
- Curves applied **before** damping physics
- Evaluation: `animation_value(t) = curve.evaluate(worldTime)`
- Applied to model vertex buffer each frame

### 4. Head Anchor Definition

- Per-entity: can override anchor position
- Default: `Vec3d(0, 1.6, 0)` relative to entity origin
- Accessible via: config, entity NBT, or command

### 5. GUI Config Sliders

- **Sliders**: size, distance, rotation angle (X/Y/Z)
- **Damping**: linear factor, angular factor, max distance, max angle
- **Glow**: color picker, intensity slider
- **Live preview**: updates halo in real-time during edit
- Storage: NBT in player persistence

---

## Verification Strategy

### Unit Tests (per-task)

- **task-07**: Damping math (step tests, clamp tests)
- **task-08**: Config JSON parsing & validation
- **task-10**: Renderer projection math
- **task-11**: Animation curve evaluation

### Integration Tests (verify-halo)

1. Load halo definition → render on player head
2. Walk around → damping follows smoothly
3. Teleport → snap to anchor
4. Adjust GUI sliders → live preview works
5. Command: `/halo @e[type=armor_stand] ring_default` → renders on armor stand
6. Disable halo → cleanup and no memory leaks

---

## Agent Assignment & Task Files

Each task will have its own `.md` file following the pattern:

- `task-07-damping-physics.md`
- `task-08-halo-config-cli.md`
- `task-09-halo-lifecycle.md`
- `task-10-halo-renderer.md`
- `task-11-animation-evaluator.md`
- `task-12-head-track-integration.md`
- `task-13-gui-config-panel.md`
- `task-14-model-format-support.md`
- `task-15-arbitrary-entity-command.md`
- `task-verify-05-halo-integration.md`

**Recommended Personnel Distribution**:

- **Backend Physics**: Dev-1 (tasks 07, 08, 09)
- **Rendering/Graphics**: Dev-2 (tasks 10, 11, 12)
- **UI/Polish**: Dev-3 (tasks 13, 14, 15)
- **QA Lead**: Dev-1 or Lead (verify-halo)
