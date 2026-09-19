package network.azusake.halo.render;

import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;

/** Opt-in JFR counters; no per-frame allocation or logging when recording is disabled. */
public final class HaloFrameDiagnostics {
    @Name("halo.Frame") @Label("Halo host frame") @Category("Halo")
    @Enabled(false) @StackTrace(false)
    public static final class Frame extends Event {
        @Timespan(Timespan.NANOSECONDS) public long interval;
        public int geometryUploads;
        public int sortedIndexUploads;
    }
    private static final Frame PROBE = new Frame();
    private static long previous;
    private static int geometryUploads, sortedIndexUploads;
    private HaloFrameDiagnostics() {}

    public static void endFrame() {
        if (PROBE.isEnabled()) {
            long now = System.nanoTime();
            if (previous != 0) {
                var event = new Frame();
                event.interval = now - previous;
                event.geometryUploads = geometryUploads;
                event.sortedIndexUploads = sortedIndexUploads;
                event.commit();
            }
            previous = now;
        } else previous = 0;
        geometryUploads = sortedIndexUploads = 0;
    }
    static void geometryUploaded() { if (PROBE.isEnabled()) geometryUploads++; }
    static void indicesUploaded() { if (PROBE.isEnabled()) sortedIndexUploads++; }
}
