package network.azusake.halo.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.BiFunction;

/** The same literals serve local execution and the client's combined completion tree. */
public final class RendererCommandTree {
    private RendererCommandTree() {}

    static <S> LiteralArgumentBuilder<S> command(BiFunction<S, String, Integer> execute) {
        return LiteralArgumentBuilder.<S>literal("renderer")
            .executes(ctx -> execute.apply(ctx.getSource(), null))
            .then(LiteralArgumentBuilder.<S>literal("compatibility")
                .executes(ctx -> execute.apply(ctx.getSource(), "compatibility")))
            .then(LiteralArgumentBuilder.<S>literal("cached")
                .executes(ctx -> execute.apply(ctx.getSource(), "cached")));
    }

    public static <S> void addSuggestions(CommandDispatcher<S> dispatcher) {
        // If Forge has not merged a /halo root, the client command dispatcher owns
        // the complete local tree. Creating a partial root here would hide children.
        if (dispatcher.getRoot().getChild("halo") == null) return;
        // Build descendants before merging so a colliding server /halo root gains
        // the renderer-only client suggestions without losing server children.
        dispatcher.register(LiteralArgumentBuilder.<S>literal("halo")
            .then(command((source, backend) -> 0)));
    }
}
