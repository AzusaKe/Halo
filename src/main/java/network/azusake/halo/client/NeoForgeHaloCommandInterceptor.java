package network.azusake.halo.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.SuggestionProvider;


import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.util.HaloIdMatcher;


/**
 * NeoForge implementation of {@link HaloCommandInterceptor}.
 *
 * <p>Registers a client-side {@code /halo} command tree via
 * {@link RegisterClientCommandsEvent}. The executor checks the current
 * phase and either handles ownership commands locally (LOCAL phase) or forwards them
 * to the server (MULTIPLAYER phase / singleplayer). Renderer selection always stays local.</p>
 *
 * <p>Single-threaded: all command executors run on the render thread.</p>
 */

public final class NeoForgeHaloCommandInterceptor implements HaloCommandInterceptor {

    private volatile boolean registered;

    // Client tree construction needs only Brigadier; Commands initializes server permission registries.
    private static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    @Override
    public void register() {
        if (registered) return;
        registered = true;

        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> registerCommands(event.getDispatcher(), event.getBuildContext()));
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
            for (Identifier id : HaloJsonLoader.getDefinitions().keySet()) {
                if (HaloIdMatcher.matches(id, remaining)) {
                    builder.suggest(id.toString());
                }
            }
            return builder.buildFuture();
        };

    void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher,
                          net.minecraft.commands.CommandBuildContext registryAccess) {

        var haloNode = literal("halo")
            .then(RendererCommandTree.command(NeoForgeHaloCommandInterceptor::renderer))
            .executes(ctx -> executeLocal("halo"))
            .then(literal("list")
                .executes(ctx -> executeLocal("halo list"))
            )
            .then(literal("dump")
                .executes(ctx -> executeLocal("halo dump"))
            )
            .then(literal("show")
                .executes(ctx -> executeLocal("halo show"))
                .then(literal("@s")
                    .executes(ctx -> executeLocal("halo show @s"))
                    .then(argument("definition", net.minecraft.commands.arguments.IdentifierArgument.id())
                        .suggests(DEFINITION_SUGGESTIONS)
                        .executes(ctx -> {
                            String def = ctx.getArgument("definition", net.minecraft.resources.Identifier.class).toString();
                            return executeLocal("halo show @s " + def);
                        })
                    )
                )
                .then(argument("target", net.minecraft.commands.arguments.EntityArgument.entity())
                    .executes(ctx -> executeLocal("halo show"))
                    .then(argument("definition", net.minecraft.commands.arguments.IdentifierArgument.id())
                        .suggests(DEFINITION_SUGGESTIONS)
                        .executes(ctx -> {
                            String target = ctx.getInput().split(" ")[2];
                            String def = ctx.getArgument("definition", net.minecraft.resources.Identifier.class).toString();
                            return executeLocal("halo show " + target + " " + def);
                        })
                    )
                )
            )
            .then(literal("hide")
                .executes(ctx -> executeLocal("halo hide"))
                .then(literal("@s")
                    .executes(ctx -> executeLocal("halo hide @s"))
                )
                .then(argument("target", net.minecraft.commands.arguments.EntityArgument.entity())
                    .executes(ctx -> {
                        String target = ctx.getInput().split(" ")[2];
                        return executeLocal("halo hide " + target);
                    })
                )
            )
            .then(literal("config")
                .executes(ctx -> executeLocal("halo config"))
                .then(literal("linear-damping")
                    .then(argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config linear-damping " + v);
                        })
                    )
                )
                .then(literal("angular-damping")
                    .then(argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config angular-damping " + v);
                        })
                    )
                )
                .then(literal("max-linear-distance")
                    .then(argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.01))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config max-linear-distance " + v);
                        })
                    )
                )
                .then(literal("max-angular-degrees")
                    .then(argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config max-angular-degrees " + v);
                        })
                    )
                )
                .then(literal("scale")
                    .then(argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.1))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config scale " + v);
                        })
                    )
                )
                .then(literal("allow-angular-momentum")
                    .then(argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean v = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "value");
                            return executeLocal("halo config allow-angular-momentum " + v);
                        })
                    )
                )
                .then(literal("angular-momentum-factor")
                    .then(argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config angular-momentum-factor " + v);
                        })
                    )
                )
                .then(literal("max-angular-momentum-degrees")
                    .then(argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config max-angular-momentum-degrees " + v);
                        })
                    )
                )
            )
            .then(literal("reload")
                .executes(ctx -> executeLocal("halo reload"))
            )
            .then(literal("active")
                .executes(ctx -> executeLocal("halo active"))
            )
            .then(literal("inspect")
                .executes(ctx -> executeLocal("halo inspect"))
                .then(literal("@s")
                    .executes(ctx -> executeLocal("halo inspect @s"))
                )
                .then(argument("target", net.minecraft.commands.arguments.EntityArgument.entity())
                    .executes(ctx -> {
                        String target = ctx.getInput().split(" ")[2];
                        return executeLocal("halo inspect " + target);
                    })
                )
            )
            .then(literal("save")
                .executes(ctx -> executeLocal("halo save"))
            )
            .then(literal("debug")
                .executes(ctx -> executeLocal("halo debug"))
            );

        dispatcher.register(haloNode);
    }

    // ------------------------------------------------------------------
    // Phase-aware dispatch
    // ------------------------------------------------------------------

    /** Renderer preferences belong to this client, regardless of ownership phase or permission. */
    private static int renderer(CommandSourceStack source, String backend) {
        var config = network.azusake.halo.config.HaloModConfigStore.get();
        if (backend != null) {
            config.setPrimitiveRenderBackend(backend);
            network.azusake.halo.config.HaloModConfigStore.save(config);
        }
        source.sendSuccess(() -> Component.translatable("halo.renderer.current", config.getPrimitiveRenderBackend()), false);
        return 1;
    }

    /** Execute an ownership command locally or forward it according to the current phase. */
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
        // CommandExecutionC2SPacket, bypassing Fabric's ClientCommandInternals
        // hook to avoid re-entering our own client-command executor.
        if (client.getConnection() != null) {
            sendCommandRaw(command);
        }
        return 0;
    }

    /**
     * Send a command to the server as a raw {@code ChatMessageC2SPacket},
     * bypassing Fabric's {@code ClientCommandInternals} hook so we don't
     * re-enter our own client-command executor.
     *
     * <p>The server treats chat messages starting with {@code /}
     * as commands.  We serialize the packet by hand because the alternative
     * ({@code sendCommand()}) would be re-intercepted by Fabric and loop.</p>
     */
    private static void sendCommandRaw(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) return;

        // Send a CommandExecutionC2SPacket directly to the Netty pipeline,
        // bypassing Fabric's ClientCommandInternals hook entirely.
        // The constructor takes: command, timestamp, salt, argumentSignatures, lastSeenMessages
        var packet = new net.minecraft.network.protocol.game.ServerboundChatCommandPacket(command);

        client.getConnection().getConnection().send(packet);
    }
}
