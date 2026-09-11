package network.azusake.halo.api.v2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnchorValueTest {

    @Test
    void rotationNormalizesFiniteQuaternion() {
        AnchorRotation rotation = new AnchorRotation(0.0, 0.0, 0.0, 2.0);
        assertEquals(1.0, rotation.w(), 1.0e-12);
        assertEquals(0.0, rotation.x(), 1.0e-12);
    }

    @Test
    void invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new AnchorVec3(Double.NaN, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
            () -> new AnchorRotation(0.0, 0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
            () -> new AnchorRotation(0.0, Double.POSITIVE_INFINITY, 0.0, 1.0));
        assertThrows(NullPointerException.class,
            () -> new AnchorPose(null, new AnchorRotation(0, 0, 0, 1)));
    }
}
