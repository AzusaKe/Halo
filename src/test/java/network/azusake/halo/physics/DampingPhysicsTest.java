package network.azusake.halo.physics;

import network.azusake.halo.data.HaloDampingConfig;
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
 * instant snap on teleport, frame-rate-independent exponential convergence,
 * distance clamping, and angular slerp-with-clamp.</p>
 *
 * <p>All tests use {@code deltaTime = 0.05} (reference tick at 20 TPS)
 * so the frame-rate-independent formula reduces to the base formula:
 * k_f = 1 − (1 − k)^{0.05/0.05} = k.</p>
 */
class DampingPhysicsTest {

    /** Reference tick duration (20 TPS). */
    private static final double DT = 0.05;

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
                current, Vec3d.ZERO, config, state, DT
            );

            assertEquals(Vec3d.ZERO, result,
                "snap must return (0,0,0) regardless of current position");
            assertFalse(state.needsSnap,
                "needsSnap flag must be cleared after the snap tick");
        }
    }

    // ------------------------------------------------------------------
    // 2. Damping decay (exponential convergence)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Damping decay (exponential convergence)")
    class DampingDecay {

        @Test
        @DisplayName("k=0.5 → position halves each tick toward target (0,0,0)")
        void testDampingDecay() {
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;

            // linearFactor = 0.5 → at dt=0.05: k_f = 1−(1−0.5)^1 = 0.5
            // damped = current × (1−0.5) = current × 0.5  (exponential decay toward 0)
            HaloDampingConfig config = new HaloDampingConfig(0.5, 0.1, 100.0, 180.0);

            // Simulate physics loop: each tick, current = previous damped value
            // (because halo position converges toward anchor)
            Vec3d current = new Vec3d(10.0, 0.0, 0.0); // initial offset

            // --- Tick 1: 10 → 5 (50% decay) ---
            current = DampingPhysics.computeDampedPosition(current, Vec3d.ZERO, config, state, DT);
            assertEquals(5.0, current.x, 0.0001, "tick 1: x should decay from 10 to 5");
            assertEquals(0.0, current.y, 0.0001);
            assertEquals(0.0, current.z, 0.0001);

            // --- Tick 2: 5 → 2.5 ---
            current = DampingPhysics.computeDampedPosition(current, Vec3d.ZERO, config, state, DT);
            assertEquals(2.5, current.x, 0.0001, "tick 2: x should decay from 5 to 2.5");

            // --- Tick 3: 2.5 → 1.25 ---
            current = DampingPhysics.computeDampedPosition(current, Vec3d.ZERO, config, state, DT);
            assertEquals(1.25, current.x, 0.0001, "tick 3: x should decay from 2.5 to 1.25");

            // --- Tick 4: 1.25 → 0.625 ---
            current = DampingPhysics.computeDampedPosition(current, Vec3d.ZERO, config, state, DT);
            assertEquals(0.625, current.x, 0.0001, "tick 4: x should decay from 1.25 to 0.625");

            // Sanity: after 4 ticks the total decay is 10 * 0.5^4 = 0.625
            assertEquals(10.0 * Math.pow(0.5, 4), current.x, 1e-9);
        }
    }

    // ------------------------------------------------------------------
    // 3. Distance clamping
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Max-distance clamping (算法: 如果 d > max_d，在 S 方向继续前进直到 d = max_d)")
    class ClampDistance {

        @Test
        @DisplayName("d > maxDist → clamped to maxDist, direction from target to halo preserved")
        void testClampDistance() {
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;

            // linearFactor = 0.0 → k_f = 0 → halo frozen at previous position
            // current = H − T = (6, 8, 0) → length = 10 = d
            // maxLinearDistance = 3.0 → clamp: d → 3.0
            HaloDampingConfig config = new HaloDampingConfig(0.0, 0.1, 3.0, 180.0);

            Vec3d result = DampingPhysics.computeDampedPosition(
                new Vec3d(6.0, 8.0, 0.0), Vec3d.ZERO, config, state, DT
            );

            // Length must be exactly maxLinearDistance
            assertEquals(3.0, result.length(), 0.0001,
                "clamped vector length must equal maxLinearDistance");

            // Direction must be preserved: (6,8,0) normalized = (0.6, 0.8, 0)
            // Scaled to length 3: (1.8, 2.4, 0)
            assertEquals(1.8, result.x, 0.0001, "x direction preserved");
            assertEquals(2.4, result.y, 0.0001, "y direction preserved");
            assertEquals(0.0, result.z, 0.0001);
        }

        @Test
        @DisplayName("d ≤ maxDist → NO clamping (pass-through)")
        void testNoClampWhenWithinLimit() {
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;

            // k=0 → k_f=0 → halo frozen.  d = 1.0 ≤ maxDist = 5.0
            HaloDampingConfig config = new HaloDampingConfig(0.0, 0.1, 5.0, 180.0);

            Vec3d result = DampingPhysics.computeDampedPosition(
                new Vec3d(0.0, 1.0, 0.0), Vec3d.ZERO, config, state, DT
            );

            assertEquals(1.0, result.length(), 0.0001,
                "vector should NOT be clamped when d ≤ maxDist");
            assertEquals(0.0, result.x, 0.0001);
            assertEquals(1.0, result.y, 0.0001);
        }

        @Test
        @DisplayName("clamp occurs AFTER damping: halo moves toward target then clamped if still too far")
        void testClampAfterDamping() {
            // Simulates the real scenario: entity moves rapidly, halo lags behind.
            // T = (0,0,0), H = (10,0,0)
            // k=0.3 → k_f=0.3 → H_new' = 0.7*10 + 0.3*0 = 7.0
            // d = |T − H_new'| = 7.0 > maxDist = 5.0
            // Clamp: H_new = (5,0,0) — halo is exactly 5 blocks from target
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;
            // Previous frame position (offset from target) = H - T
            // We simulate the state storing the "previous damped offset"
            state.prevRelativePosition = new Vec3d(10.0, 0.0, 0.0);

            HaloDampingConfig config = new HaloDampingConfig(0.3, 0.3, 5.0, 180.0);

            Vec3d result = DampingPhysics.computeDampedPosition(
                new Vec3d(10.0, 0.0, 0.0), // current = H - T
                Vec3d.ZERO,                  // target offset = ZERO
                config, state, DT
            );

            // After damping: 10 * (1−0.3) = 7.0
            // 7.0 > 5.0 → clamped to 5.0
            assertEquals(5.0, result.x, 0.0001,
                "halo should be clamped to maxDist=5 after damping");
            assertEquals(5.0, result.length(), 0.0001);
        }

        @Test
        @DisplayName("clamp only if d > max_d; small gap passes through unchanged")
        void testNoClampForSmallGap() {
            // T = (0,0,0), H = (2,0,0)
            // k=0.3 → k_f=0.3 → H_new' = 2 * 0.7 = 1.4
            // d = 1.4 ≤ maxDist = 5.0 → no clamp
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;
            state.prevRelativePosition = new Vec3d(2.0, 0.0, 0.0);

            HaloDampingConfig config = new HaloDampingConfig(0.3, 0.3, 5.0, 180.0);

            Vec3d result = DampingPhysics.computeDampedPosition(
                new Vec3d(2.0, 0.0, 0.0), Vec3d.ZERO, config, state, DT
            );

            // 2.0 * (1−0.3) = 1.4 → no clamp needed
            assertEquals(1.4, result.x, 0.0001,
                "small gap should pass through damping without clamp");
        }

        @Test
        @DisplayName("多帧模拟: 光环永远不会超过 maxLinearDistance (multi-frame simulation)")
        void testMultiFrameNeverExceedsMaxDist() {
            // Simulate 100 frames with varying entity movement.
            // The halo must NEVER exceed maxDist from target at the END of any frame.
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;
            state.prevRelativePosition = Vec3d.ZERO;

            HaloDampingConfig config = new HaloDampingConfig(0.3, 0.3, 1.0, 180.0);
            Vec3d current = Vec3d.ZERO;

            java.util.Random rng = new java.util.Random(42); // deterministic

            for (int frame = 0; frame < 100; frame++) {
                // Simulate entity movement: random target jump each frame (up to 0.5 blocks)
                double jumpX = (rng.nextDouble() - 0.5) * 1.0;
                double jumpY = (rng.nextDouble() - 0.5) * 1.0;
                double jumpZ = (rng.nextDouble() - 0.5) * 1.0;

                // Target moves (relative to old target ≡ ZERO in offset space)
                // Convert the jump to offset space: new current = H - T_new = old_H - new_T
                // Since old T was at offset (0,0,0) and new T moved by jump,
                // H − T_new = H_old_relative + (old_T − new_T) = current + (-jump)
                // But we keep it simple: current represents "offset from entity target"
                // and each frame the target jumps, so the offset changes.
                Vec3d targetMovement = new Vec3d(jumpX, jumpY, jumpZ);
                Vec3d newCurrent = current.subtract(targetMovement); // H − T_new

                Vec3d result = DampingPhysics.computeDampedPosition(
                    newCurrent, Vec3d.ZERO, config, state, DT
                );

                double dist = result.length();
                assertTrue(dist <= config.maxLinearDistance() + 1e-9,
                    String.format("Frame %d: dist=%.4f exceeds maxDist=%.4f",
                        frame, dist, config.maxLinearDistance()));

                current = result;
            }
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

            // angularFactor = 0.5 → at dt=0.05: angularK_f = 0.5
            // slerp(identity, target, 0.5) should give 45° around Y
            HaloDampingConfig config = new HaloDampingConfig(0.1, 0.5, 10.0, 180.0);

            Quaternionf result = DampingPhysics.computeDampedRotation(
                new Quaternionf(), target, config, state, DT
            );

            // Extract angle from w component: θ = 2 * acos(w)
            double angleDeg = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(result.w))));
            assertEquals(45.0, angleDeg, 0.01,
                "halfway slerp from identity to 90° should yield 45°");
        }

        @Test
        @DisplayName("angularFactor=0.0 → stays at previous rotation (frozen at identity)")
        void testAngularDampingFrozen() {
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;

            // Previous: 30° around Z
            state.prevRelativeRotation = new Quaternionf()
                .rotateZ((float) Math.toRadians(30.0));

            // Target: 90° around Y (should be ignored when angularK_f = 0)
            Quaternionf target = new Quaternionf()
                .rotateY((float) Math.toRadians(90.0));

            // angularFactor = 0.0 → angularK_f = 0 → slerp stays at prev
            HaloDampingConfig config = new HaloDampingConfig(0.1, 0.0, 10.0, 180.0);

            Quaternionf result = DampingPhysics.computeDampedRotation(
                new Quaternionf(), target, config, state, DT
            );

            // Result should still be close to 30° around Z
            // angularK_f=0 means "frozen — no movement toward target"
            double angleDeg = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(result.w))));
            assertEquals(30.0, angleDeg, 0.5,
                "frozen rotation (angularK=0 → angularK_f=0) should keep previous angle");
        }

        @Test
        @DisplayName("angularFactor=1.0 → slerp all the way to target (instant snap)")
        void testAngularDampingInstant() {
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;

            // Previous: identity
            state.prevRelativeRotation = new Quaternionf();

            // Target: 60° around X
            Quaternionf target = new Quaternionf()
                .rotateX((float) Math.toRadians(60.0));

            // angularFactor = 1.0 → at dt=0.05: angularK_f = 1.0 → snap to target
            HaloDampingConfig config = new HaloDampingConfig(0.1, 1.0, 10.0, 180.0);

            Quaternionf result = DampingPhysics.computeDampedRotation(
                new Quaternionf(), target, config, state, DT
            );

            // Should be 60°
            double angleDeg = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(result.w))));
            assertEquals(60.0, angleDeg, 0.01,
                "instant angular snap (angularK=1 → angularK_f=1) should match target angle");
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

            // angularFactor = 1.0 → angularK_f = 1.0 → would give full 120°
            // maxAngularDegrees = 45.0 → clamp to 45°
            HaloDampingConfig config = new HaloDampingConfig(0.1, 1.0, 10.0, 45.0);

            Quaternionf result = DampingPhysics.computeDampedRotation(
                new Quaternionf(), target, config, state, DT
            );

            double angleDeg = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(result.w))));
            assertEquals(45.0, angleDeg, 0.01,
                "angular clamp should cap the result at maxAngularDegrees");
        }
    }

    // ------------------------------------------------------------------
    // 5. Frame-rate independence (NEW — core feature)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Frame-rate-independent damping")
    class FrameRateIndependence {

        @Test
        @DisplayName("same k and total time → same convergence regardless of step count")
        void testFrameRateIndependence() {
            // With k = 0.5, after 0.05s at 20 TPS: remaining = (1−0.5) = 0.5
            // After 3 steps of dt=0.0167 (60 FPS for 0.05s):
            //   each step: k_f = 1 − (1−0.5)^(0.0167/0.05) = 1 − 0.5^0.334 ≈ 0.206
            //   damped = current × (1−0.206) = current × 0.794
            //   after 3 steps: 0.794^3 ≈ 0.5 → same as single 20-TPS tick!
            HaloDampingState state20 = new HaloDampingState();
            state20.needsSnap = false;
            HaloDampingConfig config = new HaloDampingConfig(0.5, 0.3, 100.0, 180.0);

            // One step at 20 TPS (dt=0.05)
            Vec3d pos20 = new Vec3d(10.0, 0.0, 0.0);
            pos20 = DampingPhysics.computeDampedPosition(pos20, Vec3d.ZERO, config, state20, 0.05);

            // Three steps at 60 FPS (dt≈0.0167)
            HaloDampingState state60 = new HaloDampingState();
            state60.needsSnap = false;
            Vec3d pos60 = new Vec3d(10.0, 0.0, 0.0);
            pos60 = DampingPhysics.computeDampedPosition(pos60, Vec3d.ZERO, config, state60, 0.0167);
            pos60 = DampingPhysics.computeDampedPosition(pos60, Vec3d.ZERO, config, state60, 0.0167);
            pos60 = DampingPhysics.computeDampedPosition(pos60, Vec3d.ZERO, config, state60, 0.0167);

            // Total real time is ~0.05s in both cases → magnitude should match within tolerance
            assertEquals(pos20.length(), pos60.length(), 0.01,
                "frame-rate-independent: 1× 20TPS step and 3× 60FPS steps over same real time "
                + "should converge to same distance");
        }

        @Test
        @DisplayName("k_f = 1 − (1−k)^(Δt/0.05) — exact formula at dt=0.05")
        void testFactorAtReferenceTick() {
            // At dt=0.05, k_f = 1 − (1−k)^1 = k
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;
            HaloDampingConfig config = new HaloDampingConfig(0.3, 0.3, 10.0, 180.0);

            // With current = (10,0,0), k=0.3:
            // k_f = 0.3, damped = 10 * (1-0.3) = 7.0
            Vec3d result = DampingPhysics.computeDampedPosition(
                new Vec3d(10.0, 0.0, 0.0), Vec3d.ZERO, config, state, 0.05
            );
            assertEquals(7.0, result.x, 0.0001,
                "at reference tick, damped = current × (1−k)");
        }

        @Test
        @DisplayName("k_f formula: extreme k=0 (frozen) → k_f=0 regardless of dt")
        void testFactorFrozen() {
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;
            HaloDampingConfig config = new HaloDampingConfig(0.0, 0.3, 10.0, 180.0);

            // k_f = 1 − (1−0)^(dt/0.05) = 1 − 1 = 0
            // damped = current × (1−0) = current (full retention)
            Vec3d result = DampingPhysics.computeDampedPosition(
                new Vec3d(10.0, 0.0, 0.0), Vec3d.ZERO, config, state, 0.1
            );
            assertEquals(10.0, result.x, 0.0001,
                "k=0 means halo never moves, regardless of dt");
        }

        @Test
        @DisplayName("k_f formula: extreme k=1 (snap) → k_f=1 regardless of dt")
        void testFactorSnap() {
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;
            HaloDampingConfig config = new HaloDampingConfig(1.0, 0.3, 10.0, 180.0);

            // k_f = 1 − (1−1)^(dt/0.05) = 1 − 0 = 1
            // damped = current × (1−1) = 0 (instant snap to target)
            Vec3d result = DampingPhysics.computeDampedPosition(
                new Vec3d(10.0, 0.0, 0.0), Vec3d.ZERO, config, state, 0.01
            );
            assertEquals(0.0, result.x, 0.0001,
                "k=1 means instant snap to target, regardless of dt");
        }

        @Test
        @DisplayName("high dt (long pause) is clamped to prevent overshoot")
        void testLongDeltaTime() {
            HaloDampingState state = new HaloDampingState();
            state.needsSnap = false;
            // k=0.5, dt=1.0s (20× reference tick)
            // Without clamping: k_f = 1 − 0.5^20 ≈ 0.999999
            // damped = 10 × (1−0.999999) ≈ 0.00001 → nearly instant snap
            // With exponent capped at 10: k_f = 1 − 0.5^10 ≈ 0.9990
            // damped = 10 × 0.001 ≈ 0.01
            HaloDampingConfig config = new HaloDampingConfig(0.5, 0.3, 100.0, 180.0);

            Vec3d result = DampingPhysics.computeDampedPosition(
                new Vec3d(10.0, 0.0, 0.0), Vec3d.ZERO, config, state, 1.0
            );
            // Should have converged substantially but not be zero
            assertTrue(result.x < 1.0,
                "after 1s pause halo should have converged substantially");
            assertTrue(result.x > 0.0,
                "exponent clamping prevents instant snap on long pauses");
        }
    }

    // ------------------------------------------------------------------
    // 6. Edge cases
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
                new Vec3d(5, 0, 0), Vec3d.ZERO, config, state, DT
            );

            // After snap, prevRelPos must be (0,0,0)
            assertEquals(Vec3d.ZERO, state.prevRelativePosition);

            // Second tick (no snap): current = target = (0,0,0)
            // damped = 0 * (1−0.5) + 0 * 0.5 = 0
            Vec3d r2 = DampingPhysics.computeDampedPosition(
                Vec3d.ZERO, Vec3d.ZERO, config, state, DT
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
