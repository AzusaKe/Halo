package network.azusake.halo.compat.emf;

import network.azusake.halo.anchor.AnchorPoseMath;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorVec3;
import network.azusake.halo.physics.HeadFrameMath;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmfCompatTest {

    private static final double EPS = 1.0e-3;

    @AfterEach
    void clearCapture() {
        EmfHeadCapture.clearForTests();
    }

    @Test
    @DisplayName("version gate uses 3.1.1 as a lower bound without an upper bound")
    void lowerBoundVersionGate() {
        assertTrue(EmfVersionGate.isSupportedVersion("3.1.1"));
        assertTrue(EmfVersionGate.isSupportedVersion("3.2.4"));
        assertTrue(EmfVersionGate.isSupportedVersion("3.3.5"));
        assertTrue(EmfVersionGate.isSupportedVersion("3.3.6"));
        assertTrue(EmfVersionGate.isSupportedVersion("4.0.0"));
        assertTrue(EmfVersionGate.isSupportedVersion("3.1.1+mc1.21.1"));
        assertFalse(EmfVersionGate.isSupportedVersion("3.1.0"));
        assertFalse(EmfVersionGate.isSupportedVersion("3.0.17"));
        assertFalse(EmfVersionGate.isSupportedVersion(null));
    }

    @Test
    @DisplayName("1.21.1 EMF render ABI uses the packed-colour render overload")
    void renderAbiSignature() {
        assertEquals("method_22699", Emf1211Symbols.RENDER_METHOD_INTERMEDIARY);
        assertEquals(
            "(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;III)V",
            Emf1211Symbols.RENDER_DESCRIPTOR_INTERMEDIARY);
    }

    @Test
    @DisplayName("EMF captures retain only the immediately previous frame")
    void oneFrameRetention() {
        UUID uuid = UUID.randomUUID();
        EmfHeadCapture.recordForTests(uuid, new Matrix4f(), new Matrix4f(), Vec3d.ZERO);
        assertNotNull(EmfHeadCapture.getCurrent(uuid));
        assertNull(EmfHeadCapture.getPrevious(uuid));

        EmfHeadCapture.advanceFrameForTests();
        assertNull(EmfHeadCapture.getCurrent(uuid));
        assertNotNull(EmfHeadCapture.getPrevious(uuid));

        EmfHeadCapture.advanceFrameForTests();
        assertNull(EmfHeadCapture.getCurrent(uuid));
        assertNull(EmfHeadCapture.getPrevious(uuid));
    }

    @Test
    @DisplayName("previous EMF captures retain their producing camera frame")
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

        EmfHeadCapture.recordForTests(uuid, capturedHead, captureView, captureCamera);
        EmfHeadCapture.advanceFrameForTests();

        AnchorPose anchor = EmfHeadMath.toAnchorPose(EmfHeadCapture.getPrevious(uuid));
        assertNotNull(anchor);
        assertEquals(expectedWorld.x, anchor.position().x(), EPS);
        assertEquals(expectedWorld.y - 0.25, anchor.position().y(), EPS);
        assertEquals(expectedWorld.z, anchor.position().z(), EPS);
    }

    @Test
    @DisplayName("EMF head orientation and centre use the shared vanilla convention")
    void headOrientationAndCentre() {
        float yaw = 35f;
        float pitch = -20f;
        float roll = 42f;
        HeadFrameMath.HeadFrame frame = HeadFrameMath.of(yaw, pitch, roll);
        Vec3d cubeOrigin = new Vec3d(2, 3, 4);
        Matrix4f matrix = matrixFromFrame(frame, cubeOrigin);

        AnchorPose anchor = EmfHeadMath.toAnchorPose(new EmfHeadCapture.CapturedHead(
            matrix, new Matrix4f(), Vec3d.ZERO));

        assertNotNull(anchor);
        assertDirection(frame.forward(), anchor, new AnchorVec3(0, 0, 1));
        assertDirection(frame.headUp(), anchor, new AnchorVec3(0, 1, 0));
        Vec3d expectedCenter = cubeOrigin.add(frame.headUp().multiply(0.25));
        assertEquals(expectedCenter.x, anchor.position().x(), EPS);
        assertEquals(expectedCenter.y, anchor.position().y(), EPS);
        assertEquals(expectedCenter.z, anchor.position().z(), EPS);
    }

    @Test
    @DisplayName("non-finite EMF matrices fall back instead of poisoning the anchor")
    void nonFiniteMatrixRejected() {
        Matrix4f invalid = new Matrix4f().m00(Float.NaN);
        assertNull(EmfHeadMath.toAnchorPose(new EmfHeadCapture.CapturedHead(
            invalid, new Matrix4f(), Vec3d.ZERO)));
        assertNull(EmfHeadMath.toAnchorPose(new EmfHeadCapture.CapturedHead(
            new Matrix4f(), new Matrix4f().scale(0f), Vec3d.ZERO)));
    }

    @Test
    @DisplayName("view-space EMF matrices are restored before extracting orientation")
    void viewSpaceRestoration() {
        HeadFrameMath.HeadFrame frame = HeadFrameMath.of(-70f, 25f, -30f);
        Matrix4f world = matrixFromFrame(frame, new Vec3d(3, 2, -1));
        Matrix4f view = new Matrix4f().rotate(new Quaternionf().rotationYXZ(0.4f, -0.2f, 0.1f));
        Matrix4f viewSpace = new Matrix4f(view).mul(world);

        AnchorPose anchor = EmfHeadMath.toAnchorPose(new EmfHeadCapture.CapturedHead(
            viewSpace, view, Vec3d.ZERO));

        assertNotNull(anchor);
        assertDirection(frame.forward(), anchor, new AnchorVec3(0, 0, 1));
        assertDirection(frame.headUp(), anchor, new AnchorVec3(0, 1, 0));
    }

    private static void assertDirection(Vec3d expected, AnchorPose pose, AnchorVec3 local) {
        AnchorVec3 actual = AnchorPoseMath.rotate(pose.rotation(), local);
        assertEquals(expected.x, actual.x(), EPS);
        assertEquals(expected.y, actual.y(), EPS);
        assertEquals(expected.z, actual.z(), EPS);
    }

    private static Matrix4f matrixFromFrame(HeadFrameMath.HeadFrame frame, Vec3d origin) {
        Matrix4f matrix = new Matrix4f();
        matrix.m00((float) frame.right().x);
        matrix.m01((float) frame.right().y);
        matrix.m02((float) frame.right().z);
        matrix.m10((float) -frame.headUp().x);
        matrix.m11((float) -frame.headUp().y);
        matrix.m12((float) -frame.headUp().z);
        matrix.m20((float) -frame.forward().x);
        matrix.m21((float) -frame.forward().y);
        matrix.m22((float) -frame.forward().z);
        matrix.m30((float) origin.x);
        matrix.m31((float) origin.y);
        matrix.m32((float) origin.z);
        return matrix;
    }
}
