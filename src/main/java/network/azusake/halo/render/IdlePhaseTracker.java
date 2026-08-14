package network.azusake.halo.render;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side tracker for the last rendered idle-animation phase of each halo.
 *
 * <p>The renderer records the {@code animTime} it draws each halo at every
 * frame.  When a remove packet arrives for a halo whose shared instance was
 * already deleted by the integrated server, the network layer reads the last
 * recorded phase here so the shutdown animation's head can align to the exact
 * frame the player last saw — without any server involvement in rendering
 * state.</p>
 *
 * <p>Entries are pruned by a TTL so halos that died, unloaded, or were never
 * rendered again do not leak memory.</p>
 */
public final class IdlePhaseTracker {

    /** Entries not refreshed within this window are considered stale. */
    static final long ENTRY_TTL_MS = 10_000;

    private final Map<UUID, Entry> phases = new ConcurrentHashMap<>();

    private record Entry(double phase, long writtenAt) {}

    /** Record the phase this halo was last rendered at. */
    public void record(UUID uuid, double phase, long nowMillis) {
        phases.put(uuid, new Entry(phase, nowMillis));
    }

    /** Convenience variant using the wall clock. */
    public void record(UUID uuid, double phase) {
        record(uuid, phase, System.currentTimeMillis());
    }

    /**
     * The last recorded phase for {@code uuid}, or {@link Double#NaN} when
     * there is no fresh record (halo never rendered recently or stale).
     */
    public double get(UUID uuid, long nowMillis) {
        Entry e = phases.get(uuid);
        if (e == null) {
            return Double.NaN;
        }
        if (nowMillis - e.writtenAt() > ENTRY_TTL_MS) {
            phases.remove(uuid, e);
            return Double.NaN;
        }
        return e.phase();
    }

    /** Drop all records (full sync / world change). */
    public void clear() {
        phases.clear();
    }

    /** Drop stale entries.  Called once per rendered frame. */
    public void prune(long nowMillis) {
        phases.entrySet().removeIf(e -> nowMillis - e.getValue().writtenAt() > ENTRY_TTL_MS);
    }

    /** Number of live entries (debug/tests). */
    public int size() {
        return phases.size();
    }
}
