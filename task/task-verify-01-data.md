# Verify 01: Data Model & JSON Loading

## Agent Type: `general-purpose` (verification)

## Goal
Verify that the data model classes, JSON deserialization, and resource loading work correctly. Catch schema mismatches, deserialization bugs, and missing edge case handling before they propagate to dependent tasks.

## Dependencies
- `task-01-data-model` complete

## Verification Steps

### 1. Static Code Review
- Check all record/class fields match the JSON schema:
  - `HaloDefinition` has `shape`, `animation`, `positioning`, `damping`
  - `BillboardShape` has `texture`, `size`, optional `glow`
  - `GlowLayer` has `texture`, `size`, `color`, `alpha`, optional `pulse`
  - Animation curves have correct `axis`, `type` fields
- Verify polymorphic deserializer handles all `type` values: `billboard`, `multi_billboard`, `static`, `linear`, `oscillate`, `keyframe`
- Verify custom type adapters: `Vec3d` from `[x, y, z]`, `Identifier` from string, `Vec2f` from `[w, h]`

### 2. Resource Loading Test
- Confirm `HaloJsonLoader` is registered as a resource reload listener
- Confirm it scans `halo_definitions/` directory
- Test that `ring_default.json` parses without errors
- Test that `/halo dump` (if implemented) prints the loaded definition

### 3. Malformed JSON Handling
- Temporarily add a deliberately broken JSON file to verify graceful error handling:
  - Missing required field → should log error, skip, not crash
  - Unknown `type` value → should log error, skip
  - Invalid texture path → should log warning (not crash — texture may be in another pack)
  - Array with wrong number of elements for Vec3d → should log error, skip
- After each test, mod should still load and other valid definitions should still work

### 4. Schema Validation
- Write a small check that every loaded `HaloDefinition` has:
  - Non-null `id`
  - Non-null `shape` with valid `texture` identifier
  - Non-null `positioning` with finite `offset` values
  - Damping values in valid ranges (`linearFactor` in [0,1], `maxLinearDistance` >= 0, etc.)
  - Animation curves with valid axis names (`x`, `y`, `z`, `yaw`, `pitch`, `roll`)
- If JSON loader doesn't already do this validation, add it

### 5. Registry Access
- Verify `HaloJsonLoader.get(identifier)` returns correct definition
- Verify `HaloJsonLoader.getAllIds()` returns all loaded IDs
- Verify registry is cleared and reloaded on `/reload`

## Pass Criteria
- [ ] All valid JSON definitions parse without errors
- [ ] Malformed JSON is skipped with log message, does not crash the game
- [ ] All fields survive round-trip (JSON → Java → toString matches expectations)
- [ ] Resource reload clears and rebuilds registry correctly
- [ ] Default definition `ring_default` is present and valid

## Output
- Verification report (pass/fail per criterion)
- List of bugs found (if any) with file paths and fix suggestions
