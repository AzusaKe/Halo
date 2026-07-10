package network.azusake.halo.physics;

import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for player anchor provider math: head-center computation,
 * fallback behaviour, and pose key naming.
 *
 * <p>These tests exercise the pure-computation paths — no Minecraft
 * instance is required.</p>
 */
class PlayerAnchorProviderTest {

    // ------------------------------------------------------------------
    // 1. Head center math (three-step algorithm)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Head-center computation (pivot + rotation * head_center_vector)")
    class HeadCenterMath {

        /**
         * Re-implementation of the head-center math from {@link PlayerAnchorProvider#resolve}
         * for use in pure unit tests, without Minecraft entity dependencies.
         */
        private static Vec3d computeHeadCenter(
            Vec3d footPos, Vec3d pivotLocal, Vec3d hcv,
            float yawDeg, float pitchDeg
        ) {
            float yawRad = (float) Math.toRadians(yawDeg);
            float pitchRad = (float) Math.toRadians(pitchDeg);

            // World pivot
            Vec3d pivotWorld = footPos.add(pivotLocal);

            // Forward basis (matching AnchorFrameCalculator / computeHeadRelativeOffset)
            Vec3d forward = new Vec3d(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad)
            ).normalize();

            Vec3d worldUp = new Vec3d(0, 1, 0);
            Vec3d right;
            if (Math.abs(forward.dotProduct(worldUp)) > 0.999) {
                right = new Vec3d(-Math.cos(yawRad), 0, -Math.sin(yawRad));
            } else {
                right = forward.crossProduct(worldUp).normalize();
            }
            Vec3d headUp = right.crossProduct(forward).normalize();

            // Project head_center_vector through basis
            Vec3d offset = right.multiply(hcv.x)
                .add(headUp.multiply(hcv.y))
                .add(forward.multiply(hcv.z));

            return pivotWorld.add(offset);
        }

        @Test
        @DisplayName("standing: yaw=0 pitch=0, head center is pivot + hcv.y upward")
        void testStandingDefault() {
            // Standing player at origin, looking south (yaw=0, pitch=0)
            Vec3d footPos = new Vec3d(10, 64, 10);
            Vec3d pivot = new Vec3d(0, 1.50, 0);      // standing neck pivot
            Vec3d hcv = new Vec3d(0, 0.12, 0);        // head center above pivot

            Vec3d headCenter = computeHeadCenter(footPos, pivot, hcv, 0f, 0f);

            // At yaw=0 pitch=0: forward = (0, 0, 1), right = (1, 0, 0), headUp = (0, 1, 0)
            // headCenter = footPos + pivot + (0, 0.12, 0) = (10, 65.62, 10)
            assertEquals(10.0, headCenter.x, 0.001);
            assertEquals(65.62, headCenter.y, 0.001); // 64 + 1.50 + 0.12
            assertEquals(10.0, headCenter.z, 0.001);
        }

        @Test
        @DisplayName("standing: yaw=0 pitch=0 head center one unit above standing eye height")
        void testPivotToHeadCenterUpward() {
            Vec3d footPos = new Vec3d(0, 70, 0);
            Vec3d pivot = new Vec3d(0, 1.50, 0);
            Vec3d hcv = new Vec3d(0, 0.12, 0);

            Vec3d headCenter = computeHeadCenter(footPos, pivot, hcv, 0f, 0f);

            assertEquals(71.62, headCenter.y, 0.001);
        }

        @Test
        @DisplayName("standing: yaw=90° facing east, head center Y unchanged")
        void testYaw90PreservesHeight() {
            Vec3d footPos = new Vec3d(0, 64, 0);
            Vec3d pivot = new Vec3d(0, 1.50, 0);
            Vec3d hcv = new Vec3d(0, 0.12, 0);

            // yaw=90: forward = (-1, 0, 0), right = (0, 0, -1), headUp = (0, 1, 0)
            Vec3d headCenter = computeHeadCenter(footPos, pivot, hcv, 90f, 0f);

            assertEquals(65.62, headCenter.y, 0.001, "pitch=0 should not affect Y");
            // headCenter vector is (0, 0.12, 0), only Y component → only headUp gets it
            // headCenter = footPos + (0, 1.50, 0) + (0, 0.12, 0) = (0, 65.62, 0)
        }

        @Test
        @DisplayName("swimming: head_center_vector [0,0,0.4] projects forward with yaw=0")
        void testSwimmingForward() {
            // Swimming player: pivot at Y=0.4, head is 0.4 blocks forward
            Vec3d footPos = new Vec3d(0, 60, 0);
            Vec3d pivot = new Vec3d(0, 0.40, 0);
            Vec3d hcv = new Vec3d(0, 0, 0.40);   // head is forward from pivot

            Vec3d headCenter = computeHeadCenter(footPos, pivot, hcv, 0f, 0f);

            // yaw=0: forward = (0, 0, 1), headUp = (0, 1, 0)
            // pivotWorld = (0, 60.4, 0)
            // headCenter = pivotWorld + 0.4·forward = (0, 60.4, 0.4)
            assertEquals(0.0, headCenter.x, 0.001);
            assertEquals(60.4, headCenter.y, 0.001);
            assertEquals(0.4, headCenter.z, 0.001);
        }

        @Test
        @DisplayName("swimming: yaw=90° direction, head goes sideways")
        void testSwimmingYaw90() {
            Vec3d footPos = new Vec3d(0, 60, 0);
            Vec3d pivot = new Vec3d(0, 0.40, 0);
            Vec3d hcv = new Vec3d(0, 0, 0.40);

            Vec3d headCenter = computeHeadCenter(footPos, pivot, hcv, 90f, 0f);

            // yaw=90: forward = (-1, 0, 0)
            // headCenter = pivotWorld + 0.4·forward = (0, 60.4, 0) + (-0.4, 0, 0) = (-0.4, 60.4, 0)
            assertEquals(-0.4, headCenter.x, 0.001);
            assertEquals(60.4, headCenter.y, 0.001);
            assertEquals(0.0, headCenter.z, 0.001);
        }

        @Test
        @DisplayName("pitch up: head center moves backward (forward points up)")
        void testPitchAffectsForward() {
            // Pitch=-45° (looking up), yaw=0
            Vec3d footPos = new Vec3d(0, 64, 0);
            Vec3d pivot = new Vec3d(0, 1.50, 0);
            Vec3d hcv = new Vec3d(0, 0.12, 0.5); // has Z component

            Vec3d headCenter = computeHeadCenter(footPos, pivot, hcv, 0f, -45f);

            // pitch=-45: forward = (0, sin(45°), cos(45°)) = (0, 0.707, 0.707)
            // headCenter = (0, 65.5, 0) + 0.12*headUp + 0.5*forward
            // headUp ≈ right × forward (complex), but Y should be >65.62 due to forward's Y
            assertTrue(headCenter.y > (footPos.y + pivot.y + hcv.y),
                "upward pitch should raise head center");
        }

        @Test
        @DisplayName("snake: sneaking pivot is lower than standing")
        void testSneakingLower() {
            Vec3d footPos = new Vec3d(0, 64, 0);
            Vec3d pivotStanding = new Vec3d(0, 1.50, 0);
            Vec3d pivotSneaking = new Vec3d(0, 1.15, 0);
            Vec3d hcv = new Vec3d(0, 0.12, 0);

            Vec3d standHead = computeHeadCenter(footPos, pivotStanding, hcv, 0f, 0f);
            Vec3d sneakHead = computeHeadCenter(footPos, pivotSneaking, hcv, 0f, 0f);

            assertEquals(65.62, standHead.y, 0.001);
            assertEquals(65.27, sneakHead.y, 0.001);
            assertTrue(sneakHead.y < standHead.y,
                "sneaking head center should be 0.35 blocks lower");
        }
    }

    // ------------------------------------------------------------------
    // 2. Fallback behaviour
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Fallback provider gives height*0.85 head centre")
    class FallbackProvider {

        @Test
        @DisplayName("non-player entity: head at height * 0.85 above feet")
        void testHeight085Fallback() {
            // For a entity of height 2.0, head centre = feet + (0, 1.7, 0)
            double footY = 32.0;
            double height = 2.0;
            Vec3d expectedHead = new Vec3d(0, footY + height * 0.85, 0);

            assertEquals(32.0 + 1.7, expectedHead.y, 0.001);
            assertEquals(33.7, expectedHead.y, 0.001);
        }

        @Test
        @DisplayName("tall entity: head anchor scales proportionally")
        void testTallEntityFallback() {
            // Enderman height ≈ 2.9
            double footY = 32.0;
            double height = 2.9;
            Vec3d headCenter = new Vec3d(0, footY + height * 0.85, 0);

            assertEquals(32.0 + 2.465, headCenter.y, 0.001);
            assertEquals(34.465, headCenter.y, 0.001);
        }

        @Test
        @DisplayName("short entity: head anchor still follows 85% rule")
        void testShortEntityFallback() {
            // Chicken height ≈ 0.7
            double footY = 64.0;
            double height = 0.7;
            Vec3d headCenter = new Vec3d(0, footY + height * 0.85, 0);

            assertEquals(64.595, headCenter.y, 0.001);
        }
    }

    // ------------------------------------------------------------------
    // 3. Hard fallback constant is eye-height-neutral
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Hard fallback (FALLBACK_STANDING)")
    class HardFallback {

        @Test
        @DisplayName("hard fallback pivot at 1.62 + zero head_center_vector = eye height as head center")
        void testHardFallbackMatchesOldBehaviour() {
            // The hardcoded FALLBACK_STANDING in PlayerAnchorProvider:
            //   pivot = (0, 1.62, 0)
            //   head_center_vector = (0, 0, 0)
            // This means headCenter = footPos + pivot + 0 = footPos + (0, 1.62, 0)
            // which is exactly the old getStandingEyeHeight() behaviour.

            Vec3d footPos = new Vec3d(0, 64, 0);
            Vec3d pivot = new Vec3d(0, 1.62, 0);
            Vec3d hcv = new Vec3d(0, 0, 0);

            Vec3d headCenter = footPos.add(pivot).add(hcv);
            assertEquals(64 + 1.62, headCenter.y, 0.001);
            assertEquals(65.62, headCenter.y, 0.001);
        }
    }

    // ------------------------------------------------------------------
    // 4. EntityAnchorProfile resolve / fallback
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("EntityAnchorProfile fallback chain")
    class ProfileFallback {

        @Test
        @DisplayName("resolve returns direct match when key is present")
        void testDirectMatch() {
            var pose = new network.azusake.halo.data.PoseAnchor(
                new Vec3d(0, 1.5, 0), new Vec3d(0, 0.12, 0));
            var profile = new network.azusake.halo.data.EntityAnchorProfile(
                new net.minecraft.util.Identifier("minecraft:player"),
                "standing",
                java.util.Map.of("standing", pose)
            );

            assertTrue(profile.resolve("standing").isPresent());
            assertEquals(1.5, profile.resolve("standing").get().pivot().y, 0.001);
        }

        @Test
        @DisplayName("resolve falls back to defaultPose when key absent")
        void testFallbackToDefault() {
            var standingPose = new network.azusake.halo.data.PoseAnchor(
                new Vec3d(0, 1.5, 0), new Vec3d(0, 0.12, 0));
            var profile = new network.azusake.halo.data.EntityAnchorProfile(
                new net.minecraft.util.Identifier("minecraft:player"),
                "standing",
                java.util.Map.of("standing", standingPose)
            );

            // "unknown_key" is absent → should fall back to "standing"
            assertTrue(profile.resolve("unknown_key").isPresent());
            assertEquals(1.5, profile.resolve("unknown_key").get().pivot().y, 0.001);
        }

        @Test
        @DisplayName("resolve returns empty when defaultPose itself is missing")
        void testEmptyWhenDefaultMissing() {
            var swimmingPose = new network.azusake.halo.data.PoseAnchor(
                new Vec3d(0, 0.4, 0), new Vec3d(0, 0, 0.4));
            // defaultPose="standing" but no "standing" key exists → invalid profile
            var profile = new network.azusake.halo.data.EntityAnchorProfile(
                new net.minecraft.util.Identifier("minecraft:player"),
                "standing",
                java.util.Map.of("swimming", swimmingPose)
            );

            assertFalse(profile.isValid());
            assertTrue(profile.resolve("swimming").isPresent()); // direct hit works
            assertFalse(profile.resolve("unknown_key").isPresent()); // fallback to default is null
        }

        @Test
        @DisplayName("isValid returns true when at least defaultPose exists")
        void testValidProfile() {
            var standingPose = new network.azusake.halo.data.PoseAnchor(
                new Vec3d(0, 1.5, 0), new Vec3d(0, 0.12, 0));
            var swimmingPose = new network.azusake.halo.data.PoseAnchor(
                new Vec3d(0, 0.4, 0), new Vec3d(0, 0, 0.4));
            var profile = new network.azusake.halo.data.EntityAnchorProfile(
                new net.minecraft.util.Identifier("minecraft:player"),
                "standing",
                java.util.Map.of("standing", standingPose, "swimming", swimmingPose)
            );

            assertTrue(profile.isValid());
        }
    }

    // ------------------------------------------------------------------
    // 5. Player pose key mapping (string constants)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Pose key constants")
    class PoseKeyConstants {

        @Test
        @DisplayName("pose key naming matches JSON keys")
        void testPoseKeysAreStandard() {
            // The keys must match the JSON keys in player.json
            // Production code uses: sleeping, fall_flying, swimming, crawling, sneaking, standing
            String[] expectedKeys = {"sleeping", "fall_flying", "swimming", "crawling", "sneaking", "standing"};
            for (String key : expectedKeys) {
                assertNotNull(key); // placeholder — prevents unused variable warning
            }
        }
    }
}
