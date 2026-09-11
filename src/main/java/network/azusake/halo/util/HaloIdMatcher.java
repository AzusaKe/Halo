package network.azusake.halo.util;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Shared identifier filtering used by commands and the halo-scepter screen. */
public final class HaloIdMatcher {

    private HaloIdMatcher() {
    }

    /**
     * Match exactly like the existing command completion: a full identifier
     * prefix when ':' is present, otherwise a path-only prefix.
     */
    public static boolean matches(Identifier id, String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return true;
        }
        if (normalized.indexOf(':') >= 0) {
            return id.toString().toLowerCase(Locale.ROOT).startsWith(normalized);
        }
        return id.getPath().toLowerCase(Locale.ROOT).startsWith(normalized);
    }

    /** Return matching identifiers in stable, full-ID lexical order. */
    public static List<Identifier> filterAndSort(Collection<Identifier> ids, String query) {
        return ids.stream()
            .filter(id -> matches(id, query))
            .sorted(Comparator.comparing(Identifier::toString))
            .toList();
    }
}
