package network.azusake.halo.platform;

/** Continuous client time, using output-frame time while ReplayMod exports a video. */
final class RenderFrameClock {
    record Time(long millis, long nanos) {}
    private boolean initialized;
    private long baseMillis, baseNanos, logicalNanos, lastWallNanos;
    private Object session;
    private int firstFrame, previousFrame, fps;
    private long captureBaseNanos;

    Time sample(long wallMillis, long wallNanos, Object capture, int frame, int framesPerSecond) {
        if (!initialized) {
            initialized = true;
            baseMillis = wallMillis;
            baseNanos = logicalNanos = wallNanos;
            lastWallNanos = wallNanos;
        }
        if (capture != null && framesPerSecond > 0) {
            if (session != capture || fps != framesPerSecond || frame < previousFrame) {
                // Keep existing animation ages valid when entering or restarting an export.
                captureBaseNanos = logicalNanos;
                firstFrame = frame;
                fps = framesPerSecond;
            }
            logicalNanos = captureBaseNanos
                + Math.round(((long) frame - firstFrame) * (1_000_000_000.0 / fps));
            previousFrame = frame;
            session = capture;
        } else {
            // Do not import the time spent exporting when returning to normal playback.
            if (session == null) logicalNanos += Math.max(0, wallNanos - lastWallNanos);
            session = null;
        }
        lastWallNanos = wallNanos;
        return new Time(baseMillis + (logicalNanos - baseNanos) / 1_000_000L, logicalNanos);
    }
}
