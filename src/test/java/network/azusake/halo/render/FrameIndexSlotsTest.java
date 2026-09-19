package network.azusake.halo.render;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameIndexSlotsTest {
    @Test void queuedInstancesAndPreviewsCannotOverwriteEachOthersIndices() {
        var released = new ArrayList<int[]>();
        var slots = new FrameIndexSlots<int[]>(released::add);
        int[] worldA = slots.acquire(1, () -> new int[1]);
        int[] worldB = slots.acquire(1, () -> new int[1]);
        int[] preview = slots.acquire(1, () -> new int[1]);
        worldA[0] = 7; worldB[0] = 11; preview[0] = 13;
        assertEquals(7, worldA[0]); assertEquals(11, worldB[0]);
        assertNotSame(worldA, preview);
        assertTrue(released.isEmpty());
        assertSame(worldA, slots.acquire(2, () -> fail("Resident EBO should be reused")));
        assertSame(worldA, slots.acquire(3, () -> fail("Resident EBO should be reused")));
        assertEquals(2, released.size());
        slots.close(); slots.close();
        assertEquals(3, released.size());
    }

    @Test void failedAllocationDoesNotConsumeASlotAndReloadStartsFresh() {
        var counter = new AtomicInteger();
        var slots = new FrameIndexSlots<Integer>(ignored -> {});
        assertThrows(IllegalStateException.class, () -> slots.acquire(1, () -> { throw new IllegalStateException(); }));
        assertEquals(1, slots.acquire(1, counter::incrementAndGet));
        assertEquals(1, slots.acquire(2, counter::incrementAndGet));
        slots.close();
        assertEquals(2, slots.acquire(2, counter::incrementAndGet));
    }
}
