package network.azusake.halo.compat.emf;

import network.azusake.halo.api.HeadAnchor;
import network.azusake.halo.physics.HeadFrameMath;
import net.minecraft.world.phys.Vec3;
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
    @DisplayName("1.21.1 NeoForge EMF render ABI uses the packed-colour render overload")
    void renderAbiSignature() {
        assertEquals("render", Emf1211Symbols.RENDER_METHOD);
        assertEquals(
            "(Lcom/mojang/blaze3d/vertex/PoseStack;"
                + "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            Emf1211Symbols.RENDER_DESCRIPTOR);
    }

    @Test
    @DisplayName("EMF captures retain only the immediately previous frame")
    void oneFrameRetention() {
        UUID uuid = UUID.randomUUID();
        EmfHeadCapture.recordForTests(uuid, new Matrix4f(), new Matrix4f(), Vec3.ZERO);
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
        Vec3 expectedWorld = new Vec3(13, 4, -8);
        Vec3 captureCamera = new Vec3(10, 2, -5);
        Matrix4f captureView = new Matrix4f().rotateY(0.65f).rotateX(-0.2f);
        Matrix4f cameraRelativeWorld = new Matrix4f().translate(
            (float) (expectedWorld.x - captureCamera.x),
            (float) (expectedWorld.y - captureCamera.y),
            (float) (expectedWorld.z - captureCamera.z));
        Matrix4f capturedHead = new Matrix4f(captureView).mul(cameraRelativeWorld);

        EmfHeadCapture.recordForTests(uuid, capturedHead, captureView, captureCamera);
        EmfHeadCapture.advanceFrameForTests();

        HeadAnchor anchor = EmfHeadMath.toHeadAnchor(EmfHeadCapture.getPrevious(uuid));
        assertNotNull(anchor);
        assertEquals(expectedWorld.x, anchor.headCenter().x, EPS);
        assertEquals(expectedWorld.y - 0.25, anchor.headCenter().y, EPS);
        assertEquals(expectedWorld.z, anchor.headCenter().z, EPS);
    }

    @Test
    @DisplayName("EMF head orientation and centre use the shared vanilla convention")
    void headOrientationAndCentre() {
        float yaw = 35f;
        float pitch = -20f;
        float roll = 42f;
        HeadFrameMath.HeadFrame frame = HeadFrameMath.of(yaw, pitch, roll);
        Vec3 cubeOrigin = new Vec3(2, 3, 4);
        Matrix4f matrix = matrixFromFrame(frame, cubeOrigin);

        HeadAnchor anchor = EmfHeadMath.toHeadAnchor(new EmfHeadCapture.CapturedHead(
            matrix, new Matrix4f(), Vec3.ZERO));

        assertNotNull(anchor);
        assertEquals(yaw, anchor.yaw(), 0.05);
        assertEquals(pitch, anchor.pitch(), 0.05);
        assertEquals(roll, anchor.roll(), 0.05);
        Vec3 expectedCenter = cubeOrigin.add(frame.headUp().scale(0.25));
        assertEquals(expectedCenter.x, anchor.headCenter().x, EPS);
        assertEquals(expectedCenter.y, anchor.headCenter().y, EPS);
        assertEquals(expectedCenter.z, anchor.headCenter().z, EPS);
    }

    @Test
    @DisplayName("non-finite EMF matrices fall back instead of poisoning the anchor")
    void nonFiniteMatrixRejected() {
        Matrix4f invalid = new Matrix4f().m00(Float.NaN);
        assertNull(EmfHeadMath.toHeadAnchor(new EmfHeadCapture.CapturedHead(
            invalid, new Matrix4f(), Vec3.ZERO)));
        assertNull(EmfHeadMath.toHeadAnchor(new EmfHeadCapture.CapturedHead(
            new Matrix4f(), new Matrix4f().scale(0f), Vec3.ZERO)));
    }

    @Test
    @DisplayName("view-space EMF matrices are restored before extracting orientation")
    void viewSpaceRestoration() {
        HeadFrameMath.HeadFrame frame = HeadFrameMath.of(-70f, 25f, -30f);
        Matrix4f world = matrixFromFrame(frame, new Vec3(3, 2, -1));
        Matrix4f view = new Matrix4f().rotate(new Quaternionf().rotationYXZ(0.4f, -0.2f, 0.1f));
        Matrix4f viewSpace = new Matrix4f(view).mul(world);

        HeadAnchor anchor = EmfHeadMath.toHeadAnchor(new EmfHeadCapture.CapturedHead(
            viewSpace, view, Vec3.ZERO));

        assertNotNull(anchor);
        assertEquals(-70f, anchor.yaw(), 0.05);
        assertEquals(25f, anchor.pitch(), 0.05);
        assertEquals(-30f, anchor.roll(), 0.05);
    }

    private static Matrix4f matrixFromFrame(HeadFrameMath.HeadFrame frame, Vec3 origin) {
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
