package network.azusake.halo.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.util.HaloIdMatcher;

import java.time.Instant;
import java.util.BitSet;

/** Forge 1.20.1 client-side command interceptor. */
public final class ForgeHaloCommandInterceptor implements HaloCommandInterceptor {
    private volatile boolean registered;

    private static final SuggestionProvider<CommandSourceStack> DEFINITIONS = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase();
        for (ResourceLocation id : HaloJsonLoader.getDefinitions().keySet()) {
            if (HaloIdMatcher.matches(id, remaining)) {
                builder.suggest(id.toString());
            }
        }
        return builder.buildFuture();
    };

    @Override public void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
            registerCommands(event.getDispatcher()));
    }

    @Override public boolean isRegistered() { return registered; }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        var halo = Commands.literal("halo").executes(c -> execute(c, "halo"));
        halo.then(Commands.literal("list").executes(c -> execute(c, "halo list")));
        halo.then(Commands.literal("dump").executes(c -> execute(c, "halo dump")));
        halo.then(Commands.literal("reload").executes(c -> execute(c, "halo reload")));
        halo.then(Commands.literal("active").executes(c -> execute(c, "halo active")));
        halo.then(Commands.literal("save").executes(c -> execute(c, "halo save")));
        halo.then(Commands.literal("debug").executes(c -> execute(c, "halo debug")));
        halo.then(Commands.literal("show").executes(c -> execute(c, "halo show"))
            .then(Commands.argument("target", EntityArgument.entity()).executes(c -> execute(c, "halo show"))
                .then(Commands.argument("definition", ResourceLocationArgument.id()).suggests(DEFINITIONS)
                    .executes(c -> execute(c, c.getInput())))));
        halo.then(Commands.literal("hide").executes(c -> execute(c, "halo hide"))
            .then(Commands.argument("target", EntityArgument.entity())
                .executes(c -> execute(c, c.getInput()))));
        var config = Commands.literal("config").executes(c -> execute(c, "halo config"));
        config.then(number("linear-damping", 0, 1));
        config.then(number("angular-damping", 0, 1));
        config.then(number("max-linear-distance", 0.01, Double.MAX_VALUE));
        config.then(number("max-angular-degrees", 1, Double.MAX_VALUE));
        config.then(number("scale", 0.1, Double.MAX_VALUE));
        config.then(Commands.literal("allow-angular-momentum")
            .then(Commands.argument("value", BoolArgumentType.bool()).executes(c -> execute(c, c.getInput()))));
        config.then(number("angular-momentum-factor", 0, 1));
        config.then(number("max-angular-momentum-degrees", 1, Double.MAX_VALUE));
        halo.then(config);
        halo.then(Commands.literal("inspect").executes(c -> execute(c, "halo inspect"))
            .then(Commands.argument("target", EntityArgument.entity()).executes(c -> execute(c, c.getInput()))));
        dispatcher.register(halo);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> number(String name, double min, double max) {
        return Commands.literal(name).then(Commands.argument("value", DoubleArgumentType.doubleArg(min, max))
            .executes(c -> execute(c, c.getInput())));
    }

    private static int execute(CommandContext<CommandSourceStack> context, String raw) {
        String command = raw.startsWith("/") ? raw.substring(1) : raw;
        Minecraft client = Minecraft.getInstance();
        if (HaloPhaseTracker.getInstance().shouldIntercept()) {
            String result = HaloLocalCommandHandler.handle(command);
            if (result != null && client.player != null) client.player.sendSystemMessage(Component.literal(result));
            return 0;
        }
        if (client.getConnection() != null) {
            client.getConnection().send(new ServerboundChatCommandPacket(
                command,
                Instant.now(),
                0L,
                ArgumentSignatures.EMPTY,
                new LastSeenMessages.Update(0, new BitSet())
            ));
        }
        return 0;
    }
}
