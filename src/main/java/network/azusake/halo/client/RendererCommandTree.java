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
        // If Fabric has not run yet and the server has no /halo, let Fabric attach
        // its complete client tree. Creating a root here would hide its other children.
        if (dispatcher.getRoot().getChild("halo") == null) return;
        // Build descendants before merging: Fabric API 0.92's recursive copy attaches
        // an empty root first, losing newly copied children if a server /halo exists.
        // This tree is only used for completion; Fabric's client dispatcher executes it.
        dispatcher.register(LiteralArgumentBuilder.<S>literal("halo")
            .then(command((source, backend) -> 0)));
    }
}
