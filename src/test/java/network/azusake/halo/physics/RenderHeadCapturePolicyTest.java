package network.azusake.halo.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import network.azusake.halo.api.HeadAnchor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RenderHeadCapturePolicyTest {

    @Nested
    class IrisPassSelection {
        @Test
        void absentIrisApiIsReportedAsVanillaMainPass() {
            OptionalIrisDiagnostics.Snapshot snapshot = OptionalIrisDiagnostics.snapshot();
            assertFalse(snapshot.irisLoaded());
            assertEquals(OptionalIrisDiagnostics.Status.FALSE, snapshot.shadowPass());
            assertTrue(RenderHeadCapture.isMainPass(snapshot));
        }

        @Test
        void vanillaMainPassIsAccepted() {
            assertTrue(RenderHeadCapture.isMainPass(new OptionalIrisDiagnostics.Snapshot(
                false,
                OptionalIrisDiagnostics.Status.FALSE,
                OptionalIrisDiagnostics.Status.FALSE,
                "not-loaded"
            )));
        }

        @Test
        void irisMainPassIsAccepted() {
            assertTrue(RenderHeadCapture.isMainPass(new OptionalIrisDiagnostics.Snapshot(
                true,
                OptionalIrisDiagnostics.Status.TRUE,
                OptionalIrisDiagnostics.Status.FALSE,
                "iris-api-v0"
            )));
        }

        @Test
        void irisShadowPassIsRejected() {
            assertFalse(RenderHeadCapture.isMainPass(new OptionalIrisDiagnostics.Snapshot(
                true,
                OptionalIrisDiagnostics.Status.TRUE,
                OptionalIrisDiagnostics.Status.TRUE,
                "iris-api-v0"
            )));
        }

        @Test
        void unknownPassIsRejectedFailSafe() {
            assertFalse(RenderHeadCapture.isMainPass(new OptionalIrisDiagnostics.Snapshot(
                true,
                OptionalIrisDiagnostics.Status.UNKNOWN,
                OptionalIrisDiagnostics.Status.UNKNOWN,
                "probe-failed"
            )));
        }
    }

    @Nested
    class DeferredMainCapture {
        private final Object level = new Object();
        private final RenderHeadCapture.MainPassAnchor cached =
            new RenderHeadCapture.MainPassAnchor(
                10L, 42, level, new Vec3(0.25, 1.75, -0.5),
                35f, -12f, 4f, 2, "PlayerModel@test"
            );

        @Test
        void currentAndImmediatelyFollowingFramesCanUseCapture() {
            assertTrue(cached.isUsable(10L, 42, level));
            assertTrue(cached.isUsable(11L, 42, level));
        }

        @Test
        void staleFutureWrongEntityAndWrongLevelAreRejected() {
            assertFalse(cached.isUsable(12L, 42, level));
            assertFalse(cached.isUsable(9L, 42, level));
            assertFalse(cached.isUsable(11L, 43, level));
            assertFalse(cached.isUsable(11L, 42, new Object()));
        }

        @Test
        void entityRelativeCenterFollowsCurrentInterpolatedPosition() {
            HeadAnchor resolved = cached.resolve(new Vec3(100, 64, -20));
            assertEquals(new Vec3(100.25, 65.75, -20.5), resolved.headCenter());
            assertEquals(35f, resolved.yaw());
            assertEquals(-12f, resolved.pitch());
            assertEquals(4f, resolved.roll());
        }

        @Test
        void playerSizedEntityRelativeCaptureIsPlausible() {
            assertTrue(RenderHeadCapture.isPlausibleEntityRelativeCenter(
                new Vec3(-0.1, 1.65, 0.05), 1.8));
        }

        @Test
        void guiSpaceAndNonFiniteCapturesAreRejected() {
            assertFalse(RenderHeadCapture.isPlausibleEntityRelativeCenter(
                new Vec3(280, 257, 2), 1.8));
            assertFalse(RenderHeadCapture.isPlausibleEntityRelativeCenter(
                new Vec3(Double.NaN, 1.65, 0), 1.8));
        }

        @Test
        void laterCaptureInSameFrameCannotOverwriteFirstTrustedCapture() {
            RenderHeadCapture.MainPassAnchor later = new RenderHeadCapture.MainPassAnchor(
                10L, 42, level, new Vec3(-0.1, 1.6, 0.1),
                40f, -8f, 0f, 3, "PlayerModel@later"
            );
            assertSame(cached, RenderHeadCapture.selectMainPassAnchor(cached, later));
        }

        @Test
        void trustedCaptureFromNewFrameReplacesPreviousFrame() {
            RenderHeadCapture.MainPassAnchor nextFrame = new RenderHeadCapture.MainPassAnchor(
                11L, 42, level, new Vec3(-0.1, 1.6, 0.1),
                40f, -8f, 0f, 1, "PlayerModel@next"
            );
            assertSame(nextFrame, RenderHeadCapture.selectMainPassAnchor(cached, nextFrame));
        }
    }

    @Nested
    class CameraPolicy {
        @Test
        void localFirstPersonAlwaysUsesCameraFallback() {
            assertFalse(RenderHeadAnchorProvider.shouldUseRenderCapture(true, true));
        }

        @Test
        void localThirdPersonUsesRenderedHead() {
            assertTrue(RenderHeadAnchorProvider.shouldUseRenderCapture(true, false));
        }

        @Test
        void remotePlayersCanUseRenderedHeadWhileCameraIsFirstPerson() {
            assertTrue(RenderHeadAnchorProvider.shouldUseRenderCapture(false, true));
        }
    }
}


