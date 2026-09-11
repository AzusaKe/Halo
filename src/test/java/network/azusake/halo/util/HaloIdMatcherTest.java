package network.azusake.halo.util;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaloIdMatcherTest {

    private static final ResourceLocation RING =
        ResourceLocation.fromNamespaceAndPath("halo", "ring_default");

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
        List<ResourceLocation> result = HaloIdMatcher.filterAndSort(List.of(
            ResourceLocation.fromNamespaceAndPath("zeta", "ring"),
            ResourceLocation.fromNamespaceAndPath("halo", "other"),
            ResourceLocation.fromNamespaceAndPath("alpha", "ring_blue")
        ), "ring");

        assertEquals(List.of(
            ResourceLocation.fromNamespaceAndPath("alpha", "ring_blue"),
            ResourceLocation.fromNamespaceAndPath("zeta", "ring")
        ), result);
    }
}
