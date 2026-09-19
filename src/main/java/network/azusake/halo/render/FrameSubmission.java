package network.azusake.halo.render;

/** One simulation result shared by the solid and late stages of a main-camera frame. */
final class FrameSubmission<T> {
    private long frame = Long.MIN_VALUE;
    private T pending;
    private boolean earlyClaimed;

    boolean begin(long nextFrame) {
        if (frame == nextFrame) return false;
        frame = nextFrame;
        pending = null;
        earlyClaimed = false;
        return true;
    }

    void publish(T value) { pending = value; }

    T claimEarly() {
        if (earlyClaimed) return null;
        earlyClaimed = true;
        return pending;
    }

    T claimLate() {
        T result = pending;
        pending = null;
        return result;
    }

    void clear() { frame = Long.MIN_VALUE; pending = null; earlyClaimed = false; }
}
