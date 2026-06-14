package com.example.halo.command;

import com.example.halo.config.HaloConfig;
import com.example.halo.data.HaloDefinition;
import com.example.halo.json.HaloJsonLoader;
import com.example.halo.manager.HaloManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Brigadier command tree for {@code /halo}.
 *
 * <p>All sub-commands require permission level 2 (op).</p>
 *
 * <pre>
 * /halo list                      – list loaded halo definitions
 * /halo dump                      – detailed dump of all definitions
 * /halo reload                    – hint to use /reload
 * /halo show &lt;entity&gt; &lt;definition&gt; – attach a halo to an entity
 * /halo hide &lt;entity&gt;             – remove a halo from an entity
 * /halo config &lt;param&gt; &lt;value&gt;    – change runtime config
 * </pre>
 */
public final class HaloConfigCommand {

    private HaloConfigCommand() {
        // utility class
    }

    /**
     * Register the full {@code /halo} command tree on the given dispatcher.
     *
     * @param dispatcher the Brigadier command dispatcher
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var haloNode = literal("halo")
            .requires(source -> source.hasPermissionLevel(2));

        // --- /halo list ---
        haloNode.then(literal("list")
            .executes(HaloConfigCommand::listDefinitions)
        );

        // --- /halo dump (detailed) ---
        haloNode.then(literal("dump")
            .executes(HaloConfigCommand::dumpDefinitions)
        );

        // --- /halo reload (convenience hint) ---
        haloNode.then(literal("reload")
            .executes(HaloConfigCommand::reloadHint)
        );

        // --- /halo show <entity> <definition> ---
        // Use greedyString() because regular string()/word() disallow ':' in unquoted input
        haloNode.then(literal("show")
            .then(argument("target", EntityArgumentType.entity())
                .then(argument("definition", StringArgumentType.greedyString())
                    .executes(HaloConfigCommand::showHalo)
                )
            )
        );

        // --- /halo hide <entity> ---
        haloNode.then(literal("hide")
            .then(argument("target", EntityArgumentType.entity())
                .executes(HaloConfigCommand::hideHalo)
            )
        );

        // --- /halo config <param> <value> ---
        var configNode = literal("config");

        configNode.then(literal("linear-damping")
            .then(argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                .executes(ctx -> configSet(ctx, "linear-damping",
                    DoubleArgumentType.getDouble(ctx, "value")))
            )
        );

        configNode.then(literal("angular-damping")
            .then(argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                .executes(ctx -> configSet(ctx, "angular-damping",
                    DoubleArgumentType.getDouble(ctx, "value")))
            )
        );

        configNode.then(literal("max-linear-distance")
            .then(argument("value", DoubleArgumentType.doubleArg(0.01, Double.MAX_VALUE))
                .executes(ctx -> configSet(ctx, "max-linear-distance",
                    DoubleArgumentType.getDouble(ctx, "value")))
            )
        );

        configNode.then(literal("max-angular-degrees")
            .then(argument("value", DoubleArgumentType.doubleArg(1.0, Double.MAX_VALUE))
                .executes(ctx -> configSet(ctx, "max-angular-degrees",
                    DoubleArgumentType.getDouble(ctx, "value")))
            )
        );

        configNode.then(literal("scale")
            .then(argument("value", DoubleArgumentType.doubleArg(0.1, 5.0))
                .executes(ctx -> configSet(ctx, "scale",
                    DoubleArgumentType.getDouble(ctx, "value")))
            )
        );

        haloNode.then(configNode);

        dispatcher.register(haloNode);
    }

    // ------------------------------------------------------------------
    // Command executors
    // ------------------------------------------------------------------

    /**
     * /halo list — show a compact listing of all loaded halo definitions.
     */
    private static int listDefinitions(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        Map<Identifier, HaloDefinition> defs = HaloJsonLoader.getDefinitions();

        if (defs.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§eNo halo definitions loaded."), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendFeedback(() -> Text.literal("§aLoaded halo definitions (" + defs.size() + "):"), false);
        for (Identifier id : defs.keySet()) {
            source.sendFeedback(() -> Text.literal("  §7- §f" + id), false);
        }
        return defs.size();
    }

    /**
     * /halo dump — detailed dump of all loaded definitions with shape/animation/damping info.
     */
    private static int dumpDefinitions(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        Map<Identifier, HaloDefinition> defs = HaloJsonLoader.getDefinitions();

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
    }

    /**
     * /halo reload — convenience hint directing the player to use /reload.
     */
    private static int reloadHint(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        source.sendFeedback(() -> Text.literal("§eUse §f/reload§e to reload all resources including halo definitions."), false);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * /halo show &lt;entity&gt; &lt;definition&gt; — attach a halo to a living entity.
     */
    private static int showHalo(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        Entity target;

        try {
            target = EntityArgumentType.getEntity(ctx, "target");
        } catch (Exception e) {
            source.sendError(Text.literal("Invalid entity selector."));
            return 0;
        }

        if (!(target instanceof LivingEntity living)) {
            source.sendError(Text.literal("Target must be a living entity."));
            return 0;
        }

        String defIdRaw = StringArgumentType.getString(ctx, "definition");
        Identifier defId = Identifier.tryParse(defIdRaw);
        if (defId == null) {
            source.sendError(Text.literal("Invalid definition identifier: " + defIdRaw));
            return 0;
        }

        // Namespace fallback: if the user omits the namespace (defaults to "minecraft"),
        // try "halo" namespace first since halo definitions are registered under "halo:"
        if ("minecraft".equals(defId.getNamespace())) {
            Identifier haloNsId = new Identifier("halo", defId.getPath());
            if (HaloJsonLoader.getDefinition(haloNsId).isPresent()) {
                defId = haloNsId;
            }
        }

        // Capture a final copy for use in lambdas below
        final Identifier resolvedId = defId;

        HaloManager.getInstance().showHaloOn(living, resolvedId);

        // Check if the definition was actually loaded
        if (HaloManager.getInstance().getHaloInstance(living.getUuid()) == null) {
            source.sendError(Text.literal("Unknown halo definition: " + resolvedId));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("§aHalo §f" + resolvedId + "§a shown on §f" + living.getDisplayName().getString()), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * /halo hide &lt;entity&gt; — remove a halo from a living entity.
     */
    private static int hideHalo(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        Entity target;

        try {
            target = EntityArgumentType.getEntity(ctx, "target");
        } catch (Exception e) {
            source.sendError(Text.literal("Invalid entity selector."));
            return 0;
        }

        if (!(target instanceof LivingEntity living)) {
            source.sendError(Text.literal("Target must be a living entity."));
            return 0;
        }

        HaloManager.getInstance().hideHaloOn(living);

        source.sendFeedback(() -> Text.literal("§aHalo hidden from §f" + living.getDisplayName().getString()), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * /halo config &lt;param&gt; &lt;value&gt; — set a runtime configuration value.
     */
    private static int configSet(CommandContext<ServerCommandSource> ctx, String param, double value) {
        HaloConfig config = HaloManager.getInstance().getConfig();

        switch (param) {
            case "linear-damping"      -> config.setLinearDampingFactor(value);
            case "angular-damping"     -> config.setAngularDampingFactor(value);
            case "max-linear-distance" -> config.setMaxLinearDistance(value);
            case "max-angular-degrees" -> config.setMaxAngularDegrees(value);
            case "scale"               -> config.setHaloScale(value);
            default -> {
                ctx.getSource().sendError(Text.literal("Unknown config parameter: " + param));
                return 0;
            }
        }

        ctx.getSource().sendFeedback(
            () -> Text.literal("§aSet §f" + param + "§a to §f" + value),
            true
        );
        return Command.SINGLE_SUCCESS;
    }
}
