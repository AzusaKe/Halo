package network.azusake.halo.util;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaloIdMatcherTest {

    private static final Identifier RING = new Identifier("halo", "ring_default");

    @Test
    void emptyQueryMatchesEverything() {
        assertTrue(HaloIdMatcher.matches(RING, ""));
        assertTrue(HaloIdMatcher.matches(RING, "   "));
        assertTrue(HaloIdMatcher.matches(RING, null));
    }

    @Test
    void pathOnlyQueryUsesCaseInsensitivePrefix() {
        assertTrue(HaloIdMatcher.matches(RING, "ring"));
        assertTrue(HaloIdMatcher.matches(RING, "RING_D"));
        assertFalse(HaloIdMatcher.matches(RING, "default"));
        assertFalse(HaloIdMatcher.matches(RING, "hal"));
    }

    @Test
    void namespacedQueryUsesFullIdentifierPrefix() {
        assertTrue(HaloIdMatcher.matches(RING, "halo:rin"));
        assertTrue(HaloIdMatcher.matches(RING, "HALO:RING"));
        assertFalse(HaloIdMatcher.matches(RING, "other:ring"));
    }

    @Test
    void resultsAreFilteredAndSortedByFullIdentifier() {
        List<Identifier> result = HaloIdMatcher.filterAndSort(List.of(
            new Identifier("zeta", "ring"),
            new Identifier("halo", "other"),
            new Identifier("alpha", "ring_blue")
        ), "ring");

        assertEquals(List.of(
            new Identifier("alpha", "ring_blue"),
            new Identifier("zeta", "ring")
        ), result);
    }
}
