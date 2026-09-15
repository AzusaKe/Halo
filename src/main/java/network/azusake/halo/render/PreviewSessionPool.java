package network.azusake.halo.render;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import network.azusake.halo.core.runtime.PreviewOptions;
import network.azusake.halo.core.runtime.PreviewSession;

/** Host-owned automatic UI views. Explicit integrations can own a PreviewSession directly. */
public final class PreviewSessionPool implements AutoCloseable {
    private final Function<PreviewOptions, PreviewSession> factory;
    private final Map<Key, Entry> views = new HashMap<>();
    private final Map<View, Integer> occurrences = new HashMap<>();
    private Object owner, world;
    private PreviewOptions options;
    private long frame, nanos;
    private boolean active;

    public PreviewSessionPool(Function<PreviewOptions, PreviewSession> factory) { this.factory = factory; }

    public void beginFrame(Object owner, Object world, PreviewOptions options, long nanos) {
        // Also finish an interrupted previous frame before starting another.
        if (active) endFrame();
        if (this.owner != owner || this.world != world || !Objects.equals(this.options, options)) close();
        this.owner = owner;
        this.world = world;
        this.options = options;
        this.nanos = nanos;
        occurrences.clear();
        frame++;
        active = world != null;
    }

    public Lease acquire(Object viewIdentity, UUID wearer, int runtimeId, PreviewOptions requested) {
        if (!active || !requested.equals(options)) return new Lease(factory.apply(requested), true);
        View view = new View(viewIdentity, wearer, runtimeId);
        int occurrence = occurrences.merge(view, 1, Integer::sum) - 1;
        Key key = new Key(view, occurrence);
        Entry entry = views.get(key);
        if (entry == null || !entry.session.isValid()) {
            if (entry != null) entry.session.close();
            entry = new Entry(factory.apply(requested));
            views.put(key, entry);
        }
        entry.frame = frame;
        return new Lease(entry.session, false);
    }

    public long frameNanos() { return active ? nanos : System.nanoTime(); }
    public void resetMotion() { views.values().forEach(entry -> entry.session.resetMotion()); }
    public void endFrame() {
        views.values().removeIf(entry -> {
            if (entry.frame == frame) return false;
            entry.session.close();
            return true;
        });
        occurrences.clear();
        active = false;
    }
    @Override public void close() {
        views.values().forEach(entry -> entry.session.close());
        views.clear();
        occurrences.clear();
        owner = world = null;
        options = null;
        active = false;
    }
    public record Lease(PreviewSession session, boolean owned) implements AutoCloseable {
        @Override public void close() { if (owned) session.close(); }
    }
    private record View(Object identity, UUID wearer, int runtimeId) {}
    private record Key(View view, int occurrence) {}
    private static final class Entry {
        private final PreviewSession session;
        private long frame;
        private Entry(PreviewSession session) { this.session = session; }
    }
}
