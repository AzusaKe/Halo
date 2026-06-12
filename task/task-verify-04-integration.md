# Verify 04: End-to-End Integration

## Agent Type: `general-purpose` (verification)

## Goal
Final integration verification covering the full pipeline: JSON definition → resource loading → command → server physics → network sync → client render → visual output. Also verify config persistence, example definitions, and overall stability.

## Dependencies
- `task-06-config-polish` complete
- `task-verify-01-data` passed
- `task-verify-02-server` passed
- `task-verify-03-render` passed

## Verification Steps

### 1. Full Pipeline: JSON to Visual

For each example definition (`ring_default`, `ring_double`, `ring_cross`, `ring_divine`, `ring_geometric`):
- [ ] Load via `/reload` → appears in `/halo definitions`
- [ ] Apply to self via `/halo apply @p halo:ring_*`
- [ ] Visible in third-person view
- [ ] Correct texture, size, animation
- [ ] Remove via `/halo remove @p`
- [ ] Re-apply → works again (no state corruption)

### 2. Config Persistence

- [ ] Open Cloth Config screen (ModMenu → Halo)
- [ ] Change damping `linearFactor` slider to 50
- [ ] Exit config screen
- [ ] Apply a halo, observe damping behavior matches new value
- [ ] Exit and restart game
- [ ] Config value persists (still 50)

### 3. Multi-Entity Scenario

- [ ] Apply 5 different halos to 5 entities (2 players, 3 mobs)
- [ ] All 5 render correctly simultaneously
- [ ] Each shows correct definition (no mix-ups)
- [ ] Walking between them: correct depth sorting
- [ ] Remove one → only that one disappears
- [ ] Remove all → `/halo list` shows empty

### 4. Stability / Soak Test

- [ ] Apply halo, play normally for 10 minutes (walk, mine, fight, teleport)
- [ ] No crash
- [ ] No log spam (check latest.log for ERROR/WARN from `halo` namespace)
- [ ] Halo rendering never breaks (doesn't disappear or glitch)
- [ ] FPS stable (no memory leak — check heap in F3 after 10 min)

### 5. Server-Client Desync Check

- [ ] Server applies halo to Player A
- [ ] Player B sees the halo on Player A
- [ ] Server removes halo from Player A
- [ ] Player B sees it disappear (within 1-2 seconds at most)
- [ ] Player A disconnects → halo removed from Player B's view
- [ ] Player A reconnects → halo reappears for both

### 6. Error Recovery

- [ ] Delete a halo definition JSON file while game is running → `/reload` logs error, other definitions still work
- [ ] Restore the file → `/reload` loads it again
- [ ] Apply halo with valid definition, then edit definition to be invalid → `/reload` logs error, existing active halo continues using old cached definition (graceful degradation)
- [ ] Spam `/halo apply` + `/halo remove` rapidly 20 times → no crash, final state correct

### 7. API Surface Check (if api/ package is used)

- [ ] `HaloApi.setHalo(entity, definitionId)` works from another mod's context (simulate by calling from a test command)
- [ ] `HaloApi.removeHalo(entityUuid)` works
- [ ] `HaloApi.getHalo(entityUuid)` returns correct state

### 8. Documentation Check

- [ ] JSON schema is documented (or at least the example files serve as reference)
- [ ] All commands have help text (`/halo` with no args shows usage)
- [ ] Config sliders have tooltips explaining what they do

## Pass Criteria
All checkbox sections must pass. Any failure is a blocker for beta release.

## Output
- Completed integration test report
- List of any remaining issues with severity (blocker / major / minor / cosmetic)
- Recommendation: ready for beta, or list of must-fix items before beta
