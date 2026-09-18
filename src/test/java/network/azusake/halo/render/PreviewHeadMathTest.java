package network.azusake.halo.render;

import network.azusake.halo.physics.RenderHeadCapture;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewHeadMathTest {
    @Test void pixelTranslationGuiScaleAndReflectionDoNotChangeBlockSizedHeadPose() {
        var entityRoot = new Matrix4f().rotateY(.6f).scale(-.9375f, -.9375f, .9375f).translate(0, -1.5f, 0);
        var local = new Matrix4f().translate(1f/16, 2f/16, -3f/16).rotateZYX(.2f, .4f, -.3f).scale(1.1f, .9f, 1.2f);
        var expectedHead = new Matrix4f(entityRoot).mul(local);
        Vector3f expectedCenter = expectedHead.transformPosition(new Vector3f(0, -.25f, 0));
        for (float scale : new float[]{1, 20, 30, 80}) {
            var gui = new Matrix4f().translate(713, 290, 50).scale(scale, scale, -scale).rotateZ((float) Math.PI).rotateX(.23f);
            var capture = new RenderHeadCapture.CapturedHead(new Matrix4f(gui).mul(entityRoot),
                1, 2, -3, -.3f, .4f, .2f, 1.1f, .9f, 1.2f);
            var rootBefore = new Matrix4f(gui);
            var result = PreviewHeadMath.toAnchor(gui, capture);
            assertNotNull(result);
            assertEquals(expectedCenter.x, result.position().x(), 1e-4);
            assertEquals(expectedCenter.y, result.position().y(), 1e-4);
            assertEquals(expectedCenter.z, result.position().z(), 1e-4);
            var q = result.rotation();
            var orientation = new org.joml.Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w());
            var expectedForward = expectedHead.transformDirection(new Vector3f(0, 0, -1)).normalize();
            var actualForward = orientation.transform(new Vector3f(0, 0, 1));
            assertEquals(expectedForward.x, actualForward.x, 1e-5);
            assertEquals(expectedForward.y, actualForward.y, 1e-5);
            assertEquals(expectedForward.z, actualForward.z, 1e-5);
            assertEquals(rootBefore, gui);
        }
    }

    @Test void singularOrNonFinitePreviewRootsAreRejectedWithoutInventingAnAnchor() {
        var capture = new RenderHeadCapture.CapturedHead(new Matrix4f(), 0, 0, 0, 0, 0, 0, 1, 1, 1);
        assertNull(PreviewHeadMath.toAnchor(new Matrix4f().scale(0), capture));
        assertNull(PreviewHeadMath.toAnchor(new Matrix4f().m00(Float.NaN), capture));
    }
}
