package network.azusake.halo.render;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import network.azusake.halo.core.render.FrameOutput;
import network.azusake.halo.core.render.PreviewFrame;
import network.azusake.halo.core.runtime.PreviewOptions;
import network.azusake.halo.core.runtime.PreviewSession;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewSessionPoolTest {
    private final UUID wearer = new UUID(0,1);
    private final Object screen = new Object(), world = new Object();
    private final List<FakeSession> created = new ArrayList<>();
    private final PreviewSessionPool pool = new PreviewSessionPool(options -> {
        var session = new FakeSession(); created.add(session); return session;
    });
    private void begin(int frame) { pool.beginFrame(screen, world, PreviewOptions.PHYSICS, frame); }
    private PreviewSessionPool.Lease acquire(Object key) { return pool.acquire(key, wearer, 1, PreviewOptions.PHYSICS); }

    @Test void viewsPersistAcrossFramesAndDuplicatePositionsStayIndependent() {
        begin(1);
        var first = acquire("left"); var duplicate = acquire("left"); var right = acquire("right");
        assertNotSame(first.session(), duplicate.session());
        assertNotSame(first.session(), right.session());
        first.close(); duplicate.close(); right.close(); pool.endFrame();
        begin(2);
        assertSame(first.session(), acquire("left").session());
        assertSame(duplicate.session(), acquire("left").session());
        pool.endFrame();
        assertFalse(right.session().isValid());
        assertTrue(first.session().isValid());
        pool.close(); pool.close();
        assertTrue(created.stream().allMatch(s -> s.closes == 1));
    }

    @Test void screenWorldOptionsRuntimeIdAndCoreInvalidationReleaseOldSessions() {
        begin(1); var old = acquire("left"); pool.endFrame();
        pool.beginFrame(new Object(), world, PreviewOptions.PHYSICS, 2);
        assertFalse(old.session().isValid());
        var replaced = acquire("left"); pool.endFrame();
        begin(3); var current = acquire("left"); pool.endFrame();
        assertFalse(replaced.session().isValid());
        ((FakeSession) current.session()).valid = false;
        begin(4); assertNotSame(current.session(), acquire("left").session()); pool.endFrame();
        pool.beginFrame(screen, new Object(), PreviewOptions.RIGID, 5);
        pool.endFrame();
        assertTrue(created.stream().allMatch(s -> s.closes == 1));
        begin(6); var entity = acquire("left"); pool.endFrame();
        begin(7); pool.acquire("left", wearer, 2, PreviewOptions.PHYSICS); pool.endFrame();
        assertFalse(entity.session().isValid());
        pool.close();
    }

    @Test void callsOutsideAFrameAreOwnedAndInterruptedFramesCanBeReleased() {
        try (var temporary = acquire("left")) { assertTrue(temporary.owned()); }
        assertEquals(1, created.get(0).closes);
        begin(1); var retained = acquire("left");
        // Host render throws before RETURN; next frame still prunes its unused view.
        begin(2); pool.endFrame();
        assertFalse(retained.session().isValid());
        begin(3); acquire("left");
        pool.close();
        assertTrue(created.stream().allMatch(s -> s.closes == 1));
    }

    private static class FakeSession implements PreviewSession {
        private int closes;
        private boolean valid = true;
        public FrameOutput render(PreviewFrame frame) { return new FrameOutput(1, List.of(), List.of()); }
        public boolean isValid() { return valid; }
        public void close() { closes++; valid = false; }
    }
}
