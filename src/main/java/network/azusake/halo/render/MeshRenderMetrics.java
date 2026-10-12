package network.azusake.halo.render;

/** Opt-in render-thread diagnostics; times CPU submission, not GPU execution or whole-game FPS. */
final class MeshRenderMetrics {
    private static final boolean ENABLED = Boolean.getBoolean("halo.mesh.profile");
    private static long nanos, drawNanos, resident, expanded, vertexBytes, indexBytes, indexUploads, vertexUploads;
    private static long framePrepareNanos, frameDrawNanos, frameVertexBytes, frameIndexBytes, frameGeometryUploads;
    private static int frames;
    private static long previousFrameNanos;
    private MeshRenderMetrics() {}
    /** Main-camera collection cadence includes the entire previous frame, not just Halo CPU work. */
    static void frame() {
        if (!ENABLED) return;
        long now = System.nanoTime();
        if (previousFrameNanos != 0 && FrameTime.TYPE.isEnabled()) {
            var event = new FrameTime();
            event.frameNanos = now - previousFrameNanos;
            event.prepareNanos = framePrepareNanos;
            event.submitNanos = frameDrawNanos;
            event.vertexBytes = frameVertexBytes;
            event.indexBytes = frameIndexBytes;
            event.geometryUploads = frameGeometryUploads;
            event.commit();
        }
        previousFrameNanos = now;
        framePrepareNanos = frameDrawNanos = frameVertexBytes = frameIndexBytes = frameGeometryUploads = 0;
    }
    static void resetFrame() { previousFrameNanos = 0; }
    @jdk.jfr.Name("halo.FrameTime")
    @jdk.jfr.Label("Halo main-camera frame interval")
    @jdk.jfr.Category({"Halo", "Rendering"})
    @jdk.jfr.StackTrace(false)
    static final class FrameTime extends jdk.jfr.Event {
        static final jdk.jfr.EventType TYPE = jdk.jfr.EventType.getEventType(FrameTime.class);
        @jdk.jfr.Timespan(jdk.jfr.Timespan.NANOSECONDS)
        public long frameNanos;
        @jdk.jfr.Timespan(jdk.jfr.Timespan.NANOSECONDS)
        public long prepareNanos;
        @jdk.jfr.Timespan(jdk.jfr.Timespan.NANOSECONDS)
        public long submitNanos;
        @jdk.jfr.DataAmount(jdk.jfr.DataAmount.BYTES)
        public long vertexBytes;
        @jdk.jfr.DataAmount(jdk.jfr.DataAmount.BYTES)
        public long indexBytes;
        public long geometryUploads;
    }
    static long start() { return ENABLED ? System.nanoTime() : 0; }
    static void vertexUpload(int bytes) { if (ENABLED) { vertexBytes += bytes; frameVertexBytes += bytes; vertexUploads++; } }
    static void geometryUpload() { if (ENABLED) frameGeometryUploads++; }
    static void indexUpload(int bytes) { if (ENABLED) { indexBytes += bytes; frameIndexBytes += bytes; indexUploads++; } }
    static void submitted(long start) {
        if (ENABLED) {
            long elapsed = System.nanoTime() - start;
            drawNanos += elapsed;
            frameDrawNanos += elapsed;
        }
    }
    static void residentDraw() { if (ENABLED) resident++; }
    static void expandedDraw() { if (ENABLED) expanded++; }
    static void finish(long start, boolean frameEnd) {
        if (!ENABLED) return;
        long elapsed = System.nanoTime() - start;
        nanos += elapsed;
        framePrepareNanos += elapsed;
        if (frameEnd && ++frames == 300) {
            org.slf4j.LoggerFactory.getLogger("halo").info(
                "Halo mesh profile: frames={}, prepareMs/frame={}, drawMs/frame={}, resident={}, expanded={}, vertexUploads={}, vertexBytes={}, indexUploads={}, indexBytes={}",
                frames, nanos / (frames * 1_000_000.0), drawNanos / (frames * 1_000_000.0), resident, expanded, vertexUploads, vertexBytes, indexUploads, indexBytes);
            frames = 0; nanos = drawNanos = resident = expanded = vertexBytes = indexBytes = indexUploads = vertexUploads = 0;
        }
    }
}
