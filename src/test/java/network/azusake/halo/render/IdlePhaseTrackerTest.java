package network.azusake.halo.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the renderer-owned idle-phase tracker used to align shutdown
 * heads with the last frame the player actually saw.
 */
class IdlePhaseTrackerTest {

    @Nested
    @DisplayName("IdlePhaseTracker")
    class TrackerTests {

        @Test
        @DisplayName("record + get returns the last rendered phase")
        void recordAndGet() {
            IdlePhaseTracker tracker = new IdlePhaseTracker();
            UUID uuid = UUID.randomUUID();

            tracker.record(uuid, 13.0, 1_000L);
            assertEquals(13.0, tracker.get(uuid, 1_000L), 1e-9);
            assertEquals(13.0, tracker.get(uuid, 1_500L), 1e-9, "still fresh within TTL");
        }

        @Test
        @DisplayName("get for an unknown uuid returns NaN")
        void getUnknownReturnsNaN() {
            IdlePhaseTracker tracker = new IdlePhaseTracker();
            assertTrue(Double.isNaN(tracker.get(UUID.randomUUID(), 1_000L)));
        }

        @Test
        @DisplayName("entry exactly at the TTL boundary is still valid")
        void ttlBoundaryStillValid() {
            IdlePhaseTracker tracker = new IdlePhaseTracker();
            UUID uuid = UUID.randomUUID();
            tracker.record(uuid, 5.0, 0L);
            assertEquals(5.0, tracker.get(uuid, IdlePhaseTracker.ENTRY_TTL_MS), 1e-9);
        }

        @Test
        @DisplayName("stale entry expires: NaN and removed")
        void staleEntryExpires() {
            IdlePhaseTracker tracker = new IdlePhaseTracker();
            UUID uuid = UUID.randomUUID();
            tracker.record(uuid, 7.0, 0L);

            long now = IdlePhaseTracker.ENTRY_TTL_MS + 1;
            assertTrue(Double.isNaN(tracker.get(uuid, now)));
            assertEquals(0, tracker.size(), "expired entry removed on read");
        }

        @Test
        @DisplayName("prune drops stale entries but keeps fresh ones")
        void pruneRemovesStaleOnly() {
            IdlePhaseTracker tracker = new IdlePhaseTracker();
            UUID stale = UUID.randomUUID();
            UUID fresh = UUID.randomUUID();
            tracker.record(stale, 1.0, 0L);
            tracker.record(fresh, 2.0, 9_000L);

            tracker.prune(IdlePhaseTracker.ENTRY_TTL_MS + 1);
            assertTrue(Double.isNaN(tracker.get(stale, IdlePhaseTracker.ENTRY_TTL_MS + 1)));
            assertEquals(2.0, tracker.get(fresh, IdlePhaseTracker.ENTRY_TTL_MS + 1), 1e-9);
        }

        @Test
        @DisplayName("clear drops all records")
        void clearEmpties() {
            IdlePhaseTracker tracker = new IdlePhaseTracker();
            tracker.record(UUID.randomUUID(), 3.0, 0L);
            tracker.clear();
            assertEquals(0, tracker.size());
        }
    }
}
