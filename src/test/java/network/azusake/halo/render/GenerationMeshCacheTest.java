package network.azusake.halo.render;

import java.util.ArrayList;
import java.util.Map;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.render.TriangleMesh;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GenerationMeshCacheTest {
    @Test void aFailedUploadIsNotRetriedEveryRefreshButANewGenerationCanRecover() {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var failed = GenerationMeshCache.<Resource>empty().updated(1, Map.of(A,FIRST), (id,mesh) -> {
            attempts.incrementAndGet(); throw new IllegalStateException("upload failed");
        },(id,error) -> {}).cache();
        var same = failed.updated(1,Map.of(A,FIRST),(id,mesh) -> { attempts.incrementAndGet(); return new Resource(); },fail()).cache();
        assertEquals(1,attempts.get()); assertNull(same.get(A));
        var recovered = same.updated(2,Map.of(A,FIRST),(id,mesh) -> { attempts.incrementAndGet(); return new Resource(); },fail()).cache();
        assertEquals(2,attempts.get()); assertNotNull(recovered.get(A)); recovered.close();
    }
    private static final Identifier A = new Identifier("halo:a"), B = new Identifier("halo:b");
    private static final TriangleMesh FIRST = mesh(0), SECOND = mesh(1);

    @Test void uploadsOncePerGenerationAndReusesAnUnchangedSharedModel() {
        var created = new ArrayList<Resource>();
        GenerationMeshCache<Resource> cache = GenerationMeshCache.empty();
        var first = cache.updated(1, Map.of(A, FIRST, B, FIRST), (id, mesh) -> add(created), fail());
        assertEquals(2, first.created());
        cache = first.cache();
        Resource a = cache.get(A), b = cache.get(B);

        var sameGeneration = cache.updated(1, Map.of(A, FIRST, B, FIRST), (id, mesh) -> add(created), fail());
        assertEquals(0, sameGeneration.created());
        assertSame(a, sameGeneration.cache().get(A));
        assertSame(b, sameGeneration.cache().get(B));
        assertEquals(2, created.size());
    }

    @Test void replacementRemovalFailureAndCloseReleaseExactlyTheirOwnedResources() {
        var created = new ArrayList<Resource>();
        var failures = new ArrayList<Identifier>();
        GenerationMeshCache<Resource> cache = GenerationMeshCache.<Resource>empty()
            .updated(1, Map.of(A, FIRST, B, FIRST), (id, mesh) -> add(created), (id, error) -> failures.add(id)).cache();
        Resource oldA = cache.get(A), oldB = cache.get(B);

        var changed = cache.updated(1, Map.of(A, SECOND), (id, mesh) -> add(created), (id, error) -> failures.add(id));
        assertEquals(1, changed.created());
        assertTrue(oldA.closed);
        assertTrue(oldB.closed);
        cache = changed.cache();
        Resource replacement = cache.get(A);

        var nextGeneration = cache.updated(2, Map.of(A, SECOND, B, FIRST), (id, mesh) -> {
            if (id.equals(B)) throw new IllegalStateException("synthetic upload failure");
            return add(created);
        }, (id, error) -> failures.add(id));
        assertEquals(1, nextGeneration.created());
        assertEquals(java.util.List.of(B), failures);
        assertTrue(replacement.closed);
        assertNull(nextGeneration.cache().get(B));
        Resource current = nextGeneration.cache().get(A);
        nextGeneration.cache().close();
        assertTrue(current.closed);
    }

    private static java.util.function.BiConsumer<Identifier, Throwable> fail() {
        return (id, error) -> org.junit.jupiter.api.Assertions.fail("Unexpected creation failure for " + id, error);
    }
    private static Resource add(ArrayList<Resource> resources) {
        Resource value = new Resource(); resources.add(value); return value;
    }
    private static TriangleMesh mesh(float x) {
        return new TriangleMesh(new float[]{x,0,0, x+1,0,0, x,1,0},
            new float[]{0,0,1,0,0,1}, new int[]{0,1,2});
    }
    private static final class Resource implements GenerationMeshCache.Owned {
        boolean closed;
        @Override public void close() { assertFalse(closed); closed = true; }
    }
}
