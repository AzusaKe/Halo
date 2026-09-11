package network.azusake.halo.physics;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link HeadFrameMath} — the shared 6DOF head-basis math.
 *
 * <p>Verifies: roll=0 reproduces the legacy basis exactly, the basis is
 * orthonormal for arbitrary angles, and roll rotates right/headUp around
 * forward with the Minecraft camera convention.</p>
 */
class HeadFrameMathTest {

    private static final double EPS = 1e-4;

    // ---- legacy (pre-6DOF) basis re-implementation ----
    private static Vec3 legacyForward(float yawDeg, float pitchDeg) {
        float yawRad = (float) Math.toRadians(yawDeg);
        float pitchRad = (float) Math.toRadians(pitchDeg);
        return new Vec3(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        ).normalize();
    }

    private static Vec3 legacyRight(float yawDeg, float pitchDeg) {
        float yawRad = (float) Math.toRadians(yawDeg);
        Vec3 forward = legacyForward(yawDeg, pitchDeg);
        Vec3 worldUp = new Vec3(0, 1, 0);
        if (Math.abs(forward.dot(worldUp)) > 0.999) {
            return new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad));
        }
        return forward.cross(worldUp).normalize();
    }

    private static Vec3 legacyHeadUp(float yawDeg, float pitchDeg) {
        return legacyRight(yawDeg, pitchDeg).cross(legacyForward(yawDeg, pitchDeg)).normalize();
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPS, "x");
        assertEquals(expected.y, actual.y, EPS, "y");
        assertEquals(expected.z, actual.z, EPS, "z");
    }

    @Nested
    @DisplayName("roll=0 reproduces the legacy basis")
    class RollZeroRegression {

        @Test
        void matchesLegacyAcrossCommonAngles() {
            for (float yaw : new float[]{0f, 30f, 90f, 180f, -90f}) {
                for (float pitch : new float[]{-60f, -30f, 0f, 30f, 60f}) {
                    HeadFrameMath.HeadFrame frame = HeadFrameMath.of(yaw, pitch, 0f);
                    assertVec(legacyForward(yaw, pitch), frame.forward());
                    assertVec(legacyRight(yaw, pitch), frame.right());
                    assertVec(legacyHeadUp(yaw, pitch), frame.headUp());
                }
            }
        }

        @Test
        void matchesLegacyNearVerticalPitch() {
            HeadFrameMath.HeadFrame frame = HeadFrameMath.of(45f, 89.9f, 0f);
            assertVec(legacyRight(45f, 89.9f), frame.right());
            assertVec(legacyHeadUp(45f, 89.9f), frame.headUp());
            assertVec(legacyForward(45f, 89.9f), frame.forward());
        }
    }

    @Nested
    @DisplayName("Orthonormality for arbitrary yaw/pitch/roll")
    class Orthonormality {

        @Test
        void basisIsOrthonormal() {
            for (float yaw : new float[]{-180f, -45f, 10f, 135f}) {
                for (float pitch : new float[]{-80f, -20f, 25f, 80f}) {
                    for (float roll : new float[]{-90f, -30f, 0f, 45f, 120f}) {
                        HeadFrameMath.HeadFrame frame = HeadFrameMath.of(yaw, pitch, roll);
                        assertEquals(1.0, frame.right().length(), EPS, "right length");
                        assertEquals(1.0, frame.headUp().length(), EPS, "headUp length");
                        assertEquals(1.0, frame.forward().length(), EPS, "forward length");
                        assertEquals(0.0, frame.right().dot(frame.headUp()), EPS, "right·headUp");
                        assertEquals(0.0, frame.right().dot(frame.forward()), EPS, "right·forward");
                        assertEquals(0.0, frame.headUp().dot(frame.forward()), EPS, "headUp·forward");
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("Roll rotates right/headUp around forward (camera convention)")
    class RollRotation {

        @Test
        void roll90AtYaw0Pitch0() {
            HeadFrameMath.HeadFrame frame = HeadFrameMath.of(0f, 0f, 90f);
            assertVec(new Vec3(0, -1, 0), frame.right());
            assertVec(new Vec3(-1, 0, 0), frame.headUp());
            assertVec(new Vec3(0, 0, 1), frame.forward());
        }

        @Test
        void roll180FlipsRightAndHeadUp() {
            HeadFrameMath.HeadFrame frame = HeadFrameMath.of(0f, 0f, 180f);
            assertVec(new Vec3(1, 0, 0), frame.right());
            assertVec(new Vec3(0, -1, 0), frame.headUp());
            assertVec(new Vec3(0, 0, 1), frame.forward());
        }

        @Test
        void rollKeepsForwardUnchanged() {
            for (float roll : new float[]{-120f, -45f, 30f, 90f}) {
                HeadFrameMath.HeadFrame frame = HeadFrameMath.of(60f, 25f, roll);
                assertVec(legacyForward(60f, 25f), frame.forward());
            }
        }
    }

    @Nested
    @DisplayName("recoverRollDeg strips yaw/pitch from the camera rotation")
    class RollRecovery {

        private static Quaternionf cameraRotation(float yawDeg, float pitchDeg, float rollDeg) {
            return new Quaternionf()
                .rotateY((float) Math.PI - (float) Math.toRadians(yawDeg))
                .rotateX(-(float) Math.toRadians(pitchDeg))
                .rotateZ((float) Math.toRadians(rollDeg));
        }

        @Test
        void zeroWhenCameraHasNoRoll() {
            // Regression for first-person rotation twitching since 1.21: Halo
            // previously stripped Camera#rotation with the 1.20.1 basis,
            // producing a changing pseudo-roll even though vanilla has no roll.
            for (float yaw : new float[]{0f, 30f, 90f, 180f, -120f}) {
                for (float pitch : new float[]{-60f, -30f, 0f, 30f, 60f}) {
                    float roll = HeadFrameMath.recoverRollDeg(yaw, pitch, cameraRotation(yaw, pitch, 0f));
                    assertEquals(0.0, roll, 1e-3, "roll for yaw=" + yaw + " pitch=" + pitch);
                }
            }
        }

        @Test
        void recoversExactRollForAnyYawPitch() {
            for (float yaw : new float[]{-170f, 15f, 90f}) {
                for (float pitch : new float[]{-45f, 0f, 55f}) {
                    for (float roll : new float[]{-120f, -45f, 0f, 30f, 170f}) {
                        float recovered = HeadFrameMath.recoverRollDeg(
                            yaw, pitch, cameraRotation(yaw, pitch, roll));
                        assertEquals(roll, recovered, 1e-2,
                            "roll for yaw=" + yaw + " pitch=" + pitch + " roll=" + roll);
                    }
                }
            }
        }

        @Test
        void recoveredRollIsIndependentOfYaw() {
            float reference = HeadFrameMath.recoverRollDeg(0f, 0f, cameraRotation(0f, 0f, 25f));
            for (float yaw : new float[]{-150f, -30f, 60f, 175f}) {
                float recovered = HeadFrameMath.recoverRollDeg(yaw, 0f, cameraRotation(yaw, 0f, 25f));
                assertEquals(reference, recovered, 1e-2, "yaw=" + yaw);
            }
        }
    }
}
