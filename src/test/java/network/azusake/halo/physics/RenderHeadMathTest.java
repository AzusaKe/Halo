package network.azusake.halo.physics;

import network.azusake.halo.anchor.AnchorPoseMath;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorVec3;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link RenderHeadMath} — the render-capture 6-DOF conversion.
 *
 * <p>The captured matrix follows the vanilla renderer convention: after the
 * entity transform and {@code scale(-1,-1,1)} flip, head-local +X maps to the
 * world right, head-local +Y maps to world {@code -headUp} (the crown is at
 * model -Y) and head-local +Z maps to world {@code -forward} (the face is at
 * model -Z).  {@link #matrixFromFrame} builds exactly such a matrix from a
 * {@link HeadFrameMath} basis, so the tests verify {@code toYawPitchRoll}
 * inverts the shared halo convention and {@code composeHeadMatrix} reproduces
 * the {@code ModelPart} pipeline.</p>
 */
class RenderHeadMathTest {

    private static final double EPS = 1e-4;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static RenderHeadCapture.CapturedHead capture(
        Matrix4f root, float pivotX, float pivotY, float pivotZ,
        float pitchRad, float yawRad, float rollRad,
        float xScale, float yScale, float zScale
    ) {
        return new RenderHeadCapture.CapturedHead(
            new Matrix4f(root), pivotX, pivotY, pivotZ,
            pitchRad, yawRad, rollRad, xScale, yScale, zScale);
    }

    /**
     * Build the head cuboid matrix whose columns map head-local axes to world
     * axes the way the renderer's {@code scale(-1,-1,1)} flip does:
     * {@code +X → right}, {@code +Y → -headUp}, {@code +Z → -forward}.
     */
    private static Matrix4f matrixFromFrame(HeadFrameMath.HeadFrame frame, Vec3d cubeOrigin) {
        Matrix4f m = new Matrix4f();
        m.m00((float) frame.right().x); m.m01((float) frame.right().y); m.m02((float) frame.right().z); m.m03(0f);
        m.m10((float) -frame.headUp().x); m.m11((float) -frame.headUp().y); m.m12((float) -frame.headUp().z); m.m13(0f);
        m.m20((float) -frame.forward().x); m.m21((float) -frame.forward().y); m.m22((float) -frame.forward().z); m.m23(0f);
        m.m30((float) cubeOrigin.x); m.m31((float) cubeOrigin.y); m.m32((float) cubeOrigin.z); m.m33(1f);
        return m;
    }

    private static Matrix4f pipelineRoot(Vec3d entityPos, Vec3d cameraPos, float bodyYawDeg) {
        Matrix4f m = new Matrix4f();
        m.translate(
            (float) (entityPos.x - cameraPos.x),
            (float) (entityPos.y - cameraPos.y),
            (float) (entityPos.z - cameraPos.z));
        m.rotateY((float) Math.toRadians(180f - bodyYawDeg));
        m.scale(-1f, -1f, 1f);
        return m;
    }

    private static Vec3d transformDirection(Matrix4f m, float x, float y, float z) {
        Vector4f o = m.transform(new Vector4f(0f, 0f, 0f, 1f));
        Vector4f d = m.transform(new Vector4f(x, y, z, 1f));
        return new Vec3d(d.x - o.x, d.y - o.y, d.z - o.z).normalize();
    }

    private static Vec3d transformPoint(Matrix4f m, float x, float y, float z) {
        Vector4f p = m.transform(new Vector4f(x, y, z, 1f));
        return new Vec3d(p.x, p.y, p.z);
    }

    private static void assertVec(Vec3d expected, Vec3d actual, String label) {
        assertEquals(expected.x, actual.x, 1e-3, label + " x");
        assertEquals(expected.y, actual.y, 1e-3, label + " y");
        assertEquals(expected.z, actual.z, 1e-3, label + " z");
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("toYawPitchRoll inverts HeadFrameMath for arbitrary 6-DOF")
    class RoundTrip {

        @Test
        void reproducesAnglesAcrossYawPitchRoll() {
            for (float yaw : new float[]{-170f, -45f, 0f, 30f, 135f}) {
                for (float pitch : new float[]{-75f, -20f, 0f, 45f}) {
                    for (float roll : new float[]{-120f, -30f, 0f, 60f, 170f}) {
                        HeadFrameMath.HeadFrame frame = HeadFrameMath.of(yaw, pitch, roll);
                        Matrix4f m = matrixFromFrame(frame, new Vec3d(0, 1.5, 0));
                        float[] ypr = RenderHeadMath.toYawPitchRoll(m);
                        String ctx = "yaw=" + yaw + " pitch=" + pitch + " roll=" + roll;
                        assertEquals(yaw, ypr[0], 0.05, ctx + " yaw");
                        assertEquals(pitch, ypr[1], 0.05, ctx + " pitch");
                        assertEquals(roll, ypr[2], 0.05, ctx + " roll");
                    }
                }
            }
        }

        @Test
        void extractedBasisIsOrthonormalNearVerticalPitch() {
            HeadFrameMath.HeadFrame frame = HeadFrameMath.of(45f, 89.9f, 20f);
            Matrix4f m = matrixFromFrame(frame, new Vec3d(0, 1, 0));
            float[] ypr = RenderHeadMath.toYawPitchRoll(m);
            HeadFrameMath.HeadFrame out = HeadFrameMath.of(ypr[0], ypr[1], ypr[2]);
            assertEquals(1.0, out.forward().length(), EPS, "forward length");
            assertEquals(1.0, out.headUp().length(), EPS, "headUp length");
            assertEquals(0.0, out.forward().dotProduct(out.headUp()), EPS, "forward·headUp");
        }
    }

    @Nested
    @DisplayName("headCenter resolves the head box geometric centre")
    class HeadCenter {

        @Test
        void standingHeadBoxCenterIsQuarterBlockAboveCubeOrigin() {
            HeadFrameMath.HeadFrame frame = HeadFrameMath.of(0f, 0f, 0f);
            Vec3d cubeOrigin = new Vec3d(10, 1.5, -3);
            Matrix4f m = matrixFromFrame(frame, cubeOrigin);
            Vec3d cameraPos = new Vec3d(10, 1.5, -5);
            Vec3d center = RenderHeadMath.headCenter(m, cameraPos);
            assertEquals(cubeOrigin.x, center.x - cameraPos.x, EPS, "x");
            assertEquals(cubeOrigin.y + 0.25, center.y - cameraPos.y, EPS, "y");
            assertEquals(cubeOrigin.z, center.z - cameraPos.z, EPS, "z");
        }
    }

    @Nested
    @DisplayName("composeHeadMatrix reproduces the ModelPart pipeline")
    class Compose {

        @Test
        void matchesManualTranslateRotateScalePipeline() {
            Vec3d entityPos = new Vec3d(10, 64, -20);
            Vec3d cameraPos = new Vec3d(10, 64, -18);
            float bodyYawDeg = 37f;
            float headYawDeg = -12f;
            float headPitchDeg = 15f;
            float headRollDeg = 30f;
            float pivotX = 1.5f, pivotY = -3f, pivotZ = 2f;
            float hx = 1.2f, hy = 0.8f, hz = 1.0f;

            Matrix4f root = pipelineRoot(entityPos, cameraPos, bodyYawDeg);
            Matrix4f expected = new Matrix4f(root);
            expected.translate(pivotX / 16f, pivotY / 16f, pivotZ / 16f);
            expected.rotate(new Quaternionf().rotationZYX(
                (float) Math.toRadians(headRollDeg),
                (float) Math.toRadians(headYawDeg),
                (float) Math.toRadians(headPitchDeg)));
            expected.scale(hx, hy, hz);

            RenderHeadCapture.CapturedHead captured = capture(
                root, pivotX, pivotY, pivotZ,
                (float) Math.toRadians(headPitchDeg),
                (float) Math.toRadians(headYawDeg),
                (float) Math.toRadians(headRollDeg),
                hx, hy, hz);

            Matrix4f actual = RenderHeadMath.composeHeadMatrix(captured);
            float[] expectedArray = new float[16];
            float[] actualArray = new float[16];
            expected.get(expectedArray);
            actual.get(actualArray);
            for (int i = 0; i < 16; i++) {
                assertEquals(expectedArray[i], actualArray[i], 1e-3, "element " + i);
            }
        }

        @Test
        void fullAnchorRoundTripMatchesMatrixBasisAndCenter() {
            Vec3d entityPos = new Vec3d(10, 64, -20);
            Vec3d cameraPos = new Vec3d(10, 64, -18);
            float bodyYawDeg = 37f;
            float headYawDeg = -12f;
            float headPitchDeg = 15f;
            float headRollDeg = 30f;
            float pivotX = 0f, pivotY = 0f, pivotZ = 0f;

            Matrix4f root = pipelineRoot(entityPos, cameraPos, bodyYawDeg);
            RenderHeadCapture.CapturedHead captured = capture(
                root, pivotX, pivotY, pivotZ,
                (float) Math.toRadians(headPitchDeg),
                (float) Math.toRadians(headYawDeg),
                (float) Math.toRadians(headRollDeg),
                1f, 1f, 1f);

            // Identity view matrix → the capture is already world space.
            AnchorPose anchor = RenderHeadMath.toAnchorPose(captured, cameraPos, new Matrix4f());
            Matrix4f head = RenderHeadMath.composeHeadMatrix(captured);

            assertVec(transformDirection(head, 0f, 0f, -1f), rotate(anchor, 0, 0, 1), "forward");
            assertVec(transformDirection(head, 0f, -1f, 0f), rotate(anchor, 0, 1, 0), "headUp");

            Vec3d expectedCenter = cameraPos.add(transformPoint(head, 0f, -0.25f, 0f));
            assertVec(expectedCenter, vec(anchor.position()), "headCenter");
        }
    }

    @Nested
    @DisplayName("roll=0 keeps the rendered look direction horizontal")
    class Standing {

        @Test
        void forwardIsHorizontalAndPitchRollAreZero() {
            Vec3d entityPos = new Vec3d(0, 64, 0);
            Vec3d cameraPos = new Vec3d(5, 64, 3);
            for (float bodyYawDeg : new float[]{0f, 45f, 120f}) {
                for (float headYawDeg : new float[]{-30f, 0f, 25f}) {
                    Matrix4f root = pipelineRoot(entityPos, cameraPos, bodyYawDeg);
                    RenderHeadCapture.CapturedHead captured = capture(
                        root, 0f, 0f, 0f,
                        0f,
                        (float) Math.toRadians(headYawDeg),
                        0f,
                        1f, 1f, 1f);
                    AnchorPose anchor = RenderHeadMath.toAnchorPose(captured, cameraPos, new Matrix4f());
                    Vec3d forward = rotate(anchor, 0, 0, 1);
                    Vec3d up = rotate(anchor, 0, 1, 0);
                    String ctx = "body=" + bodyYawDeg + " head=" + headYawDeg;
                    assertEquals(0.0, forward.y, 1e-4, ctx + " forward.y");
                    assertEquals(1.0, up.y, 1e-4, ctx + " up.y");
                }
            }
        }
    }

    @Nested
    @DisplayName("Non-uniform head scale is corrected by Gram-Schmidt")
    class NonUniformScale {

        @Test
        void upStaysOrthogonalToForward() {
            Vec3d entityPos = new Vec3d(10, 64, -20);
            Vec3d cameraPos = new Vec3d(10, 64, -18);
            Matrix4f root = pipelineRoot(entityPos, cameraPos, 37f);
            RenderHeadCapture.CapturedHead captured = capture(
                root, 0f, 0f, 0f,
                (float) Math.toRadians(-25f),
                (float) Math.toRadians(40f),
                (float) Math.toRadians(60f),
                1.3f, 0.7f, 1.1f);

            Matrix4f head = RenderHeadMath.composeHeadMatrix(captured);
            float[] ypr = RenderHeadMath.toYawPitchRoll(head);
            HeadFrameMath.HeadFrame frame = HeadFrameMath.of(ypr[0], ypr[1], ypr[2]);

            assertVec(transformDirection(head, 0f, 0f, -1f), frame.forward(), "forward");
            assertEquals(0.0, frame.headUp().dotProduct(frame.forward()), 1e-3, "headUp·forward");
            assertEquals(1.0, frame.headUp().length(), 1e-3, "headUp length");
            assertEquals(1.0, frame.forward().length(), 1e-3, "forward length");
        }
    }

    @Nested
    @DisplayName("view-space captures are converted back to world space")
    class ViewSpaceConversion {

        @Test
        void viewSpaceAnchorEqualsWorldAnchor() {
            Vec3d entityPos = new Vec3d(10, 64, -20);
            Vec3d cameraPos = new Vec3d(10, 64, -18);
            float bodyYawDeg = 37f;
            float headYawDeg = -12f;
            float headPitchDeg = 15f;
            float headRollDeg = 30f;

            Matrix4f rootWorld = pipelineRoot(entityPos, cameraPos, bodyYawDeg);
            RenderHeadCapture.CapturedHead captured = capture(
                rootWorld, 0f, 0f, 0f,
                (float) Math.toRadians(headPitchDeg),
                (float) Math.toRadians(headYawDeg),
                (float) Math.toRadians(headRollDeg),
                1f, 1f, 1f);
            AnchorPose worldAnchor = RenderHeadMath.toAnchorPose(captured, cameraPos, new Matrix4f());

            // The entity render stack is the camera view matrix (rotation +
            // renderer flip) followed by the entity transform, so the captured
            // matrix is view × world.  Converting it back must reproduce the
            // world anchor for any camera orientation.
            for (float camPitch : new float[]{-40f, 0f, 25f}) {
                for (float camYaw : new float[]{-135f, 0f, 90f}) {
                    Matrix4f view = new Matrix4f();
                    view.rotateX((float) Math.toRadians(camPitch));
                    view.rotateY((float) Math.toRadians(camYaw + 180f));
                    view.scale(-1f, -1f, 1f);

                    Matrix4f rootView = new Matrix4f(view).mul(rootWorld);
                    RenderHeadCapture.CapturedHead viewCaptured = capture(
                        rootView, 0f, 0f, 0f,
                        (float) Math.toRadians(headPitchDeg),
                        (float) Math.toRadians(headYawDeg),
                        (float) Math.toRadians(headRollDeg),
                        1f, 1f, 1f);
                    AnchorPose viewAnchor = RenderHeadMath.toAnchorPose(viewCaptured, cameraPos, view);

                    String ctx = "camPitch=" + camPitch + " camYaw=" + camYaw;
                    assertVec(vec(worldAnchor.position()), vec(viewAnchor.position()), ctx + " headCenter");
                    assertVec(rotate(worldAnchor, 0, 0, 1), rotate(viewAnchor, 0, 0, 1), ctx + " forward");
                    assertVec(rotate(worldAnchor, 0, 1, 0), rotate(viewAnchor, 0, 1, 0), ctx + " up");
                }
            }
        }
    }

    private static Vec3d rotate(AnchorPose pose, double x, double y, double z) {
        return vec(AnchorPoseMath.rotate(pose.rotation(), new AnchorVec3(x, y, z)));
    }

    private static Vec3d vec(AnchorVec3 value) {
        return new Vec3d(value.x(), value.y(), value.z());
    }
}
