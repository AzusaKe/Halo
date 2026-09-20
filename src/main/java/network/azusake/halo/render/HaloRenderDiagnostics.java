package network.azusake.halo.render;

import network.azusake.halo.physics.OptionalIrisPassDetector;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in render-thread probe. glGetError consumes error flags; never enabled for benchmarks. */
final class HaloRenderDiagnostics implements AutoCloseable {
    private static final boolean ENABLED = Boolean.getBoolean("halo.renderDiagnostics");
    private static final Logger LOG = LoggerFactory.getLogger("HaloRenderDiagnostics");
    private static final HaloRenderDiagnostics DISABLED = new HaloRenderDiagnostics(null);
    private static final java.util.Map<String, Long> NEXT_LOG = new java.util.HashMap<>();
    private static String phase = "outside";
    private static long frames, submissions, errors, nextSummary;
    private final String previous;

    private HaloRenderDiagnostics(String previous) { this.previous = previous; }

    static void beginFrame() {
        if (!ENABLED) return;
        frames++;
        long now = System.nanoTime();
        if (now < nextSummary) return;
        nextSummary = now + 5_000_000_000L;
        LOG.info("frame={} submissions={} errorFlags={} shaders={} mainPass={} (diagnostics consume GL error flags)",
            frames, submissions, errors, OptionalIrisPassDetector.hasShaderPack(), OptionalIrisPassDetector.isMainPass());
    }

    static HaloRenderDiagnostics open(String path, Object environment) {
        if (!ENABLED) return DISABLED;
        var scope = new HaloRenderDiagnostics(phase);
        phase = path + "/" + environment;
        submissions++;
        checkpoint("before-capture");
        return scope;
    }

    static void checkpoint(String point) {
        if (!ENABLED) return;
        // Bounded even if a broken context keeps reporting errors.
        for (int i = 0; i < 8; i++) {
            int error = GL11.glGetError();
            if (error == GL11.GL_NO_ERROR) break;
            errors++;
            String key = phase + "/" + point + "/" + error;
            long now = System.nanoTime();
            if (now < NEXT_LOG.getOrDefault(key, 0L)) continue;
            NEXT_LOG.put(key, now + 5_000_000_000L);
            LOG.warn("frame={} phase={} point={} GL=0x{} VAO={} VBO={} EBO={} program={} shaders={} mainPass={}",
                frames, phase, point, Integer.toHexString(error),
                GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
                GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING), GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                OptionalIrisPassDetector.hasShaderPack(), OptionalIrisPassDetector.isMainPass());
        }
    }

    @Override public void close() {
        if (!ENABLED) return;
        checkpoint("after-restore");
        phase = previous;
    }
}
