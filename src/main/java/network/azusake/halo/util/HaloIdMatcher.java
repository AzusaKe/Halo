package network.azusake.halo.util;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Shared identifier filtering used by commands and the halo-scepter screen. */
public final class HaloIdMatcher {

    private HaloIdMatcher() {
    }

    /** Match a full identifier prefix or a path-only prefix. */
    public static boolean matches(ResourceLocation id, String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return true;
        }
        String fullId = id.toString().toLowerCase(Locale.ROOT);
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return fullId.startsWith(normalized) || path.startsWith(normalized);
    }

    /** Return matching identifiers in stable, full-ID lexical order. */
    public static List<ResourceLocation> filterAndSort(Collection<ResourceLocation> ids, String query) {
        return ids.stream()
            .filter(id -> matches(id, query))
            .sorted(Comparator.comparing(ResourceLocation::toString))
            .toList();
    }
}
