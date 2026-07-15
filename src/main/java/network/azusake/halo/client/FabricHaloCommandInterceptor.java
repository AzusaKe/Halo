package network.azusake.halo.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import network.azusake.halo.json.HaloJsonLoader;

import java.util.List;

/**
 * Fabric implementation of {@link HaloCommandInterceptor}.
 *
 * <p>Registers a client-side {@code /halo} command tree via
 * {@link ClientCommandRegistrationCallback}.  The executor checks the current
 * phase and either handles the command locally (LOCAL phase) or forwards it
 * to the server (MULTIPLAYER phase / singleplayer).</p>
 *
 * <p>Single-threaded: all command executors run on the render thread.</p>
 */
@Environment(EnvType.CLIENT)
public final class FabricHaloCommandInterceptor implements HaloCommandInterceptor {

    private volatile boolean registered;

    @Override
    public void register() {
        if (registered) return;
        registered = true;

        ClientCommandRegistrationCallback.EVENT.register(this::registerCommands);
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    // ------------------------------------------------------------------
    // Command tree
    // ------------------------------------------------------------------

    /** Suggests known halo definition IDs from the local resource pack. */
    private static final SuggestionProvider<FabricClientCommandSource> DEFINITION_SUGGESTIONS =
        (ctx, builder) -> {
            String remaining = builder.getRemaining().toLowerCase();
            for (Identifier id : HaloJsonLoader.getDefinitions().keySet()) {
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

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                  net.minecraft.command.CommandRegistryAccess registryAccess) {

        var haloNode = ClientCommandManager.literal("halo")
            .executes(ctx -> executeLocal("halo"))
            .then(ClientCommandManager.literal("list")
                .executes(ctx -> executeLocal("halo list"))
            )
            .then(ClientCommandManager.literal("dump")
                .executes(ctx -> executeLocal("halo dump"))
            )
            .then(ClientCommandManager.literal("show")
                .executes(ctx -> executeLocal("halo show"))
                .then(ClientCommandManager.argument("target", net.minecraft.command.argument.EntityArgumentType.entity())
                    .executes(ctx -> executeLocal("halo show"))
                    .then(ClientCommandManager.argument("definition", net.minecraft.command.argument.IdentifierArgumentType.identifier())
                        .suggests(DEFINITION_SUGGESTIONS)
                        .executes(ctx -> {
                            String target = ctx.getInput().split(" ")[2];
                            String def = ctx.getInput().split(" ")[3];
                            return executeLocal("halo show " + target + " " + def);
                        })
                    )
                )
            )
            .then(ClientCommandManager.literal("hide")
                .executes(ctx -> executeLocal("halo hide"))
                .then(ClientCommandManager.argument("target", net.minecraft.command.argument.EntityArgumentType.entity())
                    .executes(ctx -> {
                        String target = ctx.getInput().split(" ")[2];
                        return executeLocal("halo hide " + target);
                    })
                )
            )
            .then(ClientCommandManager.literal("config")
                .executes(ctx -> executeLocal("halo config"))
                .then(ClientCommandManager.literal("linear-damping")
                    .then(ClientCommandManager.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config linear-damping " + v);
                        })
                    )
                )
                .then(ClientCommandManager.literal("angular-damping")
                    .then(ClientCommandManager.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config angular-damping " + v);
                        })
                    )
                )
                .then(ClientCommandManager.literal("max-linear-distance")
                    .then(ClientCommandManager.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.01))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config max-linear-distance " + v);
                        })
                    )
                )
                .then(ClientCommandManager.literal("max-angular-degrees")
                    .then(ClientCommandManager.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config max-angular-degrees " + v);
                        })
                    )
                )
                .then(ClientCommandManager.literal("scale")
                    .then(ClientCommandManager.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.1, 5.0))
                        .executes(ctx -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                            return executeLocal("halo config scale " + v);
                        })
                    )
                )
            )
            .then(ClientCommandManager.literal("reload")
                .executes(ctx -> executeLocal("halo reload"))
            )
            .then(ClientCommandManager.literal("active")
                .executes(ctx -> executeLocal("halo active"))
            )
            .then(ClientCommandManager.literal("inspect")
                .executes(ctx -> executeLocal("halo inspect"))
            )
            .then(ClientCommandManager.literal("save")
                .executes(ctx -> executeLocal("halo save"))
            )
            .then(ClientCommandManager.literal("debug")
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
        MinecraftClient client = MinecraftClient.getInstance();

        if (HaloPhaseTracker.getInstance().shouldIntercept()) {
            // LOCAL phase: handle on the client
            String result = HaloLocalCommandHandler.handle(command);
            if (result != null && client.player != null) {
                client.player.sendMessage(Text.literal(result), false);
            }
            return 0;
        }

        // MULTIPLAYER or singleplayer (integrated server):
        // forward the command directly to the Netty pipeline via
        // CommandExecutionC2SPacket, bypassing Fabric's ClientCommandInternals
        // hook to avoid re-entering our own client-command executor.
        if (client.getNetworkHandler() != null) {
            sendCommandRaw(command);
        }
        return 0;
    }

    /**
     * Send a command to the server as a raw {@code ChatMessageC2SPacket},
     * bypassing Fabric's {@code ClientCommandInternals} hook so we don't
     * re-enter our own client-command executor.
     *
     * <p>In 1.20.1 the server treats chat messages starting with {@code /}
     * as commands.  We serialize the packet by hand because the alternative
     * ({@code sendCommand()}) would be re-intercepted by Fabric and loop.</p>
     */
    private static void sendCommandRaw(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return;

        // Send a CommandExecutionC2SPacket directly to the Netty pipeline,
        // bypassing Fabric's ClientCommandInternals hook entirely.
        // The constructor takes: command, timestamp, salt, argumentSignatures, lastSeenMessages
        var now = java.time.Instant.now();
        var emptySigs = net.minecraft.network.message.ArgumentSignatureDataMap.EMPTY;
        var lastSeen = new net.minecraft.network.message.LastSeenMessageList.Acknowledgment(0, new java.util.BitSet());

        var packet = new net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket(
            command, now, 0L, emptySigs, lastSeen);

        client.getNetworkHandler().getConnection().send(packet);
    }
}
