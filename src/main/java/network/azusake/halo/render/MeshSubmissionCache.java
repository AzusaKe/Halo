package network.azusake.halo.render;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.IntFunction;
import network.azusake.halo.core.Identifier;

/** Lookup results only; never asserts that GL state survives a draw or a fallback. */
final class MeshSubmissionCache<P, T> {
    private final IntFunction<P> programs;
    private final Function<Identifier, T> textures;
    private final Object[] variants = new Object[3];
    private final boolean[] resolved = new boolean[3];
    private Identifier baseId, maskId;
    private T base, mask;

    MeshSubmissionCache(IntFunction<P> programs, Function<Identifier, T> textures) {
        this.programs = programs;
        this.textures = textures;
    }

    @SuppressWarnings("unchecked")
    P program(boolean lit, boolean translucent) {
        int variant = lit ? translucent ? 2 : 1 : 0;
        if (!resolved[variant]) {
            variants[variant] = programs.apply(variant);
            resolved[variant] = true;
        }
        return (P) variants[variant];
    }

    T base(Identifier id) {
        if (!id.equals(baseId)) {
            base = textures.apply(id);
            baseId = id;
        }
        return base;
    }

    T mask(Identifier id) {
        if (id.equals(baseId)) return base;
        if (!id.equals(maskId)) {
            mask = textures.apply(id);
            maskId = id;
        }
        return mask;
    }

    void invalidate() {
        Arrays.fill(variants, null);
        Arrays.fill(resolved, false);
        baseId = maskId = null;
        base = mask = null;
    }
}
