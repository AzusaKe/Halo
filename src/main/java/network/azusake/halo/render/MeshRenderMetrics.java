package network.azusake.halo.render;

/** Opt-in render-thread diagnostics; times CPU submission, not GPU execution or whole-game FPS. */
final class MeshRenderMetrics {
    private static final boolean ENABLED = Boolean.getBoolean("halo.mesh.profile");
    private static long nanos, resident, expanded, vertexBytes, indexBytes, indexUploads, vertexUploads;
    private static int frames;
    private MeshRenderMetrics() {}
    static long start() { return ENABLED ? System.nanoTime() : 0; }
    static void vertexUpload(int bytes) { if (ENABLED) { vertexBytes += bytes; vertexUploads++; } }
    static void indexUpload(int bytes) { if (ENABLED) { indexBytes += bytes; indexUploads++; } }
    static void residentDraw() { if (ENABLED) resident++; }
    static void expandedDraw() { if (ENABLED) expanded++; }
    static void finish(long start, boolean frameEnd) {
        if (!ENABLED) return;
        nanos += System.nanoTime() - start;
        if (frameEnd && ++frames == 300) {
            org.slf4j.LoggerFactory.getLogger("halo").info(
                "Halo mesh profile: frames={}, submitMs/frame={}, resident={}, expanded={}, vertexUploads={}, vertexBytes={}, indexUploads={}, indexBytes={}",
                frames, nanos / (frames * 1_000_000.0), resident, expanded, vertexUploads, vertexBytes, indexUploads, indexBytes);
            frames = 0; nanos = resident = expanded = vertexBytes = indexBytes = indexUploads = vertexUploads = 0;
        }
    }
}
