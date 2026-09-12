package network.azusake.halo.compat.ysm;

import network.azusake.halo.anchor.AnchorPoseMath;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorVec3;
import network.azusake.halo.physics.HeadFrameMath;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YsmCompatTest {

    private static final double EPS = 1.0e-3;

    @AfterEach
    void clearCapture() {
        YsmHeadCapture.clearForTests();
    }

    @Test
    @DisplayName("version gate accepts only the verified YSM release")
    void exactVersionGate() {
        assertTrue(YsmVersionGate.isSupportedVersion("2.6.5-fabric+mc1.20.1"));
        assertFalse(YsmVersionGate.isSupportedVersion("2.6.5"));
        assertFalse(YsmVersionGate.isSupportedVersion("2.6.6-fabric+mc1.20.1"));
        assertFalse(YsmVersionGate.isSupportedVersion(null));
    }

    @Test
    @DisplayName("multi-bone locator composition matches YSM prepMatrixForLocator")
    void multiBoneComposition() {
        Matrix4f root = new Matrix4f().translate(2f, 3f, -4f).rotateY(0.35f);
        YsmV265Adapter.BonePose parent = bone(
            0.1f, -0.2f, 0.3f,
            2f, 4f, -3f,
            1.2f, 0.8f, 1.1f,
            1f, 12f, -2f);
        YsmV265Adapter.BonePose head = bone(
            -0.25f, 0.4f, -0.15f,
            -1f, 2f, 3f,
            0.9f, 1.3f, 1.05f,
            0f, 4f, 1f);

        Matrix4f expected = new Matrix4f(root);
        apply(expected, parent, true);
        apply(expected, head, false);

        Matrix4f actual = YsmV265Adapter.composeHeadMatrix(root, List.of(parent, head));
        assertMatrix(expected, actual);
    }

    @Test
    @DisplayName("zero scale and non-finite bones are rejected")
    void invalidBoneRejected() {
        assertNull(YsmV265Adapter.composeHeadMatrix(new Matrix4f(), List.of(bone(
            0, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0))));
        assertNull(YsmV265Adapter.composeHeadMatrix(new Matrix4f(), List.of(bone(
            Float.NaN, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0))));

        Matrix4f nonFiniteHead = new Matrix4f().m00(Float.NaN);
        assertNull(YsmHeadMath.toAnchorPose(
            nonFiniteHead, Vec3d.ZERO, Vec3d.ZERO, new Matrix4f()));
        assertNull(YsmHeadMath.toAnchorPose(
            new Matrix4f(), Vec3d.ZERO, Vec3d.ZERO, new Matrix4f().scale(0f)));
    }

    @Test
    @DisplayName("Head-local offset and Y-up orientation convert to a 6-DOF anchor")
    void headLocalOffsetAndOrientation() {
        float yaw = 35f;
        float pitch = -20f;
        float roll = 42f;
        HeadFrameMath.HeadFrame frame = HeadFrameMath.of(yaw, pitch, roll);
        Matrix4f matrix = matrixFromFrame(frame, new Vec3d(2, 3, 4));
        Vec3d localOffset = new Vec3d(0.2, 0.3, -0.1);
        Vec3d camera = new Vec3d(10, 60, -5);

        AnchorPose anchor = YsmHeadMath.toAnchorPose(matrix, localOffset, camera, new Matrix4f());

        assertNotNull(anchor);
        Vector4f expectedLocal = matrix.transform(new Vector4f(0.2f, 0.3f, -0.1f, 1f));
        assertEquals(camera.x + expectedLocal.x, anchor.position().x(), EPS);
        assertEquals(camera.y + expectedLocal.y, anchor.position().y(), EPS);
        assertEquals(camera.z + expectedLocal.z, anchor.position().z(), EPS);
        assertDirection(frame.forward(), anchor, new AnchorVec3(0, 0, 1));
        assertDirection(frame.headUp(), anchor, new AnchorVec3(0, 1, 0));
    }

    @Test
    @DisplayName("view-space Head matrix is restored to world orientation")
    void viewSpaceRestoration() {
        HeadFrameMath.HeadFrame frame = HeadFrameMath.of(-70f, 25f, -30f);
        Matrix4f world = matrixFromFrame(frame, new Vec3d(3, 2, -1));
        Matrix4f view = new Matrix4f().rotate(new Quaternionf().rotationYXZ(0.4f, -0.2f, 0.1f));
        Matrix4f viewSpace = new Matrix4f(view).mul(world);

        AnchorPose anchor = YsmHeadMath.toAnchorPose(viewSpace, Vec3d.ZERO, Vec3d.ZERO, view);

        assertNotNull(anchor);
        assertDirection(frame.forward(), anchor, new AnchorVec3(0, 0, 1));
        assertDirection(frame.headUp(), anchor, new AnchorVec3(0, 1, 0));
    }

    @Test
    @DisplayName("YSM captures retain only the immediately previous frame")
    void oneFrameRetention() {
        UUID uuid = UUID.randomUUID();
        Matrix4f matrix = new Matrix4f().translate(1f, 2f, 3f);

        YsmHeadCapture.recordForTests(uuid, matrix);
        assertNotNull(YsmHeadCapture.getCurrent(uuid));
        assertNull(YsmHeadCapture.getPrevious(uuid));

        YsmHeadCapture.advanceFrameForTests();
        assertNull(YsmHeadCapture.getCurrent(uuid));
        assertNotNull(YsmHeadCapture.getPrevious(uuid));

        YsmHeadCapture.advanceFrameForTests();
        assertNull(YsmHeadCapture.getCurrent(uuid));
        assertNull(YsmHeadCapture.getPrevious(uuid));
    }

    @Test
    @DisplayName("previous-frame capture is restored with its producing camera transform")
    void previousCaptureKeepsCameraFrame() {
        UUID uuid = UUID.randomUUID();
        Vec3d expectedWorld = new Vec3d(13, 4, -8);
        Vec3d captureCamera = new Vec3d(10, 2, -5);
        Matrix4f captureView = new Matrix4f().rotateY(0.65f).rotateX(-0.2f);
        Matrix4f cameraRelativeWorld = new Matrix4f().translate(
            (float) (expectedWorld.x - captureCamera.x),
            (float) (expectedWorld.y - captureCamera.y),
            (float) (expectedWorld.z - captureCamera.z));
        Matrix4f capturedHead = new Matrix4f(captureView).mul(cameraRelativeWorld);

        YsmHeadCapture.recordForTests(
            uuid, capturedHead, captureView, captureCamera);
        YsmHeadCapture.advanceFrameForTests();

        YsmHeadCapture.CapturedHead previous = YsmHeadCapture.getPrevious(uuid);
        AnchorPose anchor = YsmHeadMath.toAnchorPose(previous, Vec3d.ZERO);
        assertNotNull(anchor);
        assertEquals(expectedWorld.x, anchor.position().x(), EPS);
        assertEquals(expectedWorld.y, anchor.position().y(), EPS);
        assertEquals(expectedWorld.z, anchor.position().z(), EPS);
    }

    private static void assertDirection(network.azusake.halo.core.Vec3d expected, AnchorPose pose, AnchorVec3 local) {
        AnchorVec3 actual = AnchorPoseMath.rotate(pose.rotation(), local);
        assertEquals(expected.x, actual.x(), EPS);
        assertEquals(expected.y, actual.y(), EPS);
        assertEquals(expected.z, actual.z(), EPS);
    }

    @Test
    @DisplayName("ineligible first-person captures can be discarded before a perspective switch")
    void captureDiscard() {
        UUID uuid = UUID.randomUUID();
        YsmHeadCapture.recordForTests(uuid, new Matrix4f());
        YsmHeadCapture.advanceFrameForTests();

        YsmHeadCapture.discard(uuid);

        assertNull(YsmHeadCapture.getCurrent(uuid));
        assertNull(YsmHeadCapture.getPrevious(uuid));
    }

    private static YsmV265Adapter.BonePose bone(
        float rx, float ry, float rz,
        float px, float py, float pz,
        float sx, float sy, float sz,
        float pivotX, float pivotY, float pivotZ
    ) {
        return new YsmV265Adapter.BonePose(
            rx, ry, rz, px, py, pz, sx, sy, sz, pivotX, pivotY, pivotZ);
    }

    private static void apply(Matrix4f matrix, YsmV265Adapter.BonePose bone, boolean translateAway) {
        matrix.translate(-bone.positionX() / 16f, bone.positionY() / 16f, bone.positionZ() / 16f);
        matrix.translate(bone.pivotX() / 16f, bone.pivotY() / 16f, bone.pivotZ() / 16f);
        matrix.rotate(new Quaternionf().rotationZYX(
            bone.rotationZ(), bone.rotationY(), bone.rotationX()));
        matrix.scale(bone.scaleX(), bone.scaleY(), bone.scaleZ());
        if (translateAway) {
            matrix.translate(-bone.pivotX() / 16f, -bone.pivotY() / 16f, -bone.pivotZ() / 16f);
        }
    }

    private static Matrix4f matrixFromFrame(HeadFrameMath.HeadFrame frame, Vec3d origin) {
        Matrix4f matrix = new Matrix4f();
        matrix.m00((float) frame.right().x);
        matrix.m01((float) frame.right().y);
        matrix.m02((float) frame.right().z);
        matrix.m10((float) frame.headUp().x);
        matrix.m11((float) frame.headUp().y);
        matrix.m12((float) frame.headUp().z);
        matrix.m20((float) -frame.forward().x);
        matrix.m21((float) -frame.forward().y);
        matrix.m22((float) -frame.forward().z);
        matrix.m30((float) origin.x);
        matrix.m31((float) origin.y);
        matrix.m32((float) origin.z);
        return matrix;
    }

    private static void assertMatrix(Matrix4f expected, Matrix4f actual) {
        assertNotNull(actual);
        float[] expectedValues = new float[16];
        float[] actualValues = new float[16];
        expected.get(expectedValues);
        actual.get(actualValues);
        for (int i = 0; i < 16; i++) {
            assertEquals(expectedValues[i], actualValues[i], 1.0e-5, "matrix element " + i);
        }
    }
}
