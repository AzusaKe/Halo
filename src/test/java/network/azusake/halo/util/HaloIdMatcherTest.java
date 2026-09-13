package network.azusake.halo.util;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaloIdMatcherTest {

    private static final ResourceLocation RING = new ResourceLocation("halo", "ring_default");

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
        List<ResourceLocation> result = HaloIdMatcher.filterAndSort(List.of(
            new ResourceLocation("zeta", "ring"),
            new ResourceLocation("halo", "other"),
            new ResourceLocation("alpha", "ring_blue")
        ), "ring");

        assertEquals(List.of(
            new ResourceLocation("alpha", "ring_blue"),
            new ResourceLocation("zeta", "ring")
        ), result);
    }

    @Test
    void namespacePrefixWithColonFiltersByFullIdentifier() {
        List<ResourceLocation> result = HaloIdMatcher.filterAndSort(List.of(
            new ResourceLocation("halo", "ring_default"),
            new ResourceLocation("other", "halo_ring"),
            new ResourceLocation("halo_extra", "crown")
        ), "halo:");

        assertEquals(List.of(
            new ResourceLocation("halo", "ring_default")
        ), result);
    }
}
