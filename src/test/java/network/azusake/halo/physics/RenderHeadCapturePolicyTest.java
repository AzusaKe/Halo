package network.azusake.halo.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderHeadCapturePolicyTest {

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
