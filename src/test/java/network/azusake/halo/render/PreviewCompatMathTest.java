package network.azusake.halo.render;

import net.minecraft.world.phys.Vec3;
import network.azusake.halo.anchor.AnchorPoseMath;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorVec3;
import network.azusake.halo.compat.emf.EmfHeadCapture;
import network.azusake.halo.compat.emf.EmfHeadMath;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewCompatMathTest {
    @Test void removesGuiPixelsRotationAndReflectionBeforeEmfModelConversion() {
        Matrix4f model = new Matrix4f().translate(.4f, 1.8f, -.2f).rotateYXZ(.8f, -.5f, .3f).scale(1.2f, .8f, 1.1f);
        AnchorPose expectedEmf = EmfHeadMath.toAnchorPose(new EmfHeadCapture.CapturedHead(model, new Matrix4f(), Vec3.ZERO));
        for (float scale : new float[]{20, 30, 90}) {
            for (float reflection : new float[]{-1, 1}) {
                Matrix4f gui = new Matrix4f().translate(320, 180, 1050).scale(scale, -scale, scale * reflection)
                    .rotateZYX(3.14f, -.3f, .6f);
                Matrix4f captured = new Matrix4f(gui).mul(model);
                assertPose(expectedEmf, EmfHeadMath.toAnchorPose(new EmfHeadCapture.CapturedHead(captured, gui, Vec3.ZERO)));
            }
        }
    }

    @Test void singularOrNonFiniteGuiRootsCannotProduceAnAnchor() {
        for (Matrix4f root : new Matrix4f[]{new Matrix4f().scale(0), new Matrix4f().m00(Float.NaN)}) {
            assertNull(EmfHeadMath.toAnchorPose(new EmfHeadCapture.CapturedHead(new Matrix4f(), root, Vec3.ZERO)));
        }
    }

    private static void assertPose(AnchorPose expected, AnchorPose actual) {
        assertNotNull(actual);
        assertEquals(expected.position().x(), actual.position().x(), 1e-4);
        assertEquals(expected.position().y(), actual.position().y(), 1e-4);
        assertEquals(expected.position().z(), actual.position().z(), 1e-4);
        for (AnchorVec3 axis : new AnchorVec3[]{new AnchorVec3(0,1,0), new AnchorVec3(0,0,1)}) {
            AnchorVec3 a = AnchorPoseMath.rotate(expected.rotation(), axis), b = AnchorPoseMath.rotate(actual.rotation(), axis);
            assertEquals(a.x(), b.x(), 1e-4);
            assertEquals(a.y(), b.y(), 1e-4);
            assertEquals(a.z(), b.z(), 1e-4);
        }
    }
}
