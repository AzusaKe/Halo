# Verify 02: Server Core & Networking

## Agent Type: `general-purpose` (verification)

## Goal
Verify that the server-side systems (HaloManager, HaloPhysicsEngine, HaloCommand, HaloNetwork) work correctly in isolation and together. Catch logic bugs in damping math, lifecycle management, and packet sync.

## Dependencies
- `task-02-server-core` complete
- `task-03-networking` complete

## Verification Steps

### 1. Physics Engine Unit Test

Write a simple main() or use in-game logging to verify:

**Test: Linear damping convergence**
- Set `linearFactor = 0.25`, initial distance = 4.0
- Run 20 ticks, verify distance decreases toward 0
- Verify `distance(t+1) = distance(t) * 0.75`

**Test: Max distance clamping**
- Set `maxLinearDistance = 2.0`, initial distance = 5.0
- After 1 tick, distance must be ≤ 2.0 (not 5.0 * 0.75 = 3.75)
- Verify clamping happens BEFORE storing state

**Test: Snap on first tick**
- New HaloInstance with `needsSnap = true`
- First tick: position must equal target anchor, rotation must be identity
- `needsSnap` must be cleared after first tick

**Test: Teleport detection**
- Entity at position A, halo following
- Move entity to position B where |B-A| > 64 blocks
- Next tick: halo must snap (position = target, not damped)

**Test: Angular damping**
- Set initial rotation to 90° yaw offset (Quaternionf)
- Run ticks with `angularFactor = 0.15`
- Verify rotation angle decreases each tick
- Verify clamping at `maxAngularDegrees`

### 2. HaloManager Lifecycle Test

- Create halo for entity → `getHalo(uuid)` returns non-null
- Entity removed from world → halo is cleaned up
- Entity changes dimension → halo is NOT removed, but snaps on next tick
- Player disconnects → halo removed from active map
- `setHalo()` twice for same entity → second call replaces first

### 3. Command Verification

**Test each subcommand:**
- `/halo apply @p halo:ring_default` → success message, halo stored in manager
- `/halo apply @p halo:nonexistent` → error message, no crash
- `/halo apply @e[type=creeper] halo:ring_default` → halo on non-player entity works
- `/halo remove @p` → success message, halo removed
- `/halo remove @p` again → "no halo to remove" message
- `/halo list` with no halos → "no active halos" message
- `/halo list` with halos → lists each (entity name + definition ID)
- `/halo definitions` → lists all loaded definition IDs

### 4. Network Sync Verification

- Server Client A applies halo → Client B (in range) receives `HaloSyncS2CPacket`
- Verify packet contains correct `entityUuid`, `definitionId`, `needsSnap = true`
- Client B removes halo → Client B receives `HaloRemoveS2CPacket`
- Player rejoins → receives sync for all active halos (JOIN event handler)
- Entity enters tracking range of a player → that player receives sync

### 5. Concurrency Check

- Rapid `/halo apply` / `/halo remove` commands (spam 10 in 1 second)
- No `ConcurrentModificationException` or deadlock
- Manager state remains consistent (last command wins)

## Pass Criteria
- [ ] All physics tests pass (damping converges, clamp works, snap triggers)
- [ ] All lifecycle transitions handled (create, remove, dimension, disconnect)
- [ ] All commands respond correctly for valid and invalid inputs
- [ ] Network packets sent to correct recipients
- [ ] No crashes under concurrent access

## Output
- Verification report with pass/fail per criterion
- List of bugs found with reproduction steps
- Suggestions for physics parameter default values if current ones feel wrong
