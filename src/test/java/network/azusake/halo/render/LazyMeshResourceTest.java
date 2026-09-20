package network.azusake.halo.render;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LazyMeshResourceTest {
    @Test void sharedInstancesUploadOnceAndReleaseOnce() {
        var slot = new LazyMeshResource<Resource>();
        var uploads = new AtomicInteger();
        Resource first = slot.get(() -> { uploads.incrementAndGet(); return new Resource(); }, error -> fail(error));
        for (int frame = 0; frame < 1000; frame++)
            assertSame(first, slot.get(() -> { throw new AssertionError("re-upload"); }, error -> fail(error)));
        assertEquals(1, uploads.get());
        slot.close(); slot.close();
        assertEquals(1, first.closes);
    }
    @Test void aFailedIrisUploadDoesNotRetryEachFrameOrDisableNativeAndNewGenerationRecovers() {
        var iris = new LazyMeshResource<Resource>();
        var nativeSlot = new LazyMeshResource<Resource>();
        var attempts = new AtomicInteger();
        for (int i = 0; i < 10; i++) assertNull(iris.get(() -> {
            attempts.incrementAndGet(); throw new IllegalStateException("upload");
        }, error -> {}));
        assertEquals(1, attempts.get());
        assertNotNull(nativeSlot.get(Resource::new, error -> fail(error)));
        iris.close();
        iris = new LazyMeshResource<>();
        assertNotNull(iris.get(Resource::new, error -> fail(error)));
        iris.close(); nativeSlot.close();
    }
    private static class Resource implements GenerationMeshCache.Owned {
        int closes;
        public void close() { closes++; }
    }
}
