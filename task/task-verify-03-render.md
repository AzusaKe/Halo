# Verify 03: Rendering Output

## Agent Type: `general-purpose` (verification)

## Goal
Verify that halo rendering produces correct visual output: correct position, correct orientation, correct depth occlusion, correct glow effect, and smooth interpolation. This is a visual/manual verification — the agent should compile a checklist and guide the tester through each step.

## Dependencies
- `task-05-depth-glow` complete

## Verification Steps

### 1. Basic Rendering

- [ ] Third-person front view: textured ring visible behind player head
- [ ] Third-person side view: ring appears as a thin line (camera-facing billboard)
- [ ] Third-person back view: full ring visible
- [ ] First-person view: halo NOT visible (behind camera)
- [ ] Halo size matches configured scale (visually measure against player model)

### 2. Positioning

- [ ] Halo is centered behind the head, not offset to one side
- [ ] Halo is at correct height (above head, configurable by offset Y)
- [ ] Halo is at correct distance behind head (configurable by offset Z)
- [ ] When player crouches, halo follows head position down
- [ ] When player swims/crawls, halo follows correctly
- [ ] When riding a mount/boat/minecart, halo follows player head

### 3. Damping Visual

- [ ] Walk forward: halo lags behind then catches up smoothly
- [ ] Sprint: more visible lag (higher speed → more displacement)
- [ ] Sudden direction change: halo swings to new position with damping
- [ ] Teleport `/tp @p 1000 64 1000`: halo appears instantly at new position (no cross-world fly)
- [ ] Stand still: halo converges to rest position
- [ ] No visible jitter when standing still (interpolation is smooth)

### 4. Animation Visual

- [ ] Y-axis oscillation visible (gentle bobbing up and down)
- [ ] Yaw rotation visible (ring spinning)
- [ ] Animation is smooth, not stuttering
- [ ] Animation is independent of damping motion (not affected by player movement)

### 5. Depth Occlusion

- [ ] Front view: entity model occludes halo (halo BEHIND body)
- [ ] Side view: part of halo visible, part behind body
- [ ] Back view: full halo visible
- [ ] Two entities with halos, one behind the other: front entity's halo visible, back entity's halo partially occluded by front entity
- [ ] Blocks/walls between camera and entity: halo NOT visible (correct depth test against world geometry)

### 6. Glow Effect

- [ ] Glow layer visible as slightly larger, softer ring behind main ring
- [ ] Glow uses additive blending (looks brighter against dark backgrounds)
- [ ] Glow color matches configured hex color
- [ ] Glow pulse (if configured): alpha oscillates smoothly
- [ ] Glow is visible even when entity model occludes the main ring

### 7. Lighting

- [ ] Halo brightness matches environment lighting (darker in caves, brighter in sunlight)
- [ ] Halo near a torch: correctly lit from torch light
- [ ] Halo in complete darkness: dimly visible (ambient light minimum)

### 8. Multiple Halos

- [ ] Two players with different halo definitions: each shows correct texture/animation
- [ ] Multiple halos on screen simultaneously: all render correctly
- [ ] No z-fighting between overlapping halos (sorted by camera distance)

### 9. Compatibility (if possible to test)

- [ ] Sodium installed: halos still render
- [ ] Iris shaders installed: halos still render (may look different with shaders)
- [ ] Other cosmetic mods: no mixin conflict crash

## Edge Case Tests

### Teleport
- `/tp @p 10000 64 10000` → snap, no fly
- `/execute in the_nether run tp @p ~ ~ ~` → dimension change snap, no fly
- End portal / nether portal → snap on dimension arrival

### Death
- Die → halo removed (or hidden)
- Respawn → halo reappears (if auto-reapply is configured)

### Invisibility
- Drink invisibility potion → halo behavior (should become invisible or stay visible? Check config)

## Pass Criteria
All checkbox items above must pass. Any failure requires a fix in the corresponding task before proceeding to final verification.

## Output
- Completed checklist with pass/fail for each item
- Screenshots of failures (described in text if actual screenshots not possible)
- Root cause analysis for each failure (which task/class needs fixing)
