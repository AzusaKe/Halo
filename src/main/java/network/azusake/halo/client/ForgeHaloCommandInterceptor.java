package network.azusake.halo.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.util.HaloIdMatcher;

/**
 * Forge implementation of {@link HaloCommandInterceptor}.
 *
 * <p>Registers a client-side {@code /halo} command tree via
 * {@link RegisterClientCommandsEvent}.  The executor checks the current
 * phase and either handles ownership commands locally (LOCAL phase) or forwards them
 * to the server (MULTIPLAYER phase / singleplayer). Renderer selection always stays local.</p>
 *
 * <p>Single-threaded: all command executors run on the render thread.</p>
 */
public final class ForgeHaloCommandInterceptor implements HaloCommandInterceptor {

    private volatile boolean registered;

    @Override
    public void register() {
        if (registered) return;
        registered = true;

        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
            registerCommands(event.getDispatcher()));
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

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {

        var haloNode = Commands.literal("halo")
            .then(RendererCommandTree.command(ForgeHaloCommandInterceptor::renderer))
            .executes(ctx -> executeLocal("halo"))
            .then(Commands.literal("list")
                .executes(ctx -> executeLocal("halo list"))
            )
            .then(Commands.literal("dump")
                .executes(ctx -> executeLocal("halo dump"))
            )
            .then(Commands.literal("show")
                .executes(ctx -> executeLocal("halo show"))
                .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.entity())
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
                .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.entity())
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
                .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.entity())
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
            )
            .then(Commands.literal("priority")
                .executes(ctx -> executeLocal("halo priority"))
                .then(Commands.literal("list")
                    .executes(ctx -> executeLocal("halo priority list")))
                .then(Commands.literal("reload")
                    .executes(ctx -> executeLocal("halo priority reload")))
                .then(Commands.literal("set")
                    .then(Commands.argument("source", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                        .then(Commands.argument("priority", IntegerArgumentType.integer())
                            .executes(ctx -> executeLocal(ctx.getInput())))))
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
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                Component.translatable("halo.renderer.current", config.getPrimitiveRenderBackend()), false);
        }
        return 1;
    }

    /** Execute an ownership command locally or forward it according to the current phase. */
    private static int executeLocal(String command) {
        Minecraft client = Minecraft.getInstance();

        if (HaloPhaseTracker.getInstance().shouldIntercept()) {
            // LOCAL phase: handle on the client
            String result = HaloLocalCommandHandler.handle(command);
            if (result != null && client.player != null) {
                client.player.displayClientMessage(Component.literal(result), false);
            }
            return 0;
        }

        // MULTIPLAYER or singleplayer (integrated server): forward through the
        // vanilla connection so signed-command state and last-seen messages are
        // produced exactly as they are for a command typed without a client root.
        if (client.getConnection() != null) {
            sendCommandRaw(command);
        }
        return 0;
    }

    /**
     * Send a command through the normal 1.20.1 connection path. Forge executes
     * the local dispatcher before this method, so sending the resulting packet
     * does not re-enter the local command executor.
     */
    private static void sendCommandRaw(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) return;

        client.getConnection().sendCommand(command);
    }
}
