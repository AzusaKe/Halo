# Task Halo System — Complete Topology & Execution Plan

## Updated Full Dependency Graph

```
Pre-existing Base (completed):
├─ task-00: project-init ✓
├─ task-01: data-model ✓
├─ task-02: server-core ✓
├─ task-03: networking ✓
└─ task-04: client-render ✓

NEW: Halo System Pipeline:
    └─ task-07: damping-physics
         │
         ├─ depends: task-01 (data), task-02 (server)
         │
         v
    task-08: config-cli
         │
         ├─ depends: task-07 (physics)
         │
         v
    task-09: lifecycle
         │
         ├─ depends: task-08 (config)
         │
         v
    task-10: renderer
         │
         ├─ depends: task-09 (lifecycle), task-04 (client-render)
         │
         v
    task-11: animation-evaluator
         │
         ├─ depends: task-10 (renderer)
         │
         v
    task-12: head-track-integration
         │
         ├─ depends: task-11 (animation)
         │
         v
    task-13: gui-config-panel
         │
         ├─ depends: task-12 (head-track)
         │
         v
    task-14: model-format-support
         │
         ├─ depends: task-13 (gui)
         │
         v
    task-15: arbitrary-entity-command
         │
         ├─ depends: task-14 (model-format)
         │
         v
    task-verify-05: halo-integration (final)
```

---

## Topological Execution Levels

| Level   | Task(s)                                                                                                  | Can Parallel? | Duration     | Blocker                       |
| ------- | -------------------------------------------------------------------------------------------------------- | ------------- | ------------ | ----------------------------- |
| **Pre** | project-init, data-model, verify-data, server-core, networking, client-render, depth-glow, config-polish | N/A           | Pre-existing | None                          |
| **1**   | **task-07** (damping-physics)                                                                            | Solo          | 2-3 days     | Physics must work first       |
| **2**   | **task-08** (config-cli)                                                                                 | Solo          | 2-3 days     | Commands depend on physics    |
| **3**   | **task-09** (lifecycle)                                                                                  | Solo          | 2-3 days     | Lifecycle depends on config   |
| **4**   | **task-10** (renderer)                                                                                   | Solo          | 3-4 days     | Rendering needs lifecycle     |
| **5**   | **task-11** (animation-evaluator)                                                                        | Solo          | 2-3 days     | Animation evals before render |
| **6**   | **task-12** (head-track-integration)                                                                     | Solo          | 1-2 days     | Light touch, depends on anim  |
| **7**   | **task-13** (gui-config-panel)                                                                           | Solo          | 2-3 days     | UI polish                     |
| **8**   | **task-14** (model-format-support)                                                                       | Solo          | 1-2 days     | Optional asset compatibility  |
| **9**   | **task-15** (arbitrary-entity-command)                                                                   | Solo          | 1 day        | Adds command variant          |
| **10**  | **task-verify-05** (integration)                                                                         | Solo          | 1-2 days     | Final testing & fixes         |

---

## Personnel Assignment Strategy

Based on typical 3-developer team + 1 QA lead:

### Developer 1 (Backend Lead) — Tasks 07, 08, 09, verify-05

- **Expertise**: Physics, server-side state, NBT persistence
- **Time estimate**: 7-9 days
- **Deliverables**:
  - Damping physics engine with snap behavior
  - Command system & config management
  - Entity lifecycle & world persistence
  - Final integration testing

### Developer 2 (Graphics Lead) — Tasks 10, 11, 12

- **Expertise**: Rendering, matrix math, interpolation
- **Time estimate**: 6-9 days
- **Deliverables**:
  - Billboard renderer with face-camera
  - Animation curve evaluator
  - Head-track integration & smooth following

### Developer 3 (Tools/Polish) — Tasks 13, 14, 15

- **Expertise**: UI, model formats, optional features
- **Time estimate**: 4-6 days
- **Deliverables**:
  - GUI config sliders
  - Model format loaders (Blockbench, glTF, etc.)
  - Arbitrary entity command system

### QA Lead — verify-05 + throughout

- **Role**: Test coordinator, regression testing
- **Time estimate**: 2-3 days dedicated + ongoing
- **Deliverables**:
  - Integration test suite
  - Performance benchmarks
  - Edge case coverage

**Total estimated time**: 2-3 weeks for a motivated team

---

## Checkpoints & Review Gates

| Checkpoint | After Task(s)  | Reviewer | Criteria                                               |
| ---------- | -------------- | -------- | ------------------------------------------------------ |
| **CP-1**   | task-07        | QA       | Physics math verified; damping works in unit tests     |
| **CP-2**   | task-08        | QA       | Commands parse; config values validated; CLI works     |
| **CP-3**   | task-09        | QA       | Lifecycle stable; no leaks over 1 hour; NBT persists   |
| **CP-4**   | task-10        | QA       | Halos render visible; culling works; no visual bugs    |
| **CP-5**   | task-11        | QA       | Animations eval correctly; time-sync accurate          |
| **CP-6**   | task-12        | QA       | Head-track smooth; damping + anim blend correct        |
| **CP-7**   | task-13        | QA       | GUI responsive; sliders update live                    |
| **CP-8**   | task-14        | QA       | Model import works; no crashes on bad files            |
| **CP-9**   | task-15        | QA       | Command system reliable; entity selection works        |
| **CP-10**  | task-verify-05 | Lead     | Full system end-to-end; perf acceptable; docs complete |

---

## Parallel Work Opportunities

While tasks are **linearly dependent**, some setup can happen in parallel:

- **task-08** can be prototyped while task-07 is finishing (config structure)
- **task-10** rendering can start before task-09 is 100% done (mock HaloInstance)
- **task-13** GUI can be designed while task-12 is finishing
- **task-14** model loaders can research file formats early

**Recommendation**: Assign overlapping tasks with 2-3 day offset to maximize throughput while maintaining quality gates.

---

## Risk Mitigation

| Risk                             | Mitigation                                                   |
| -------------------------------- | ------------------------------------------------------------ |
| Physics math wrong               | Unit tests first; pair review of quaternion & clamp logic    |
| NBT corruption                   | Versioning strategy; safe defaults; world backup before load |
| Rendering perf degradation       | Profile on 50+ halos early; batch rendering if needed        |
| Animation timing skew            | Frame vs. tick delta tests; benchmark on varied frame rates  |
| Memory leaks with entity cleanup | Weak references; periodic entity map cleanup; heap profiling |
| Model format incompatibility     | Support only glTF + Blockbench as MVP; roadmap others        |

---

## Success Metrics (Phase Complete)

✅ **Functionality**:

- [ ] All 9 core tasks completed
- [ ] All unit tests pass (95%+ coverage)
- [ ] Integration test suite passes
- [ ] No console warnings/errors

✅ **Performance**:

- [ ] 50+ active halos: 60+ FPS (1.20.1)
- [ ] Load time < 5ms per halo definition
- [ ] Memory overhead < 1MB per 100 halos

✅ **User Experience**:

- [ ] Smooth damping animation (no jitter)
- [ ] Live config updates work
- [ ] Commands intuitive & documented
- [ ] GUI polished (proper spacing, responsive)

✅ **Documentation**:

- [ ] All task readmes complete
- [ ] In-game guide (lore/tutorial)
- [ ] JSON schema documented
- [ ] Contributor guide for asset makers

---

## Next Phases (Post-MVP)

### Phase 2 (future): Advanced Features

- [ ] task-16: Keyframe-based animation curves (complex easing)
- [ ] task-17: Particle system integration (glow trails)
- [ ] task-18: Sound effects (halo chime on spawn)
- [ ] task-19: Config GUI visual preview (mini-viewport)

### Phase 3 (future): Ecosystem

- [ ] Asset library (community halos)
- [ ] Halo packs (ZIP with textures + JSON)
- [ ] Mod integration (e.g., Jade mod support)
- [ ] Data pack templates

---

## File Structure Summary

```
f:\Halo\
├── task/
│   ├── task-07-halo-system-plan.md ✓ (this file above)
│   ├── task-07-damping-physics.md ✓
│   ├── task-08-halo-config-cli.md ✓
│   ├── task-09-halo-lifecycle.md ✓
│   ├── task-10-halo-renderer.md ✓
│   ├── task-11-animation-evaluator.md (to create)
│   ├── task-12-head-track-integration.md (to create)
│   ├── task-13-gui-config-panel.md (to create)
│   ├── task-14-model-format-support.md (to create)
│   ├── task-15-arbitrary-entity-command.md (to create)
│   ├── task-verify-05-halo-integration.md (to create)
│   └── tasks-topology.md (existing, to be updated)
│
└── src/
    └── main/java/com/example/halo/
        ├── physics/
        │   ├── DampingPhysics.java (task-07)
        │   ├── HaloDampingState.java (task-07)
        │   └── HaloTickHandler.java (task-07)
        ├── config/
        │   └── HaloConfig.java (task-08)
        ├── command/
        │   └── HaloConfigCommand.java (task-08)
        ├── manager/
        │   └── HaloManager.java (task-08 + task-09)
        ├── lifecycle/
        │   ├── EntityHaloTracker.java (task-09)
        │   ├── HaloEntityEventHandler.java (task-09)
        │   └── HaloWorldSaveData.java (task-09)
        ├── render/
        │   ├── HaloRenderer.java (task-10)
        │   ├── HaloClientManager.java (task-10)
        │   └── HaloRenderListener.java (task-10)
        ├── animation/
        │   └── HaloAnimationEvaluator.java (task-11)
        ├── ui/
        │   └── HaloConfigScreen.java (task-13)
        └── formats/
            └── HaloModelLoader.java (task-14)
```

---

## How Agent Can Execute This Plan

Agent can:

1. ✅ **Read task files** → understand requirements & acceptance criteria
2. ✅ **Generate code** → follow patterns from task-01 thru task-06 (existing tasks)
3. ✅ **Run tests** → `./gradlew test` to validate
4. ✅ **Test in-game** → launch dev server & verify halos appear/update
5. ✅ **Iterate** → update task files if scope changes
6. ✅ **Create PR** → propose completed task with test results

**Recommendation**: Assign tasks to 1-3 agents sequentially; each completes one task, passes QA gate, then next agent starts.

---

## Quick Start Command (for testing)

```bash
# Start dev server
./gradlew runClient

# In-game, once server loads:
/halo list
/halo show @s ring_default
/halo config linear-damping 0.5
```

Done! 🎉
