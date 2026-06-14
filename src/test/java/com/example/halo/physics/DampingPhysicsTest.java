package com.example.halo.physics;

import com.example.halo.data.HaloDampingConfig;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DampingPhysics} — the stateless damping engine.
 *
 * <p>Each test verifies one specific behaviour of the damping algorithm:
 * instant snap on teleport, exponential decay, distance clamping, and
 * angular slerp-with-clamp.</p>
 */
class DampingPhysicsTest {

    // ------------------------------------------------------------------
    // 1. Instant snap
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Instant snap behaviour")
    class InstantSnap {

        @Test
        @DisplayName("needsSnap=true → position is (0,0,0)")
        void testInstantSnap() {
            HaloDampingState state = new HaloDampingState();
            assertTrue(state.needsSnap,
                "fresh state must start with needsSnap=true");

            HaloDampingConfig config = new HaloDampingConfig(0.15, 0.1, 3.0, 180.0);

            // Even with a large current offset, snap should return zero
            Vec3d current = new Vec3d(100.0, 50.0, -25.0);
            Vec3d result = DampingPhysics.computeDampedPosition(
                current, Vec3d.ZERO, config, state
            );

            assertEquals(Vec3d.ZERO, result,
                "snap must return (0,0,0) regardless of current position");
            assertFalse(state.needsSnap,
                "needsSnap flag must be cleared after the snap tick");
        }
    }

    // ------------------------------------------------------------------
    // 2. Damping decay
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Damping decay (exponential convergence)")
    class DampingDecay {

        @Test
        @DisplayName("k=0.5 → position halves each tick toward target (0,0,0)")
        void testDampingDecay() {
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;

            // linearFactor = 0.5 → k = 1 - 0.5 = 0.5
            // Formula: damped = k * current  (exponential decay toward 0)
            HaloDampingConfig config = new HaloDampingConfig(0.5, 0.1, 100.0, 180.0);

            // Simulate physics loop: each tick, current = previous damped value
            // (because halo position converges toward anchor)
            Vec3d current = new Vec3d(10.0, 0.0, 0.0); // initial offset

            // --- Tick 1: 10 → 5 (50% decay) ---
            current = DampingPhysics.computeDampedPosition(current, Vec3d.ZERO, config, state);
            assertEquals(5.0, current.x, 0.0001, "tick 1: x should decay from 10 to 5");
            assertEquals(0.0, current.y, 0.0001);
            assertEquals(0.0, current.z, 0.0001);

            // --- Tick 2: 5 → 2.5 ---
            current = DampingPhysics.computeDampedPosition(current, Vec3d.ZERO, config, state);
            assertEquals(2.5, current.x, 0.0001, "tick 2: x should decay from 5 to 2.5");

            // --- Tick 3: 2.5 → 1.25 ---
            current = DampingPhysics.computeDampedPosition(current, Vec3d.ZERO, config, state);
            assertEquals(1.25, current.x, 0.0001, "tick 3: x should decay from 2.5 to 1.25");

            // --- Tick 4: 1.25 → 0.625 ---
            current = DampingPhysics.computeDampedPosition(current, Vec3d.ZERO, config, state);
            assertEquals(0.625, current.x, 0.0001, "tick 4: x should decay from 1.25 to 0.625");

            // Sanity: after 4 ticks the total decay is 10 * 0.5^4 = 0.625
            assertEquals(10.0 * Math.pow(0.5, 4), current.x, 1e-9);
        }
    }

    // ------------------------------------------------------------------
    // 3. Distance clamping
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Max-distance clamping")
    class ClampDistance {

        @Test
        @DisplayName("distance > maxDist → clamped to maxDist preserving direction")
        void testClampDistance() {
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;

            // linearFactor = 0.0 → k = 1.0 → damped = 1.0*current
            // current = (6, 8, 0) → length = 10
            // maxLinearDistance = 3.0 → clamp to length 3
            HaloDampingConfig config = new HaloDampingConfig(0.0, 0.1, 3.0, 180.0);

            Vec3d result = DampingPhysics.computeDampedPosition(
                new Vec3d(6.0, 8.0, 0.0), Vec3d.ZERO, config, state
            );

            // Length must be exactly maxLinearDistance
            assertEquals(3.0, result.length(), 0.0001,
                "clamped vector length must equal maxLinearDistance");

            // Direction must be preserved: (6,8,0) normalised = (0.6, 0.8, 0)
            // Scaled to length 3: (1.8, 2.4, 0)
            assertEquals(1.8, result.x, 0.0001);
            assertEquals(2.4, result.y, 0.0001);
            assertEquals(0.0, result.z, 0.0001);
        }

        @Test
        @DisplayName("distance ≤ maxDist → no clamping (pass-through)")
        void testNoClampWhenWithinLimit() {
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;

            // k = 1.0, maxDist = 5.0 (> 1.0)
            HaloDampingConfig config = new HaloDampingConfig(0.0, 0.1, 5.0, 180.0);

            Vec3d result = DampingPhysics.computeDampedPosition(
                new Vec3d(0.0, 1.0, 0.0), Vec3d.ZERO, config, state
            );

            assertEquals(1.0, result.length(), 0.0001,
                "vector should not be clamped when within maxDist");
        }
    }

    // ------------------------------------------------------------------
    // 4. Angular damping
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Angular (quaternion) damping")
    class AngularDamping {

        @Test
        @DisplayName("angularFactor=0.5 → slerp halfway from identity to 90° Y rotation")
        void testAngularDamping() {
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;
            // Previous rotation: identity
            state.prevRelativeRotation = new Quaternionf();

            // Target: 90 degrees around the Y axis
            Quaternionf target = new Quaternionf()
                .rotateY((float) Math.toRadians(90.0));

            // angularFactor = 0.5 → angularK = 0.5
            // slerp(identity, target, 0.5) should give 45° around Y
            HaloDampingConfig config = new HaloDampingConfig(0.1, 0.5, 10.0, 180.0);

            Quaternionf result = DampingPhysics.computeDampedRotation(
                new Quaternionf(), target, config, state
            );

            // Extract angle from w component: θ = 2 * acos(w)
            double angleDeg = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(result.w))));
            assertEquals(45.0, angleDeg, 0.01,
                "halfway slerp from identity to 90° should yield 45°");
        }

        @Test
        @DisplayName("angularFactor=1.0 → stays at previous rotation (frozen)")
        void testAngularDampingFrozen() {
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;

            // Previous: 30° around Z
            state.prevRelativeRotation = new Quaternionf()
                .rotateZ((float) Math.toRadians(30.0));

            // Target: 90° around Y (should be ignored when angularK = 0)
            Quaternionf target = new Quaternionf()
                .rotateY((float) Math.toRadians(90.0));

            // angularFactor = 1.0 → angularK = 0.0 → slerp stays at prev
            HaloDampingConfig config = new HaloDampingConfig(0.1, 1.0, 10.0, 180.0);

            Quaternionf result = DampingPhysics.computeDampedRotation(
                new Quaternionf(), target, config, state
            );

            // Result should still be close to 30° around Z
            // For a pure Z rotation: w = cos(15°), z = sin(15°)
            double angleDeg = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(result.w))));
            assertEquals(30.0, angleDeg, 0.5,
                "frozen rotation (angularK=0) should keep previous angle");
        }

        @Test
        @DisplayName("angularFactor=0.0 → slerp all the way to target (instant snap)")
        void testAngularDampingInstant() {
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;

            // Previous: identity
            state.prevRelativeRotation = new Quaternionf();

            // Target: 60° around X
            Quaternionf target = new Quaternionf()
                .rotateX((float) Math.toRadians(60.0));

            // angularFactor = 0.0 → angularK = 1.0 → snap to target
            HaloDampingConfig config = new HaloDampingConfig(0.1, 0.0, 10.0, 180.0);

            Quaternionf result = DampingPhysics.computeDampedRotation(
                new Quaternionf(), target, config, state
            );

            // Should be 60°
            double angleDeg = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(result.w))));
            assertEquals(60.0, angleDeg, 0.01,
                "instant angular snap should match target angle");
        }

        @Test
        @DisplayName("angular clamp: result angle > maxAngularDegrees is scaled back")
        void testAngularClamp() {
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;

            // Previous: identity
            state.prevRelativeRotation = new Quaternionf();

            // Target: 120° around Y
            Quaternionf target = new Quaternionf()
                .rotateY((float) Math.toRadians(120.0));

            // angularFactor = 0.0 → angularK = 1.0 → would give full 120°
            // maxAngularDegrees = 45.0 → clamp to 45°
            HaloDampingConfig config = new HaloDampingConfig(0.1, 0.0, 10.0, 45.0);

            Quaternionf result = DampingPhysics.computeDampedRotation(
                new Quaternionf(), target, config, state
            );

            double angleDeg = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(result.w))));
            assertEquals(45.0, angleDeg, 0.01,
                "angular clamp should cap the result at maxAngularDegrees");
        }
    }

    // ------------------------------------------------------------------
    // 5. Edge cases
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("snap also resets prevRelativePosition for next tick")
        void snapResetsPrevPosition() {
            HaloDampingState state = new HaloDampingState();
            // First tick: snap
            HaloDampingConfig config = new HaloDampingConfig(0.5, 0.5, 10.0, 180.0);
            DampingPhysics.computeDampedPosition(
                new Vec3d(5, 0, 0), Vec3d.ZERO, config, state
            );

            // After snap, prevRelPos must be (0,0,0)
            assertEquals(Vec3d.ZERO, state.prevRelativePosition);

            // Second tick (no snap): current = target = (0,0,0)
            // damped = 0 + 0.5 * 0 = 0
            Vec3d r2 = DampingPhysics.computeDampedPosition(
                Vec3d.ZERO, Vec3d.ZERO, config, state
            );
            assertEquals(Vec3d.ZERO, r2,
                "after snap, damping from zero should stay at zero");
        }

        @Test
        @DisplayName("HaloDampingState.recordTick persists values correctly")
        void recordTickPersists() {
            HaloDampingState state = new HaloDampingState();
            Vec3d pos = new Vec3d(1, 2, 3);
            Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(45));

            state.recordTick(pos, rot);

            assertEquals(pos, state.prevRelativePosition);
            // rotation should be a copy, not the same object
            assertNotSame(rot, state.prevRelativeRotation);
            assertEquals(rot.x, state.prevRelativeRotation.x, 0.0001);
            assertEquals(rot.y, state.prevRelativeRotation.y, 0.0001);
            assertEquals(rot.z, state.prevRelativeRotation.z, 0.0001);
            assertEquals(rot.w, state.prevRelativeRotation.w, 0.0001);
        }

        @Test
        @DisplayName("HaloDampingState.markTeleport sets needsSnap")
        void markTeleportSetsSnap() {
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;
            assertFalse(state.needsSnap);

            state.markTeleport();
            assertTrue(state.needsSnap);
        }
    }
}
