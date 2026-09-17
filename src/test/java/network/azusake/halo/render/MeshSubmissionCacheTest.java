package network.azusake.halo.render;

import java.util.ArrayList;
import java.util.List;
import network.azusake.halo.core.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeshSubmissionCacheTest {
    @Test void variantsResolveOnceAndFallbackInvalidatesEvenAMissingProgram() {
        var requests = new ArrayList<Integer>();
        var cache = new MeshSubmissionCache<String, String>(variant -> {
            requests.add(variant); return variant == 2 ? null : "program" + variant;
        }, Identifier::toString);
        assertEquals("program0", cache.program(false, false));
        assertEquals("program0", cache.program(false, true));
        assertEquals("program1", cache.program(true, false));
        assertNull(cache.program(true, true)); assertNull(cache.program(true, true));
        assertEquals(List.of(0, 1, 2), requests);
        cache.invalidate();
        cache.program(true, true); cache.program(false, false);
        assertEquals(List.of(0, 1, 2, 2, 0), requests);
    }

    @Test void texturesAreOnlyLookupResultsScopedToOneSubmission() {
        var a = new Identifier("halo:a"); var b = new Identifier("halo:b"); var c = new Identifier("halo:c");
        var requests = new ArrayList<Identifier>();
        var cache = new MeshSubmissionCache<Object, Object>(v -> new Object(), id -> {
            requests.add(id); return new Object();
        });
        Object first = cache.base(a);
        assertSame(first, cache.mask(a)); assertSame(first, cache.base(a));
        Object mask = cache.mask(b);
        assertSame(mask, cache.mask(b));
        cache.base(c); assertSame(mask, cache.mask(b));
        assertEquals(List.of(a, b, c), requests);
        cache.invalidate();
        assertNotSame(first, cache.base(a)); assertNotSame(mask, cache.mask(b));
        assertEquals(List.of(a, b, c, a, b), requests);
    }
}
