package network.azusake.halo.physics;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderHeadCapturePolicyTest {

    @Test
    void onlyMainWorldViewMatrixCanOpenYsmCaptureContext() {
        Matrix4f mainView = new Matrix4f().rotateY(0.4f).rotateX(-0.2f);
        RenderHeadCapture.setViewMatrix(mainView);

        assertTrue(RenderHeadCapture.matchesMainView(new Matrix4f(mainView)));
        assertFalse(RenderHeadCapture.matchesMainView(
            new Matrix4f().rotateY(-0.7f).rotateX(0.3f)));
        assertFalse(RenderHeadCapture.matchesMainView(
            new Matrix4f(mainView).translate(0.25f, 0f, 0f)));
    }

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
