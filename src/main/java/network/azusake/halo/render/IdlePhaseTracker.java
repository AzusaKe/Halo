package network.azusake.halo.render;

import network.azusake.halo.data.GroupVisualSnapshot;

import java.util.HashMap;
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
 * <p>While a transition is active the renderer additionally records the
 * per-group visual values it actually applies (offset/scale/alpha).  Hiding
 * mid-transition must start the fade-out from those on-screen values rather
 * than from the idle animation, otherwise the halo would visibly jump.</p>
 *
 * <p>Entries are pruned by a TTL so halos that died, unloaded, or were never
 * rendered again do not leak memory.</p>
 */
public final class IdlePhaseTracker {

    /** Entries not refreshed within this window are considered stale. */
    static final long ENTRY_TTL_MS = 10_000;

    private final Map<UUID, Entry> phases = new ConcurrentHashMap<>();

    /** The per-group visuals recorded while a transition was active. */
    private record Entry(double phase, boolean transitionActive,
                         Map<String, GroupVisualSnapshot> groups, long writtenAt) {}

    /**
     * Read snapshot for one halo: the last rendered idle phase, whether that
     * frame was inside a transition, and the per-group visuals applied then.
     */
    public record RenderState(double phase, boolean transitionActive,
                              Map<String, GroupVisualSnapshot> groups) {}

    /** Record the phase this halo was last rendered at. */
    public void record(UUID uuid, double phase, boolean transitionActive, long nowMillis) {
        phases.compute(uuid, (key, old) -> new Entry(
            phase,
            transitionActive,
            // Preserve any visuals recorded earlier (same or previous frames).
            // The network layer reads them when a mid-transition hide happens
            // BEFORE this frame's per-group visuals are re-recorded.
            old != null ? old.groups() : Map.of(),
            nowMillis));
    }

    /** Convenience variant using the wall clock. */
    public void record(UUID uuid, double phase, boolean transitionActive) {
        record(uuid, phase, transitionActive, System.currentTimeMillis());
    }

    /**
     * Record the per-group visual values applied during a transition frame.
     * Keeps the entry fresh (same TTL as the phase record).
     */
    public void recordGroupVisual(UUID uuid, String groupKey,
                                  float[] offset, float[] scale, float alpha,
                                  long nowMillis) {
        phases.compute(uuid, (key, old) -> {
            Map<String, GroupVisualSnapshot> groups =
                new HashMap<>(old != null ? old.groups() : Map.of());
            groups.put(groupKey, new GroupVisualSnapshot(offset, scale, alpha));
            return new Entry(
                old != null ? old.phase() : 0.0,
                old != null && old.transitionActive(),
                groups,
                nowMillis);
        });
    }

    /**
     * The last rendered idle phase for {@code uuid}, or {@link Double#NaN}
     * when there is no fresh record (halo never rendered recently or stale).
     */
    public double get(UUID uuid, long nowMillis) {
        RenderState state = read(uuid, nowMillis);
        return state != null ? state.phase() : Double.NaN;
    }

    /**
     * The full render state for {@code uuid}, or {@code null} when there is no
     * fresh record.
     */
    public RenderState read(UUID uuid, long nowMillis) {
        Entry e = phases.get(uuid);
        if (e == null) {
            return null;
        }
        if (nowMillis - e.writtenAt() > ENTRY_TTL_MS) {
            phases.remove(uuid, e);
            return null;
        }
        return new RenderState(e.phase(), e.transitionActive(), e.groups());
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
