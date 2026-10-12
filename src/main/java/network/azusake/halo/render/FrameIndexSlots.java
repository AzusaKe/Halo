package network.azusake.halo.render;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Each deferred draw keeps its own mutable index storage until the frame is consumed. */
final class FrameIndexSlots<T> implements AutoCloseable {
    private final List<T> slots = new ArrayList<>();
    private final Consumer<T> dispose;
    private long frame = Long.MIN_VALUE;
    private int used;

    FrameIndexSlots(Consumer<T> dispose) { this.dispose = dispose; }

    T acquire(long nextFrame, Supplier<T> create) {
        if (frame != nextFrame) {
            // Release storage no longer used by the previous completed frame.
            while (slots.size() > used) dispose.accept(slots.removeLast());
            frame = nextFrame;
            used = 0;
        }
        if (used == slots.size()) slots.add(create.get());
        return slots.get(used++);
    }

    @Override public void close() {
        slots.forEach(dispose);
        slots.clear();
        used = 0;
        frame = Long.MIN_VALUE;
    }
}
