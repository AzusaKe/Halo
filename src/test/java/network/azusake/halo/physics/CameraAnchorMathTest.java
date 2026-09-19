package network.azusake.halo.physics;

import network.azusake.halo.anchor.AnchorPoseMath;
import network.azusake.halo.api.v2.AnchorVec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraAnchorMathTest {
    @Test void cameraBasisSurvivesVerticalPitchYawWrapAndRollWithoutEulerReconstruction() {
        for (float yaw : new float[]{-361, -180, -1, 0, 179, 360})
            for (float pitch : new float[]{-90, -89.999f, 0, 89.999f, 90})
                for (float roll : new float[]{-175, -45, 0, 65, 180}) {
                    var camera = new Quaternionf().rotationYXZ((float)Math.toRadians(yaw),
                        (float)Math.toRadians(pitch), (float)Math.toRadians(roll));
                    var before = new Quaternionf(camera);
                    var actual = CameraAnchorMath.rotation(camera);
                    assertVector(new Vector3f(0,1,0).rotate(camera), AnchorPoseMath.rotate(actual,new AnchorVec3(0,1,0)));
                    assertVector(new Vector3f(0,0,-1).rotate(camera), AnchorPoseMath.rotate(actual,new AnchorVec3(0,0,1)));
                    assertEquals(before, camera, "conversion must not mutate the engine camera");
                }
    }
    private static void assertVector(Vector3f expected, AnchorVec3 actual) {
        assertEquals(expected.x, actual.x(), 1e-6);
        assertEquals(expected.y, actual.y(), 1e-6);
        assertEquals(expected.z, actual.z(), 1e-6);
    }
}
