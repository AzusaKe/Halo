package network.azusake.halo.render;

import network.azusake.halo.physics.OptionalIrisPassDetector;
import network.azusake.halo.physics.RenderHeadCapture;
import org.lwjgl.opengl.GL11C;
import org.slf4j.LoggerFactory;

/** Explicit opt-in GL diagnostics. Consumes error flags; do not enable during performance comparisons. */
final class HaloRenderDiagnostics {
    private static final boolean ENABLED = Boolean.getBoolean("halo.renderDiagnostics");
    private static long nextReport;
    private static long errors;
    private HaloRenderDiagnostics() {}

    static void check(String stage) {
        if (!ENABLED || !"OpenGL".equals(com.mojang.blaze3d.systems.RenderSystem.getDevice()
                .getDeviceInfo().backendName())) return;
        int first = GL11C.glGetError();
        if (first == GL11C.GL_NO_ERROR) return;
        errors++;
        // Bound error draining even if a lost context keeps returning an error.
        for (int i = 0; i < 15 && GL11C.glGetError() != GL11C.GL_NO_ERROR; i++) errors++;
        long now = System.nanoTime();
        if (now < nextReport) return;
        nextReport = now + 5_000_000_000L;
        LoggerFactory.getLogger("halo").warn(
            "Halo render diagnostic: stage={}, frame={}, mainPass={}, shaderPack={}, error=0x{}, totalErrors={}",
            stage, RenderHeadCapture.getFrameId(), OptionalIrisPassDetector.isMainPass(),
            OptionalIrisPassDetector.hasShaderPack(), Integer.toHexString(first), errors);
    }
}
