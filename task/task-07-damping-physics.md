# Task 07: Damping Physics Engine

## Agent Type: `general-purpose`

## Goal

Implement server-side damping physics for halo position and rotation tracking. At the end, a halo can smoothly follow an entity's head anchor with configurable damping, clamp distance, and correct teleport handling.

## Dependencies

- `task-01-data-model` complete (data classes exist)
- `task-02-server-core` complete (server tick handler available)

## Input

- `HaloInstance.java` (from task-01)
- `HaloDampingConfig.java` (from task-01)
- Server tick event system (Fabric)

## Task

### 1. Create `HaloDampingState.java` (internal state tracking)

```java
public class HaloDampingState {
    Vec3d prevRelativePosition;
    Quaternionf prevRelativeRotation;
    boolean needsSnap;  // true on first tick or after teleport
    long lastTickTime;

    // Constructor initializes snap flag
    public HaloDampingState() {
        this.needsSnap = true;
        this.prevRelativePosition = Vec3d.ZERO;
        this.prevRelativeRotation = new Quaternionf();
        this.lastTickTime = System.nanoTime();
    }

    public void recordTick(Vec3d position, Quaternionf rotation) { /*...*/ }
    public void markTeleport() { needsSnap = true; }
}
```

### 2. Create `DampingPhysics.java` (core logic)

```java
public class DampingPhysics {

    /**
     * Compute damped position for next frame.
     * @param current current position relative to anchor
     * @param target target position (anchor point)
     * @param damping damping config (k, maxDistance)
     * @param state previous frame state (for damping calculation)
     * @return new position relative to anchor
     */
    public static Vec3d computeDampedPosition(
        Vec3d current,
        Vec3d target,
        HaloDampingConfig damping,
        HaloDampingState state
    ) {
        if (state.needsSnap) {
            state.needsSnap = false;
            state.prevRelativePosition = new Vec3d(0, 0, 0);
            return new Vec3d(0, 0, 0);  // snap to anchor
        }

        // Relative offset from previous frame
        Vec3d offset = state.prevRelativePosition;

        // Apply damping: new_pos = current + k * offset
        // where k = 1 - linearFactor
        double k = 1.0 - damping.linearFactor();
        Vec3d damped = current.add(offset.multiply(k));

        // Clamp to max distance
        double distance = damped.length();
        if (distance > damping.maxLinearDistance()) {
            damped = damped.normalize().multiply(damping.maxLinearDistance());
        }

        // Store for next iteration
        state.prevRelativePosition = damped;

        return damped;
    }

    /**
     * Compute damped rotation using quaternion SLERP + damping.
     */
    public static Quaternionf computeDampedRotation(
        Quaternionf current,
        Quaternionf target,
        HaloDampingConfig damping,
        HaloDampingState state
    ) {
        if (state.needsSnap) {
            state.prevRelativeRotation = new Quaternionf();
            return new Quaternionf();  // identity
        }

        // Angular damping: interpolate toward target
        double angularK = 1.0 - damping.angularFactor();
        Quaternionf result = new Quaternionf(state.prevRelativeRotation);
        result.slerp(target, (float) angularK);

        // Clamp max angular deviation
        // (simplified: if angle > maxAngle, scale back)
        float angle = (float) Math.toDegrees(2.0f * (float) Math.acos(result.w));
        if (angle > damping.maxAngularDegrees()) {
            float scale = (float) damping.maxAngularDegrees() / angle;
            result.x *= scale;
            result.y *= scale;
            result.z *= scale;
            float newW = (float) Math.sqrt(Math.max(0, 1.0 - (result.x*result.x + result.y*result.y + result.z*result.z)));
            result.w = newW;
        }

        state.prevRelativeRotation = new Quaternionf(result);
        return result;
    }
}
```

### 3. Integrate into `HaloInstance.java`

Add damping state field and update method:

```java
public class HaloInstance {
    // ... existing fields ...
    private HaloDampingState dampingState = new HaloDampingState();

    /**
     * Called once per server tick.
     * @param anchorPos world position of head anchor
     * @param haloCenter current halo center (world space)
     */
    public void tickDamping(Vec3d anchorPos, Vec3d haloCenter, HaloDampingConfig damping) {
        Vec3d currentRel = haloCenter.subtract(anchorPos);

        // Compute new damped position
        Vec3d newRel = DampingPhysics.computeDampedPosition(
            currentRel, Vec3d.ZERO, damping, dampingState
        );

        this.relativePosition = anchorPos.add(newRel);

        // Similar for rotation...
    }

    public void markTeleported() {
        dampingState.markTeleport();
    }

    public Vec3d getInterpolatedPosition(double tickDelta) {
        // Client: lerp between prevRelativePosition and relativePosition
        return prevRelativePosition.lerp(relativePosition, tickDelta);
    }
}
```

### 4. Create `HaloTickHandler.java`

Fabric server tick event listener:

```java
public class HaloTickHandler implements ServerTickEvents.End {
    private static final HaloTickHandler INSTANCE = new HaloTickHandler();

    public static void register() {
        ServerTickEvents.END.register(INSTANCE);
    }

    @Override
    public void onEndTick(MinecraftServer server) {
        // Iterate all loaded halo instances
        // For each: call tickDamping() with current anchor position
        HaloManager.getInstance().tickAll();
    }
}
```

### 5. Unit Tests (in `test/` directory)

```
src/test/java/com/example/halo/physics/DampingPhysicsTest.java
- testInstantSnap(): snap=true → position is (0,0,0)
- testDampingDecay(): k=0.5 → position decreases by 50% per tick
- testClampDistance(): distance > maxDist → clamped to maxDist
- testAngularDamping(): quaternion slerp with damping factor
```

### 6. Verify (manual in-game)

1. Spawn a player in world
2. Halo should be visible on their head (default position)
3. Walk around → halo smoothly follows, not jittery
4. Increase `linearFactor` → halo lags behind more
5. Decrease `linearFactor` → halo snaps closer
6. Teleport player (`/tp`) → halo snaps to new anchor, no sliding

## Output Artifacts

- `src/main/java/com/example/halo/physics/DampingPhysics.java`
- `src/main/java/com/example/halo/physics/HaloDampingState.java`
- `src/main/java/com/example/halo/physics/HaloTickHandler.java`
- Updated: `src/main/java/com/example/halo/data/HaloInstance.java` (add damping methods)
- `src/test/java/com/example/halo/physics/DampingPhysicsTest.java`

## Success Criteria

✓ Halo smoothly follows head anchor with configurable damping  
✓ Clamp distance works (halo never strays > maxDistance)  
✓ Teleport causes snap (no sliding)  
✓ All unit tests pass  
✓ No console errors when ticking 100+ active halos

## Assigned to: **Dev-1 (Backend Lead)**

## Reviewer: **QA Lead**
