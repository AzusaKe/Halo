package network.azusake.halo.physics;

import network.azusake.halo.api.HeadAnchor;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the roll-aware pure math in {@link AnchorFrameCalculator}:
 * head-relative offset projection, the SYNC head quaternion, and the LOCKED
 * compass-spin reaction to a rolled head-up vector.
 */
class AnchorFrameCalculatorTest {

    private static final double EPS = 1e-4;

    private static void assertVec(Vec3d expected, Vec3d actual) {
        assertEquals(expected.x, actual.x, EPS, "x");
        assertEquals(expected.y, actual.y, EPS, "y");
        assertEquals(expected.z, actual.z, EPS, "z");
    }

    private static Vec3d rotate(Vec3d v, Quaternionf q) {
        Quaternionf qv = new Quaternionf((float) v.x, (float) v.y, (float) v.z, 0);
        Quaternionf qConj = new Quaternionf(q).conjugate();
        Quaternionf result = q.mul(qv, new Quaternionf()).mul(qConj);
        return new Vec3d(result.x, result.y, result.z);
    }

    @Nested
    @DisplayName("computeHeadRelativeOffset")
    class HeadRelativeOffset {

        @Test
        void roll0MatchesLegacyProjection() {
            Vec3d offset = new Vec3d(0.4, 0.35, 0.2);
            // yaw=0 pitch=0: right=(-1,0,0), headUp=(0,1,0), behind=(0,0,-1)
            Vec3d result = AnchorFrameCalculator.computeHeadRelativeOffset(0f, 0f, 0f, offset);
            assertVec(new Vec3d(-0.4, 0.35, -0.2), result);
        }

        @Test
        void roll90RotatesProjection() {
            Vec3d offset = new Vec3d(0.4, 0.35, 0.2);
            // yaw=0 pitch=0 roll=90: right=(0,-1,0), headUp=(-1,0,0), behind=(0,0,-1)
            Vec3d result = AnchorFrameCalculator.computeHeadRelativeOffset(0f, 0f, 90f, offset);
            assertVec(new Vec3d(-0.35, -0.4, -0.2), result);
        }

        @Test
        void offsetLengthPreserved() {
            Vec3d offset = new Vec3d(0.3, 0.4, 0.5);
            for (float roll : new float[]{0f, 30f, 90f, -60f}) {
                Vec3d result = AnchorFrameCalculator.computeHeadRelativeOffset(20f, 15f, roll, offset);
                assertEquals(offset.length(), result.length(), EPS, "offset length");
            }
        }
    }

    @Nested
    @DisplayName("buildHeadQuaternion matches the HeadFrameMath basis")
    class HeadQuaternion {

        @Test
        void mapsHeadFrameForRoll90() {
            HeadFrameMath.HeadFrame frame = HeadFrameMath.of(0f, 0f, 90f);
            Quaternionf q = AnchorFrameCalculator.buildHeadQuaternion(0f, 0f, 90f);
            Vector3f up = q.transform(new Vector3f(0, 1, 0));
            Vector3f fwd = q.transform(new Vector3f(0, 0, 1));
            assertVec(frame.headUp(), new Vec3d(up.x, up.y, up.z));
            assertVec(frame.forward(), new Vec3d(fwd.x, fwd.y, fwd.z));
        }

        @Test
        void mapsHeadFrameForArbitraryAngles() {
            for (float yaw : new float[]{-135f, 45f, 170f}) {
                for (float pitch : new float[]{-50f, 15f, 70f}) {
                    for (float roll : new float[]{-80f, 0f, 60f}) {
                        HeadFrameMath.HeadFrame frame = HeadFrameMath.of(yaw, pitch, roll);
                        Quaternionf q = AnchorFrameCalculator.buildHeadQuaternion(yaw, pitch, roll);
                        Vector3f up = q.transform(new Vector3f(0, 1, 0));
                        Vector3f fwd = q.transform(new Vector3f(0, 0, 1));
                        assertVec(frame.headUp(), new Vec3d(up.x, up.y, up.z));
                        assertVec(frame.forward(), new Vec3d(fwd.x, fwd.y, fwd.z));
                    }
                }
            }
        }

        @Test
        void roll0MatchesPreUpgradeQuaternion() {
            // Before the 6DOF upgrade the SYNC quaternion was rotateY(-yaw) * rotateX(pitch).
            Quaternionf q = AnchorFrameCalculator.buildHeadQuaternion(30f, 20f, 0f);
            Quaternionf legacy = new Quaternionf()
                .rotateY((float) -Math.toRadians(30))
                .rotateX((float) Math.toRadians(20));
            assertEquals(legacy.x, q.x, 1e-6, "x");
            assertEquals(legacy.y, q.y, 1e-6, "y");
            assertEquals(legacy.z, q.z, 1e-6, "z");
            assertEquals(legacy.w, q.w, 1e-6, "w");
        }

        @Test
        void matchesCameraRotationYXZ() {
            // Minecraft's Camera builds its rotation as
            // rotationYXZ(-yaw, pitch, roll); SYNC must match it exactly.
            for (float yaw : new float[]{-135f, 45f, 170f}) {
                for (float pitch : new float[]{-50f, 15f, 70f}) {
                    for (float roll : new float[]{-80f, 0f, 60f}) {
                        Quaternionf q = AnchorFrameCalculator.buildHeadQuaternion(yaw, pitch, roll);
                        Quaternionf expected = new Quaternionf().rotationYXZ(
                            (float) -Math.toRadians(yaw),
                            (float) Math.toRadians(pitch),
                            (float) Math.toRadians(roll));
                        assertEquals(expected.x, q.x, 1e-6, "x yaw=" + yaw + " pitch=" + pitch + " roll=" + roll);
                        assertEquals(expected.y, q.y, 1e-6, "y yaw=" + yaw + " pitch=" + pitch + " roll=" + roll);
                        assertEquals(expected.z, q.z, 1e-6, "z yaw=" + yaw + " pitch=" + pitch + " roll=" + roll);
                        assertEquals(expected.w, q.w, 1e-6, "w yaw=" + yaw + " pitch=" + pitch + " roll=" + roll);
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("LOCKED compass spin follows a rolled head-up vector")
    class LockedSpin {

        private static final Vec3d TO_HEAD = new Vec3d(
            0.4082482905, 0.8164965809, 0.4082482905); // (1,2,1)/sqrt(6)

        @Test
        void defPlusZAlignsWithRolledUpPole() {
            Vec3d P = TO_HEAD.multiply(-1); // head → halo direction (unit)
            for (float roll : new float[]{0f, 45f, 90f, -30f}) {
                Vec3d headUp = HeadFrameMath.of(0f, 0f, roll).headUp();

                Quaternionf Q_lookAt = AnchorFrameCalculator.computeLookAtOrientation(TO_HEAD);
                Quaternionf spin = AnchorFrameCalculator.computeLockedSpin(Q_lookAt, TO_HEAD, headUp, P);

                // Expected: the up pole (headUp projected onto the tangent plane
                // at P), which is already perpendicular to toHead = -P here.
                Vec3d pole = headUp.subtract(P.multiply(headUp.dotProduct(P))).normalize();

                Vec3d zActual = rotate(new Vec3d(0, 0, 1), spin.mul(Q_lookAt, new Quaternionf()));
                assertVec(pole, zActual);
            }
        }

        @Test
        void rolledHeadUpChangesSpinVsUpright() {
            Vec3d P = TO_HEAD.multiply(-1);
            Quaternionf Q_lookAt = AnchorFrameCalculator.computeLookAtOrientation(TO_HEAD);

            Quaternionf spinUpright = AnchorFrameCalculator.computeLockedSpin(
                Q_lookAt, TO_HEAD, new Vec3d(0, 1, 0), P);
            Quaternionf spinRolled = AnchorFrameCalculator.computeLockedSpin(
                Q_lookAt, TO_HEAD, new Vec3d(-1, 0, 0), P);

            assertNotEquals(spinUpright, spinRolled, "roll must change the locked spin");
        }
    }

    @Nested
    @DisplayName("NaN / degenerate-frame defense")
    class BadFrameDefense {

        @Test
        void lookAtZeroVectorReturnsIdentity() {
            Quaternionf q = AnchorFrameCalculator.computeLookAtOrientation(Vec3d.ZERO);
            assertEquals(0f, q.x, 0f);
            assertEquals(0f, q.y, 0f);
            assertEquals(0f, q.z, 0f);
            assertEquals(1f, q.w, 0f);
        }

        @Test
        void lookAtNaNVectorReturnsIdentity() {
            Quaternionf q = AnchorFrameCalculator.computeLookAtOrientation(new Vec3d(Double.NaN, 0, 0));
            assertEquals(0f, q.x, 0f);
            assertEquals(0f, q.y, 0f);
            assertEquals(0f, q.z, 0f);
            assertEquals(1f, q.w, 0f);
        }

        @Test
        void lookAtStillAlignsNormalForValidDirection() {
            Quaternionf q = AnchorFrameCalculator.computeLookAtOrientation(new Vec3d(0, 1, 0));
            Vector3f out = q.transform(new Vector3f(0, -1, 0));
            assertEquals(0f, out.x, EPS);
            assertEquals(1f, out.y, EPS);
            assertEquals(0f, out.z, EPS);
        }

        @Test
        void isFiniteRejectsNaNAnchors() {
            assertTrue(AnchorFrameCalculator.isFinite(new HeadAnchor(new Vec3d(1, 2, 3), 10f, 20f, 0f)));
            assertFalse(AnchorFrameCalculator.isFinite(new HeadAnchor(new Vec3d(Double.NaN, 2, 3), 10f, 20f, 0f)));
            assertFalse(AnchorFrameCalculator.isFinite(new HeadAnchor(new Vec3d(1, 2, 3), Float.NaN, 20f, 0f)));
            assertFalse(AnchorFrameCalculator.isFinite(new HeadAnchor(new Vec3d(1, 2, 3), 10f, 20f, Float.POSITIVE_INFINITY)));
            assertFalse(AnchorFrameCalculator.isFinite(null));
        }

        @Test
        void dampPositionNeverReturnsNaN() {
            Vec3d prev = new Vec3d(100, 64, -100);
            Vec3d target = new Vec3d(101, 64, -99);

            // NaN target with a good previous position → hold the previous position
            Vec3d held = AnchorFrameCalculator.dampPosition(
                prev, new Vec3d(Double.NaN, Double.NaN, Double.NaN), 0.2, 1.0);
            assertVec(prev, held);

            // NaN damped value (poisoned previous state) with a finite target → snap to target
            Vec3d snapped = AnchorFrameCalculator.dampPosition(
                new Vec3d(Double.NaN, Double.NaN, Double.NaN), target, 0.2, 1.0);
            assertVec(target, snapped);

            // First frame (null previous) with a finite target → target
            assertVec(target, AnchorFrameCalculator.dampPosition(null, target, 0.2, 1.0));

            // Finite case still clamps to maxDist
            Vec3d farTarget = new Vec3d(200, 64, 0);
            Vec3d clamped = AnchorFrameCalculator.dampPosition(new Vec3d(0, 0, 0), farTarget, 0.1, 1.0);
            assertEquals(1.0, clamped.distanceTo(farTarget), EPS);
        }
    }
}
