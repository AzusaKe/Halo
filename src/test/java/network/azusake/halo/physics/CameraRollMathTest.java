package network.azusake.halo.physics;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraRollMathTest {
    private static Quaternionf cameraRotation(float yawDeg, float pitchDeg, float rollDeg) {
        return new Quaternionf()
            .rotateY((float) Math.PI - (float) Math.toRadians(yawDeg))
            .rotateX(-(float) Math.toRadians(pitchDeg))
            .rotateZ((float) Math.toRadians(rollDeg));
    }

    @Test void vanillaCameraRotationNeverCreatesPseudoRollWhileLookingAround() {
        for (float yaw : new float[]{0f, 30f, 90f, 180f, -120f}) {
            for (float pitch : new float[]{-60f, -30f, 0f, 30f, 60f}) {
                assertEquals(0, CameraRollMath.recoverRollDeg(
                    yaw, pitch, cameraRotation(yaw, pitch, 0)), 1e-3,
                    "roll for yaw=" + yaw + " pitch=" + pitch);
            }
        }
    }

    @Test void cameraModRollIsRecoveredIndependentlyOfYawAndPitch() {
        for (float yaw : new float[]{-170f, 15f, 90f}) {
            for (float pitch : new float[]{-45f, 0f, 55f}) {
                for (float roll : new float[]{-120f, -45f, 0f, 30f, 170f}) {
                    assertEquals(roll, CameraRollMath.recoverRollDeg(
                        yaw, pitch, cameraRotation(yaw, pitch, roll)), 1e-2,
                        "yaw=" + yaw + " pitch=" + pitch + " roll=" + roll);
                }
            }
        }
    }
}
