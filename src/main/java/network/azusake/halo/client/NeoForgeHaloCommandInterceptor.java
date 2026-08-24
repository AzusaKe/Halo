package network.azusake.halo.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import network.azusake.halo.json.HaloJsonLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * NeoForge implementation of {@link HaloCommandInterceptor}.
 *
 * <p>Registers a client-side {@code /halo} command tree via
 * {@link RegisterClientCommandsEvent}.  The executor checks the current
 * phase and either handles the command locally (LOCAL phase) or forwards it
 * to the server (MULTIPLAYER phase / singleplayer).</p>
 *
 * <p>Single-threaded: all command executors run on the render thread.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class NeoForgeHaloCommandInterceptor implements HaloCommandInterceptor {

    private volatile boolean registered;

    @Override
    public void register() {
        if (registered) return;
        registered = true;

        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class,
            event -> registerCommands(event.getDispatcher(), event.getBuildContext()));
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    // ------------------------------------------------------------------
    // Command tree
    // ------------------------------------------------------------------

    /** Suggests known halo definition IDs from the local resource pack. */
    private static final SuggestionProvider<CommandSourceStack> DEFINITION_SUGGESTIONS =
        (ctx, builder) -> {
            String remaining = builder.getRemaining().toLowerCase();
            for (ResourceLocation id : HaloJsonLoader.getDefinitions().keySet()) {
                String idStr = id.toString();
                if (idStr.toLowerCase().startsWith(remaining)) {
                    builder.suggest(idStr);
                } else if (!remaining.contains(":")
                    && id.getPath().toLowerCase().startsWith(remaining)) {
                    builder.suggest(idStr);
                }
            }
            return builder.buildFuture();
        };

    /**
     * Client-side {@code target} token: any non-whitespace run of characters.
     *
     * <p>Selectors such as {@code @s} and player names must be accepted here
     * without the permission gate that {@code EntityArgumentType} enforces —
     * on a vanilla server a non-OP player has permission level 0, which makes
     * entity-selector parsing fail client-side.  The raw token is either
     * validated by {@link HaloLocalCommandHandler} (LOCAL phase) or forwarded
     * verbatim to the server (MULTIPLAYER phase), so no selector resolution
     * ever happens client-side.</p>
     */
    private static final ArgumentType<String> TARGET_ARGUMENT = new ArgumentType<String>() {
        @Override
        public String parse(StringReader reader) throws CommandSyntaxException {
            int start = reader.getCursor();
            while (reader.canRead() && reader.peek() != ' ') {
                reader.skip();
            }
            if (reader.getCursor() == start) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader);
            }
            return reader.getString().substring(start, reader.getCursor());
        }

        @Override
        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            if ("@s".startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest("@s");
            }
            return builder.buildFuture();
        }
    };

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher,
                                  net.minecraft.commands.CommandBuildContext registryAccess) {

        var haloNode = Commands.literal("halo")
            .executes(ctx -> executeLocal("halo"))
            .then(Commands.literal("list")
                .executes(ctx -> executeLocal("halo list"))
            )
            .then(Commands.literal("dump")
                .executes(ctx -> executeLocal("halo dump"))
            )
            .then(Commands.literal("show")
                .executes(ctx -> executeLocal("halo show"))
                .then(Commands.argument("target", TARGET_ARGUMENT)
                    .executes(ctx -> executeLocal("halo show"))
                    .then(Commands.argument("definition", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                        .suggests(DEFINITION_SUGGESTIONS)
                        .executes(ctx -> {
                            String target = ctx.getInput().split(" ")[2];
                            String def = ctx.getInput().split(" ")[3];
                            return executeLocal("halo show " + target + " " + def);
                        })
                    )
                )
            )
            .then(Commands.literal("hide")
                .executes(ctx -> executeLocal("halo hide"))
                .then(Commands.argument("target", TARGET_ARGUMENT)
                    .executes(ctx -> {
                        String target = ctx.getInput().split(" ")[2];
                        return executeLocal("halo hide " + target);
                    })
                )
            )
            .then(Commands.literal("config")
                .executes(ctx -> executeLocal("halo config"))
                .then(Commands.literal("linear-damping")
                    .then(Commands.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config linear-damping " + v);
                        })
                    )
                )
                .then(Commands.literal("angular-damping")
                    .then(Commands.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config angular-damping " + v);
                        })
                    )
                )
                .then(Commands.literal("max-linear-distance")
                    .then(Commands.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.01))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config max-linear-distance " + v);
                        })
                    )
                )
                .then(Commands.literal("max-angular-degrees")
                    .then(Commands.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config max-angular-degrees " + v);
                        })
                    )
                )
                .then(Commands.literal("scale")
                    .then(Commands.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.1))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config scale " + v);
                        })
                    )
                )
                .then(Commands.literal("allow-angular-momentum")
                    .then(Commands.argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean v = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "value");
                            return executeLocal("halo config allow-angular-momentum " + v);
                        })
                    )
                )
                .then(Commands.literal("angular-momentum-factor")
                    .then(Commands.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config angular-momentum-factor " + v);
                        })
                    )
                )
                .then(Commands.literal("max-angular-momentum-degrees")
                    .then(Commands.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config max-angular-momentum-degrees " + v);
                        })
                    )
                )
            )
            .then(Commands.literal("reload")
                .executes(ctx -> executeLocal("halo reload"))
            )
            .then(Commands.literal("active")
                .executes(ctx -> executeLocal("halo active"))
            )
            .then(Commands.literal("inspect")
                .executes(ctx -> executeLocal("halo inspect"))
                .then(Commands.argument("target", TARGET_ARGUMENT)
                    .executes(ctx -> {
                        String target = ctx.getInput().split(" ")[2];
                        return executeLocal("halo inspect " + target);
                    })
                )
            )
            .then(Commands.literal("save")
                .executes(ctx -> executeLocal("halo save"))
            )
            .then(Commands.literal("debug")
                .executes(ctx -> executeLocal("halo debug"))
            );

        dispatcher.register(haloNode);
    }

    // ------------------------------------------------------------------
    // Phase-aware dispatch
    // ------------------------------------------------------------------

    /**
     * Execute a command locally or forward it to the server, depending on
     * the current phase.
     *
     * @param command the reconstructed command string without leading slash
     *                (e.g. {@code "halo list"})
     * @return {@code 0}
     */
    private static int executeLocal(String command) {
        Minecraft client = Minecraft.getInstance();

        if (HaloPhaseTracker.getInstance().shouldIntercept()) {
            // LOCAL phase: handle on the client
            String result = HaloLocalCommandHandler.handle(command);
            if (result != null && client.player != null) {
                client.player.sendSystemMessage(Component.literal(result));
            }
            return 0;
        }

        // MULTIPLAYER or singleplayer (integrated server):
        // forward the command directly to the Netty pipeline via
        // CommandExecutionC2SPacket, bypassing the client-command dispatcher
        // hook to avoid re-entering our own client-command executor.
        if (client.getConnection() != null) {
            sendCommandRaw(command);
        }
        return 0;
    }

    /**
     * Send a command to the server as a raw {@code CommandExecutionC2SPacket},
     * bypassing the client-command dispatcher so we do not
     * re-enter our own client-command executor.
     */
    private static void sendCommandRaw(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) return;

        // In 1.21.1 the packet only carries the command string; sending it
        // directly avoids client-command interception entirely.
        var packet = new net.minecraft.network.protocol.game.ServerboundChatCommandPacket(command);

        client.getConnection().getConnection().send(packet);
    }
}
