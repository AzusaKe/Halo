package network.azusake.halo.util;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaloIdMatcherTest {

    private static final Identifier RING = Identifier.of("halo", "ring_default");

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
    }

    @Test
    void namespaceQueryUsesCaseInsensitiveFullIdentifierPrefix() {
        assertTrue(HaloIdMatcher.matches(RING, "hal"));
        assertTrue(HaloIdMatcher.matches(RING, "HALO"));
        assertTrue(HaloIdMatcher.matches(RING, "halo:"));
        assertTrue(HaloIdMatcher.matches(RING, "halo:rin"));
        assertTrue(HaloIdMatcher.matches(RING, "HALO:RING"));
        assertFalse(HaloIdMatcher.matches(RING, "other:ring"));
        assertFalse(HaloIdMatcher.matches(RING, "alo"));
    }

    @Test
    void resultsAreFilteredAndSortedByFullIdentifier() {
        List<Identifier> result = HaloIdMatcher.filterAndSort(List.of(
            Identifier.of("zeta", "ring"),
            Identifier.of("halo", "other"),
            Identifier.of("alpha", "ring_blue")
        ), "ring");

        assertEquals(List.of(
            Identifier.of("alpha", "ring_blue"),
            Identifier.of("zeta", "ring")
        ), result);
    }

    @Test
    void namespacePrefixWithColonFiltersByFullIdentifier() {
        List<Identifier> result = HaloIdMatcher.filterAndSort(List.of(
            Identifier.of("halo", "ring_default"),
            Identifier.of("other", "halo_ring"),
            Identifier.of("halo_extra", "crown")
        ), "halo:");

        assertEquals(List.of(
            Identifier.of("halo", "ring_default")
        ), result);
    }
}
