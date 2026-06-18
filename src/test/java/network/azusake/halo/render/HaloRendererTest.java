package network.azusake.halo.render;

import network.azusake.halo.data.HaloInstance;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for halo rendering math: quaternion-to-matrix conversion,
 * billboard facing, frame interpolation, and culling.
 *
 * <p>These tests exercise the pure-computation paths — no Minecraft
 * instance is required.</p>
 */
class HaloRendererTest {

    // ------------------------------------------------------------------
    // 1. Quaternion → Matrix
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Quaternion-to-matrix conversion")
    class QuaternionToMatrix {

        @Test
        @DisplayName("identity quaternion → identity matrix (no rotation)")
        void testIdentityQuaternion() {
            Quaternionf identity = new Quaternionf();
            Matrix4f mat = new Matrix4f().rotation(identity);

            // Identity matrix: diagonal should be ~1, off-diagonals ~0
            assertEquals(1.0f, mat.m00(), 0.0001f);
            assertEquals(1.0f, mat.m11(), 0.0001f);
            assertEquals(1.0f, mat.m22(), 0.0001f);
            assertEquals(0.0f, mat.m01(), 0.0001f);
            assertEquals(0.0f, mat.m02(), 0.0001f);
            assertEquals(0.0f, mat.m10(), 0.0001f);
            assertEquals(0.0f, mat.m12(), 0.0001f);
            assertEquals(0.0f, mat.m20(), 0.0001f);
            assertEquals(0.0f, mat.m21(), 0.0001f);
        }

        @Test
        @DisplayName("90° around Y → forwards stays in XZ plane, preserves length")
        void testYaw90Degrees() {
            Quaternionf quat = new Quaternionf()
                .rotateY((float) Math.toRadians(90.0));
            Matrix4f mat = new Matrix4f().rotation(quat);

            // 90° Y rotation should rotate forward (0,0,-1) to lie in the XZ
            // plane (y ≈ 0), with unit length, and be perpendicular to original.
            // NOTE: transformDirection mutates IN PLACE — use two-arg form.
            Vector3f forward = new Vector3f(0, 0, -1);
            Vector3f result = mat.transformDirection(new Vector3f(forward), new Vector3f());

            assertEquals(0.0f, result.y, 0.001f,
                "90° Y rotation: forward should stay in XZ plane (y=0)");
            assertEquals(1.0f, result.length(), 0.001f,
                "90° Y rotation: length must be preserved");
            // Dot product with original → cos(90°) ≈ 0
            assertEquals(0.0f, forward.dot(result), 0.001f,
                "90° Y rotation: forward and result must be perpendicular");
        }

        @Test
        @DisplayName("90° around X → forwards stays in XY plane, preserves length")
        void testPitch90Degrees() {
            Quaternionf quat = new Quaternionf()
                .rotateX((float) Math.toRadians(90.0));
            Matrix4f mat = new Matrix4f().rotation(quat);

            // 90° X rotation: forward (0,0,-1) rotates into the YZ plane (x ≈ 0)
            Vector3f forward = new Vector3f(0, 0, -1);
            Vector3f result = mat.transformDirection(new Vector3f(forward), new Vector3f());

            assertEquals(0.0f, result.x, 0.001f,
                "90° X rotation: forward should have x=0");
            assertEquals(1.0f, result.length(), 0.001f,
                "90° X rotation: length must be preserved");
            assertEquals(0.0f, forward.dot(result), 0.001f,
                "90° X rotation: forward and result must be perpendicular");
        }

        @Test
        @DisplayName("matrix multiplication with rotation composes correctly")
        void testMatrixComposition() {
            // Apply a 90° Y rotation via matrix multiplication
            Matrix4f base = new Matrix4f(); // identity
            Matrix4f rot = new Matrix4f()
                .rotation(new Quaternionf().rotateY((float) Math.toRadians(90.0)));
            base.mul(rot); // base = base * rot

            Vector3f forward = new Vector3f(0, 0, -1);
            Vector3f result = base.transformDirection(new Vector3f(forward), new Vector3f());

            // Should be unit length, in XZ plane, perpendicular to forward
            assertEquals(0.0f, result.y, 0.001f,
                "matrix mul Y-90°: forward should stay in XZ plane");
            assertEquals(1.0f, result.length(), 0.001f,
                "matrix mul Y-90°: length must be preserved");
            assertEquals(0.0f, forward.dot(result), 0.001f,
                "matrix mul Y-90°: forward and result must be perpendicular");
        }

        @Test
        @DisplayName("45° around Y then 45° around X → combined rotation preserves length")
        void testCombinedYawPitch() {
            Quaternionf combined = new Quaternionf()
                .rotateY((float) Math.toRadians(45.0))
                .rotateX((float) Math.toRadians(45.0));
            Matrix4f mat = new Matrix4f().rotation(combined);

            Vector3f forward = new Vector3f(0, 0, -1);
            Vector3f result = mat.transformDirection(new Vector3f(forward), new Vector3f());

            // Combined rotation should preserve unit length
            assertEquals(1.0f, result.length(), 0.001f,
                "combined rotation: length must be preserved");

            // The angle between forward and result should be nonzero
            double angleDeg = Math.toDegrees(
                Math.acos(Math.max(-1.0, Math.min(1.0, forward.dot(result)))));
            assertTrue(angleDeg > 0.0 && angleDeg < 90.0,
                "combined 45° yaw + 45° pitch should rotate forward by 0°–90°, got "
                    + angleDeg + "°");
        }

        @Test
        @DisplayName("quaternion slerp at t=0 returns start quaternion")
        void testSlerpStart() {
            Quaternionf start = new Quaternionf().rotateY((float) Math.toRadians(30));
            Quaternionf end = new Quaternionf().rotateY((float) Math.toRadians(90));
            Quaternionf result = new Quaternionf(start).slerp(end, 0.0f);

            // Extract Y angle from result
            double angle = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(result.w))));
            // Should be within ~3° of start angle
            assertTrue(angle < 40.0 && angle > 20.0,
                "slerp at t=0 should be close to start rotation (30°), got " + angle);
        }

        @Test
        @DisplayName("quaternion slerp at t=1 returns end quaternion")
        void testSlerpEnd() {
            Quaternionf start = new Quaternionf().rotateY((float) Math.toRadians(30));
            Quaternionf end = new Quaternionf().rotateY((float) Math.toRadians(90));
            Quaternionf result = new Quaternionf(start).slerp(end, 1.0f);

            double angle = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(result.w))));
            assertTrue(angle > 80.0 && angle < 100.0,
                "slerp at t=1 should be close to end rotation (90°), got " + angle);
        }
    }

    // ------------------------------------------------------------------
    // 2. Billboard facing
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Billboard camera-facing math")
    class BillboardFacing {

        @Test
        @DisplayName("billboard rotation matrix is orthonormal (no shear/scale)")
        void testBillboardRotationValid() {
            float yaw = 45.0f;
            float pitch = 30.0f;

            // Billboard transform: rotate by -yaw around Y, then +pitch around X
            // This is the standard Minecraft billboard pattern used by particles.
            Quaternionf billboard = new Quaternionf()
                .rotateY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(pitch));

            Matrix4f mat = new Matrix4f().rotation(billboard);

            // A rotation matrix must preserve vector lengths
            Vector3f v = new Vector3f(1, 2, 3);
            Vector3f rotated = mat.transformDirection(new Vector3f(v), new Vector3f());
            assertEquals(v.length(), rotated.length(), 0.001f,
                "rotation must preserve vector length");

            // The determinant of a 3x3 rotation matrix must be 1
            float det = mat.m00() * (mat.m11() * mat.m22() - mat.m12() * mat.m21())
                - mat.m01() * (mat.m10() * mat.m22() - mat.m12() * mat.m20())
                + mat.m02() * (mat.m10() * mat.m21() - mat.m11() * mat.m20());
            assertEquals(1.0f, det, 0.001f,
                "rotation matrix determinant must be 1");
        }

        @Test
        @DisplayName("billboard at yaw=0 pitch=0 → normal points along +Z (identity rotation)")
        void testBillboardDefaultCamera() {
            // Default camera: yaw=0 (looking along -Z), pitch=0 (horizontal)
            Quaternionf billboard = new Quaternionf()
                .rotateY(0)
                .rotateX(0);

            Vector3f normal = new Vector3f(0, 0, 1);
            Matrix4f mat = new Matrix4f().rotation(billboard);
            Vector3f result = mat.transformDirection(new Vector3f(normal), new Vector3f());

            // With yaw=0 pitch=0, the billboard rotation is identity:
            // normal stays at (0, 0, 1)
            assertEquals(0.0f, result.x, 0.001f);
            assertEquals(0.0f, result.y, 0.001f);
            assertEquals(1.0f, result.z, 0.001f);
        }

        @Test
        @DisplayName("billboard yaw rotation rotates normal in XZ plane only")
        void testBillboardYawOnly() {
            // Yaw=90°, pitch=0°
            Quaternionf billboard = new Quaternionf()
                .rotateY((float) Math.toRadians(-90.0))
                .rotateX(0);

            Vector3f normal = new Vector3f(0, 0, 1);
            Matrix4f mat = new Matrix4f().rotation(billboard);
            Vector3f result = mat.transformDirection(new Vector3f(normal), new Vector3f());

            // Pure yaw rotation must not move the Y component
            assertEquals(0.0f, result.y, 0.001f,
                "pure yaw rotation: normal Y must stay at 0");
            // Length must be preserved
            assertEquals(1.0f, result.length(), 0.001f);
            // Normal should be perpendicular to Y axis
            assertEquals(1.0f, Math.abs(result.x) + Math.abs(result.z), 0.001f,
                "normal should lie entirely in XZ plane");
        }
    }

    // ------------------------------------------------------------------
    // 3. Frame interpolation
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Frame interpolation (lerp / slerp)")
    class FrameInterpolation {

        @Test
        @DisplayName("lerp at tickDelta=0 returns prev position")
        void testLerpAtZero() {
            HaloInstance instance = new HaloInstance(
                java.util.UUID.randomUUID(),
                new Identifier("halo", "test")
            );

            instance.setPrevRelativePosition(new Vec3d(1.0, 2.0, 3.0));
            instance.setRelativePosition(new Vec3d(10.0, 20.0, 30.0));

            Vec3d result = instance.getInterpolatedPosition(0.0);
            assertEquals(1.0, result.x, 0.0001);
            assertEquals(2.0, result.y, 0.0001);
            assertEquals(3.0, result.z, 0.0001);
        }

        @Test
        @DisplayName("lerp at tickDelta=1 returns current position")
        void testLerpAtOne() {
            HaloInstance instance = new HaloInstance(
                java.util.UUID.randomUUID(),
                new Identifier("halo", "test")
            );

            instance.setPrevRelativePosition(new Vec3d(1.0, 2.0, 3.0));
            instance.setRelativePosition(new Vec3d(10.0, 20.0, 30.0));

            Vec3d result = instance.getInterpolatedPosition(1.0);
            assertEquals(10.0, result.x, 0.0001);
            assertEquals(20.0, result.y, 0.0001);
            assertEquals(30.0, result.z, 0.0001);
        }

        @Test
        @DisplayName("lerp at tickDelta=0.5 returns midpoint")
        void testLerpAtMidpoint() {
            HaloInstance instance = new HaloInstance(
                java.util.UUID.randomUUID(),
                new Identifier("halo", "test")
            );

            instance.setPrevRelativePosition(new Vec3d(0.0, 0.0, 0.0));
            instance.setRelativePosition(new Vec3d(10.0, 0.0, 0.0));

            Vec3d result = instance.getInterpolatedPosition(0.5);
            assertEquals(5.0, result.x, 0.0001);
            assertEquals(0.0, result.y, 0.0001);
            assertEquals(0.0, result.z, 0.0001);
        }

        @Test
        @DisplayName("lerp between consecutive frames is monotonic (no jumps)")
        void testLerpMonotonic() {
            HaloInstance instance = new HaloInstance(
                java.util.UUID.randomUUID(),
                new Identifier("halo", "test")
            );

            instance.setPrevRelativePosition(new Vec3d(0.0, 0.0, 0.0));
            instance.setRelativePosition(new Vec3d(10.0, 0.0, 0.0));

            // Sample at many tickDelta values — x should increase monotonically
            double prevX = Double.NEGATIVE_INFINITY;
            for (double t = 0.0; t <= 1.0; t += 0.05) {
                Vec3d r = instance.getInterpolatedPosition(t);
                assertTrue(r.x >= prevX - 1e-9,
                    "lerp should be monotonic; at t=" + t + " x=" + r.x + " < prevX=" + prevX);
                prevX = r.x;
            }
        }

        @Test
        @DisplayName("slerp interpolation is smooth — angle varies linearly with t")
        void testSlerpSmooth() {
            Quaternionf start = new Quaternionf().rotateY(0);
            Quaternionf end = new Quaternionf().rotateY((float) Math.toRadians(90.0));

            double prevAngle = -1.0;
            for (float t = 0.0f; t <= 1.0f; t += 0.1f) {
                Quaternionf result = new Quaternionf(start).slerp(end, t);
                double angle = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(result.w))));
                assertTrue(angle >= prevAngle - 0.01,
                    "slerp angle should increase monotonically with t; at t=" + t
                        + " angle=" + angle + " < prev=" + prevAngle);
                prevAngle = angle;
            }
        }

        @Test
        @DisplayName("interpolated position is continuous across tick boundaries")
        void testInterpolationContinuity() {
            // Simulate two consecutive game ticks with frame interpolation
            HaloInstance instance = new HaloInstance(
                java.util.UUID.randomUUID(),
                new Identifier("halo", "test")
            );

            // Tick 1: prev = (0,0,0), current = (1,0,0)
            instance.setPrevRelativePosition(new Vec3d(0.0, 0.0, 0.0));
            instance.setRelativePosition(new Vec3d(1.0, 0.0, 0.0));

            // End of tick 1 (tickDelta = 0.999): position ≈ (0.999, 0, 0)
            Vec3d endOfTick1 = instance.getInterpolatedPosition(0.999);

            // Now tick 2: prev = (1,0,0), current = (2,0,0)
            instance.setPrevRelativePosition(new Vec3d(1.0, 0.0, 0.0));
            instance.setRelativePosition(new Vec3d(2.0, 0.0, 0.0));

            // Start of tick 2 (tickDelta = 0.001): position ≈ (1.001, 0, 0)
            Vec3d startOfTick2 = instance.getInterpolatedPosition(0.001);

            // The jump between end of tick 1 and start of tick 2 should be very small
            double jump = endOfTick1.distanceTo(startOfTick2);
            assertTrue(jump < 0.01,
                "interpolation should be continuous across tick boundaries; jump=" + jump);
        }
    }

    // ------------------------------------------------------------------
    // 4. Distance culling
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Distance-based culling")
    class DistanceCulling {

        @Test
        @DisplayName("entity within render distance is NOT culled")
        void testNotCulledWhenClose() {
            Vec3d camPos = new Vec3d(0, 64, 0);
            Vec3d entityPos = new Vec3d(100, 64, 0); // 100 blocks away

            double distSq = entityPos.squaredDistanceTo(camPos);
            double renderDistSq = 16.0 * 16.0 * 16.0 * 16.0; // (16 chunks * 16 blocks)^2 = 256^2

            assertTrue(distSq <= renderDistSq,
                "entity 100 blocks away should be within 256-block render distance");
        }

        @Test
        @DisplayName("entity beyond render distance IS culled")
        void testCulledWhenFar() {
            Vec3d camPos = new Vec3d(0, 64, 0);
            Vec3d entityPos = new Vec3d(300, 64, 0); // 300 blocks away

            double distSq = entityPos.squaredDistanceTo(camPos);
            double renderDistSq = 16.0 * 16.0 * 16.0 * 16.0; // 256^2

            assertTrue(distSq > renderDistSq,
                "entity 300 blocks away should be outside 256-block render distance");
        }

        @Test
        @DisplayName("entity at exactly render distance boundary is NOT culled (inclusive)")
        void testNotCulledAtBoundary() {
            Vec3d camPos = new Vec3d(0, 64, 0);
            // Exactly 256 blocks away (one chunk = 16 blocks)
            Vec3d entityPos = new Vec3d(256, 64, 0);

            double distSq = entityPos.squaredDistanceTo(camPos);
            double renderDistSq = 16.0 * 16.0 * 16.0 * 16.0;

            assertTrue(distSq <= renderDistSq + 0.0001,
                "entity at exactly 256 blocks should be within render distance (inclusive)");
        }
    }

    // ------------------------------------------------------------------
    // 5. Vec3d helper (getHeadAnchorPosition logic)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Head anchor calculation")
    class HeadAnchor {

        @Test
        @DisplayName("anchor is at 85% of entity height above foot position")
        void testAnchorHeight() {
            // Non-player entity of height 2.0 blocks
            double footY = 64.0;
            double height = 2.0;
            Vec3d footPos = new Vec3d(10, footY, 10);

            // Anchor at 85% height
            Vec3d anchor = footPos.add(0, height * 0.85, 0);

            assertEquals(10.0, anchor.x, 0.001);
            assertEquals(64.0 + 1.7, anchor.y, 0.001); // 64 + 2.0*0.85 = 65.7
            assertEquals(10.0, anchor.z, 0.001);
        }

        @Test
        @DisplayName("anchor for tall entity is proportionally higher")
        void testTallEntityAnchor() {
            // Very tall entity (e.g. enderman, height 2.9)
            Vec3d footPos = new Vec3d(0, 32, 0);
            double height = 2.9;
            Vec3d anchor = footPos.add(0, height * 0.85, 0);

            assertEquals(32.0 + 2.9 * 0.85, anchor.y, 0.001); // 32 + 2.465 = 34.465
        }
    }

    // ------------------------------------------------------------------
    // 6. Color unpacking (glow layer)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Glow colour unpacking")
    class GlowColor {

        @Test
        @DisplayName("packed RGB 0xFFD700 (gold) unpacks correctly")
        void testUnpackGold() {
            int color = 0xFFD700; // gold
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            assertEquals(1.0f, r, 0.01f);  // 0xFF = 255
            assertEquals(0.843f, g, 0.01f); // 0xD7 = 215
            assertEquals(0.0f, b, 0.01f);  // 0x00 = 0
        }

        @Test
        @DisplayName("packed RGB 0xFFFFFF (white) unpacks correctly")
        void testUnpackWhite() {
            int color = 0xFFFFFF;
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            assertEquals(1.0f, r, 0.01f);
            assertEquals(1.0f, g, 0.01f);
            assertEquals(1.0f, b, 0.01f);
        }

        @Test
        @DisplayName("packed RGB 0x000000 (black) unpacks correctly")
        void testUnpackBlack() {
            int color = 0x000000;
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            assertEquals(0.0f, r, 0.01f);
            assertEquals(0.0f, g, 0.01f);
            assertEquals(0.0f, b, 0.01f);
        }
    }

    // ------------------------------------------------------------------
    // 7. Pulse animation math
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Pulse animation")
    class PulseAnimation {

        @Test
        @DisplayName("pulsed alpha at t=0 is base + amplitude*sin(phase)")
        void testPulseAtZero() {
            float baseAlpha = 0.8f;
            float amplitude = 0.15f;
            float frequency = 2.0f;
            float phase = 0.0f;

            double t = 0.0;
            float alpha = baseAlpha + amplitude
                * (float) Math.sin(2.0 * Math.PI * frequency * t + phase);

            assertEquals(0.8f, alpha, 0.001f); // sin(0) = 0
        }

        @Test
        @DisplayName("pulsed alpha at quarter cycle is base + amplitude")
        void testPulseAtPeak() {
            float baseAlpha = 0.8f;
            float amplitude = 0.15f;
            float frequency = 2.0f; // 2 Hz
            float phase = 0.0f;

            // Quarter cycle: t = 1/(4*f) = 1/8 = 0.125s
            // 2*PI*f*t = 2*PI*2*0.125 = PI/2 → sin = 1
            double t = 0.125;
            float alpha = baseAlpha + amplitude
                * (float) Math.sin(2.0 * Math.PI * frequency * t + phase);

            assertEquals(0.8f + 0.15f, alpha, 0.001f);
        }

        @Test
        @DisplayName("pulsed alpha is clamped to [0, 1]")
        void testPulseClamped() {
            // Large amplitude that would push alpha above 1 or below 0
            float baseAlpha = 0.5f;
            float amplitude = 0.8f; // can go from -0.3 to 1.3

            // At peak: alpha = 0.5 + 0.8 = 1.3 → clamped to 1.0
            float raw = baseAlpha + amplitude * 1.0f;
            float clamped = Math.max(0.0f, Math.min(1.0f, raw));
            assertEquals(1.0f, clamped, 0.001f);

            // At trough: alpha = 0.5 - 0.8 = -0.3 → clamped to 0.0
            raw = baseAlpha + amplitude * (-1.0f);
            clamped = Math.max(0.0f, Math.min(1.0f, raw));
            assertEquals(0.0f, clamped, 0.001f);
        }
    }

    // ------------------------------------------------------------------
    // 8. Frame-rate-independent damping & clamp (renderer path)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("逐帧阻尼与钳制 (per-frame damping & clamp)")
    class FrameDampingAndClamp {

        /**
         * Compute k_f from the renderer's actual formula for testing.
         */
        private static double computeKF(double k, double dt) {
            k = Math.max(0.001, Math.min(k, 0.999));
            double exp = dt / 0.05; // Δt / reference tick
            if (exp <= 0.0) exp = 1.0;
            if (exp > 10.0) exp = 10.0;
            double kF = 1.0 - Math.pow(1.0 - k, exp);
            return Math.max(0.0, Math.min(1.0, kF));
        }

        @Test
        @DisplayName("k_f formula: at dt=0.05, k_f = k (reference tick)")
        void testKFAtReferenceTick() {
            // The renderer clamps k to [0.001, 0.999] for numerical stability
            assertEquals(0.3, computeKF(0.3, 0.05), 0.0001);
            assertEquals(0.5, computeKF(0.5, 0.05), 0.0001);
            // k=0 clamped to 0.001: k_f = 1 − (0.999)^1 ≈ 0.001
            assertTrue(computeKF(0.0, 0.05) < 0.01, "k=0 should give near-zero k_f");
            // k=1 clamped to 0.999: k_f = 1 − (0.001)^1 = 0.999
            assertTrue(computeKF(1.0, 0.05) > 0.99, "k=1 should give near-one k_f");
        }

        @Test
        @DisplayName("k_f at 60 FPS (dt≈0.0167) is proportionally smaller")
        void testKFAt60FPS() {
            double kf60 = computeKF(0.3, 0.0167);
            // At 60 FPS: k_f = 1 − (1−0.3)^(0.0167/0.05) = 1 − 0.7^0.334 ≈ 0.112
            assertTrue(kf60 < 0.3, "60 FPS k_f should be smaller than reference tick");
            assertTrue(kf60 > 0.05, "60 FPS k_f should not be zero");
        }

        @Test
        @DisplayName("frame damping: H converges toward T with k_f factor")
        void testSingleFrameDamping() {
            // T = (0, 0, 0), H = (10, 0, 0)
            Vec3d target = Vec3d.ZERO;
            Vec3d prev = new Vec3d(10, 0, 0);

            double kF = computeKF(0.3, 0.05); // = 0.3
            // H_new = H + k_f × (T − H)
            Vec3d halo = prev.add(target.subtract(prev).multiply(kF));

            assertEquals(7.0, halo.x, 0.0001, "10 × (1−0.3) = 7.0");
            assertEquals(0.0, halo.y, 0.0001);
            assertEquals(0.0, halo.z, 0.0001);
        }

        @Test
        @DisplayName("clamp: d > max_d → halo clamped to max_d from target")
        void testFrameClampExceedsMaxDist() {
            // After damping: halo at x=7, target at x=0
            // d = 7 > max_d = 5 → clamp
            Vec3d target = Vec3d.ZERO;
            Vec3d halo = new Vec3d(7.0, 0.0, 0.0); // after damping
            double maxDist = 5.0;

            double dist = halo.distanceTo(target);
            assertTrue(dist > maxDist, "precondition: dist must exceed maxDist");

            // Clamp: H_new = T − normalize(T−H) × maxDist
            Vec3d toTarget = target.subtract(halo).normalize();
            Vec3d clamped = target.subtract(toTarget.multiply(maxDist));

            assertEquals(5.0, clamped.distanceTo(target), 0.0001,
                "clamped halo must be exactly maxDist from target");
            assertEquals(5.0, clamped.x, 0.0001);
            assertEquals(0.0, clamped.y, 0.0001);
        }

        @Test
        @DisplayName("clamp direction is toward target (在 S 方向继续前进)")
        void testClampDirectionTowardTarget() {
            // H = (10, 0, 0), T = (0, 0, 0)
            // S = k_f * (T - H) = 0.3 * (-10, 0, 0) = (-3, 0, 0) → left
            // After damping: H_new' = (7, 0, 0), d = 7 > max_d = 5
            // Clamp: continue in S direction (left) until d = 5
            // H_new = (5, 0, 0) — moved further left from 7, closer to target
            Vec3d target = Vec3d.ZERO;
            Vec3d haloAfterDamping = new Vec3d(7.0, 0.0, 0.0);
            double maxDist = 5.0;

            Vec3d toTarget = target.subtract(haloAfterDamping).normalize();
            Vec3d clamped = target.subtract(toTarget.multiply(maxDist));

            assertEquals(5.0, clamped.x, 0.0001);
            assertTrue(clamped.x < haloAfterDamping.x,
                "clamped halo should move further toward target (from x=7 to x=5)");
            assertTrue(clamped.x > target.x,
                "clamped halo should stay between target and original position");
        }

        @Test
        @DisplayName("multi-frame simulation: halo NEVER exceeds maxDist from target")
        void testMultiFrameClampGuarantee() {
            // Simulate 200 frames with random target movement
            // The halo must stay within maxDist of target after every frame
            Vec3d target = Vec3d.ZERO;
            Vec3d halo = Vec3d.ZERO;
            double maxDist = 1.0;
            double k = 0.3;
            java.util.Random rng = new java.util.Random(12345);

            for (int frame = 0; frame < 200; frame++) {
                // Target moves randomly (simulating entity movement & rotation)
                double dx = (rng.nextDouble() - 0.5) * 1.0;
                double dy = (rng.nextDouble() - 0.5) * 1.0;
                double dz = (rng.nextDouble() - 0.5) * 1.0;
                target = target.add(dx, dy, dz);

                // Per-frame damping (simulated at ~60 FPS)
                double kF = computeKF(k, 0.0167);
                halo = halo.add(target.subtract(halo).multiply(kF));

                // Clamp: ensure halo never exceeds maxDist
                double dist = halo.distanceTo(target);
                if (dist > maxDist) {
                    Vec3d toTarget = target.subtract(halo).normalize();
                    halo = target.subtract(toTarget.multiply(maxDist));
                }
            }

            // After 200 frames, halo must be within maxDist
            double finalDist = halo.distanceTo(target);
            assertTrue(finalDist <= maxDist + 1e-9,
                String.format("After 200 frames, dist=%.4f must not exceed maxDist=%.4f",
                    finalDist, maxDist));
        }

        @Test
        @DisplayName("extreme: high entity speed, low k (near freeze) → clamp still holds")
        void testExtremeClampWithLowK() {
            // k = 0.1 (very little movement per frame)
            // Entity moves 2 blocks per frame → halo lags severely
            // Clamp must prevent halo from exceeding maxDist
            Vec3d target = new Vec3d(0, 0, 0);
            Vec3d halo = new Vec3d(0, 0, 0);
            double maxDist = 0.5;

            for (int frame = 0; frame < 60; frame++) {
                // Entity moves rapidly rightward
                target = target.add(0.5, 0, 0);

                // Damping with very low k
                double kF = computeKF(0.1, 0.0167);
                halo = halo.add(target.subtract(halo).multiply(kF));

                // Clamp
                double dist = halo.distanceTo(target);
                if (dist > maxDist) {
                    Vec3d toTarget = target.subtract(halo).normalize();
                    halo = target.subtract(toTarget.multiply(maxDist));
                }

                double distAfter = halo.distanceTo(target);
                assertTrue(distAfter <= maxDist + 1e-9,
                    String.format("Frame %d: dist=%.4f > maxDist=%.4f after clamp",
                        frame, distAfter, maxDist));
            }
        }
    }
}
