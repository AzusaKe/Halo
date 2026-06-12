package com.example.halo;

import com.example.halo.data.HaloDefinition;
import com.example.halo.json.HaloJsonLoader;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static net.minecraft.server.command.CommandManager.literal;

public class HaloMod implements ModInitializer {

    public static final String MOD_ID = "halo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Halo mod initializing...");

        // Register resource reload listeners for JSON halo definitions
        HaloJsonLoader.register();

        // Register the /halo dump command for manual verification
        CommandRegistrationCallback.EVENT.register(this::registerCommands);

        LOGGER.info("Halo mod initialized");
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher,
                                   CommandRegistryAccess registryAccess,
                                   CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("halo")
            .then(literal("dump")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(ctx -> {
                    ServerCommandSource source = ctx.getSource();
                    Map<net.minecraft.util.Identifier, HaloDefinition> defs = HaloJsonLoader.getDefinitions();

                    if (defs.isEmpty()) {
                        source.sendFeedback(() -> Text.literal("§eNo halo definitions loaded. Run §f/reload§e first."), false);
                        return Command.SINGLE_SUCCESS;
                    }

                    source.sendFeedback(() -> Text.literal("§a=== Loaded Halo Definitions (" + defs.size() + ") ==="), false);
                    for (HaloDefinition def : defs.values()) {
                        source.sendFeedback(() -> Text.literal(
                            "§7  - §f" + def.id() +
                                " §8shape=§7" + def.shape().getClass().getSimpleName() +
                                " §8anim=§7" + def.animation().positionCurves().size() + "p/" + def.animation().rotationCurves().size() + "r" +
                                " §8damping=§7k=" + def.damping().linearFactor()
                        ), false);
                    }
                    return defs.size();
                })
            )
            .then(literal("reload")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(ctx -> {
                    // Trigger a resource reload via the standard /reload is preferred;
                    // this is a convenience alias that reminds the user.
                    ServerCommandSource source = ctx.getSource();
                    source.sendFeedback(() -> Text.literal("§eUse §f/reload§e to reload all resources including halo definitions."), false);
                    return Command.SINGLE_SUCCESS;
                })
            )
        );
    }
}
