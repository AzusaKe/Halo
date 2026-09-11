package network.azusake.halo.compat.ysm;

import net.minecraft.world.phys.Vec3;
import network.azusake.halo.anchor.AnchorPoseMath;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorVec3;
import network.azusake.halo.physics.HeadFrameMath;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YsmCompatTest {
    private static final double EPS = 1.0e-3;

    @Test
    void exactVersionGateUsesJarMetadataVersion() {
        assertTrue(YsmVersionGate.isSupportedVersion("2.6.5-neoforge+mc26.1"));
        assertFalse(YsmVersionGate.isSupportedVersion("2.6.5-neoforge+mc26.1-hotfix"));
        assertFalse(YsmVersionGate.isSupportedVersion("2.6.5"));
        assertFalse(YsmVersionGate.isSupportedVersion(null));
    }

    @Test
    void multiBonePivotZyxRotationAndNonUniformScale() {
        Matrix4f root = new Matrix4f().translate(2, 3, -4).rotateY(0.35f);
        var parent = bone(0.1f, -0.2f, 0.3f, 2, 4, -3, 1.2f, 0.8f, 1.1f, 1, 12, -2);
        var head = bone(-0.25f, 0.4f, -0.15f, -1, 2, 3, 0.9f, 1.3f, 1.05f, 0, 4, 1);
        Matrix4f expected = new Matrix4f(root);
        apply(expected, parent, true);
        apply(expected, head, false);
        assertMatrix(expected, YsmV265Adapter.composeHeadMatrix(root, List.of(parent, head)));
    }

    @Test
    void rejectsZeroScaleAndNonFiniteValues() {
        assertNull(YsmV265Adapter.composeHeadMatrix(new Matrix4f(), List.of(
            bone(0, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0))));
        assertNull(YsmV265Adapter.composeHeadMatrix(new Matrix4f(), List.of(
            bone(Float.NaN, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0))));
        assertNull(YsmHeadMath.toAnchorPose(new Matrix4f().m00(Float.NaN),
            Vec3.ZERO, Vec3.ZERO, new Matrix4f()));
    }

    @Test
    void localOffsetViewRestoreAndYawPitchRollSigns() {
        float yaw = 35, pitch = -20, roll = 42;
        HeadFrameMath.HeadFrame frame = HeadFrameMath.of(yaw, pitch, roll);
        Matrix4f world = matrixFromFrame(frame, new Vec3(2, 3, 4));
        Matrix4f view = new Matrix4f().rotate(new Quaternionf().rotationYXZ(0.4f, -0.2f, 0.1f));
        Matrix4f captured = new Matrix4f(view).mul(world);
        Vec3 offset = new Vec3(0.2, 0.3, -0.1);
        Vec3 camera = new Vec3(10, 60, -5);

        AnchorPose anchor = YsmHeadMath.toAnchorPose(captured, offset, camera, view);
        assertNotNull(anchor);
        Vector4f expected = world.transform(new Vector4f(0.2f, 0.3f, -0.1f, 1));
        assertEquals(camera.x + expected.x, anchor.position().x(), EPS);
        assertEquals(camera.y + expected.y, anchor.position().y(), EPS);
        assertEquals(camera.z + expected.z, anchor.position().z(), EPS);
        AnchorVec3 actualForward = AnchorPoseMath.rotate(anchor.rotation(), new AnchorVec3(0, 0, 1));
        AnchorVec3 actualUp = AnchorPoseMath.rotate(anchor.rotation(), new AnchorVec3(0, 1, 0));
        assertVector(frame.forward(), actualForward);
        assertVector(frame.headUp(), actualUp);
    }

    private static YsmV265Adapter.BonePose bone(
        float rx, float ry, float rz, float x, float y, float z,
        float sx, float sy, float sz, float px, float py, float pz
    ) {
        return new YsmV265Adapter.BonePose(rx, ry, rz, x, y, z, sx, sy, sz, px, py, pz);
    }

    private static void apply(Matrix4f matrix, YsmV265Adapter.BonePose b, boolean away) {
        matrix.translate(-b.positionX() / 16, b.positionY() / 16, b.positionZ() / 16)
            .translate(b.pivotX() / 16, b.pivotY() / 16, b.pivotZ() / 16)
            .rotate(new Quaternionf().rotationZYX(b.rotationZ(), b.rotationY(), b.rotationX()))
            .scale(b.scaleX(), b.scaleY(), b.scaleZ());
        if (away) matrix.translate(-b.pivotX() / 16, -b.pivotY() / 16, -b.pivotZ() / 16);
    }

    private static void assertVector(Vec3 expected, AnchorVec3 actual) {
        assertEquals(expected.x, actual.x(), EPS);
        assertEquals(expected.y, actual.y(), EPS);
        assertEquals(expected.z, actual.z(), EPS);
    }

    private static Matrix4f matrixFromFrame(HeadFrameMath.HeadFrame frame, Vec3 origin) {
        Matrix4f m = new Matrix4f();
        m.m00((float) frame.right().x).m01((float) frame.right().y).m02((float) frame.right().z);
        m.m10((float) frame.headUp().x).m11((float) frame.headUp().y).m12((float) frame.headUp().z);
        m.m20((float) -frame.forward().x).m21((float) -frame.forward().y).m22((float) -frame.forward().z);
        m.m30((float) origin.x).m31((float) origin.y).m32((float) origin.z);
        return m;
    }

    private static void assertMatrix(Matrix4f expected, Matrix4f actual) {
        assertNotNull(actual);
        float[] a = new float[16], b = new float[16];
        expected.get(a); actual.get(b);
        for (int i = 0; i < 16; i++) assertEquals(a[i], b[i], 1.0e-5, "element " + i);
    }
}
