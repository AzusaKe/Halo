package network.azusake.halo.command;

import network.azusake.halo.config.HaloConfig;
import network.azusake.halo.data.HaloDefinition;
import network.azusake.halo.data.HaloEntityData;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.lifecycle.EntityHaloTracker;
import network.azusake.halo.lifecycle.HaloWorldSaveData;
import network.azusake.halo.manager.HaloManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;

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
 * /halo save                      – sync halo data and trigger world save
 * /halo inspect &lt;entity&gt;          – detailed runtime status of one entity's halo
 * /halo active                    – list all entities with active halos
 * /halo debug &lt;true|false&gt;        – toggle teleport/snap debug logging
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

        // --- /halo save (sync halo data + trigger world save) ---
        haloNode.then(literal("save")
            .executes(HaloConfigCommand::saveHaloData)
        );

        // --- /halo debug <true|false> ---
        haloNode.then(literal("debug")
            .then(argument("enabled", BoolArgumentType.bool())
                .executes(HaloConfigCommand::debugToggle)
            )
        );

        // --- /halo active (list entities with halos) ---
        haloNode.then(literal("active")
            .executes(HaloConfigCommand::listActiveHalos)
        );

        // --- /halo inspect <entity> (detailed halo status) ---
        haloNode.then(literal("inspect")
            .then(argument("target", EntityArgumentType.entity())
                .executes(HaloConfigCommand::inspectHalo)
            )
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
                    " §8layers=§7" + def.model().layers().size() +
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
     * /halo save — sync halo data to persistent state and trigger a world save-all.
     * This replaces the need for the vanilla {@code /save-all} command when testing
     * halo persistence (useful when cheats are not enabled).
     */
    private static int saveHaloData(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        MinecraftServer server = source.getServer();

        // Sync halo assignments to world persistent state
        ServerWorld overworld = server.getOverworld();
        if (overworld != null) {
            HaloWorldSaveData data = HaloWorldSaveData.get(overworld);
            data.syncFromManager();
        }

        // Trigger save-all so entity NBT (including our mixin data) is written
        server.saveAll(true, true, true);

        int count = HaloManager.getInstance().getActiveCount();
        source.sendFeedback(
            () -> Text.literal("§aWorld saved. §f" + count + "§a active halo(s) persisted."),
            true
        );
        return Command.SINGLE_SUCCESS;
    }

    /**
     * /halo active — list all entities that currently have an active halo.
     */
    private static int listActiveHalos(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        Map<UUID, HaloInstance> halos = HaloManager.getInstance().getActiveHalos();

        if (halos.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§eNo active halos."), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendFeedback(
            () -> Text.literal("§aActive halos (§f" + halos.size() + "§a):"), false
        );

        MinecraftServer server = source.getServer();
        for (var entry : halos.entrySet()) {
            UUID uuid = entry.getKey();
            HaloInstance instance = entry.getValue();

            // Try to resolve entity name
            String entityName = "<unknown>";
            for (var world : server.getWorlds()) {
                Entity e = world.getEntity(uuid);
                if (e != null) {
                    entityName = e.getDisplayName().getString();
                    break;
                }
            }

            boolean persisted = HaloEntityData.hasHalo(uuid);
            boolean teleporting = EntityHaloTracker.isTeleporting(uuid);
            long ageMs = System.currentTimeMillis() - instance.getCreatedAtTime();

            String status = instance.isActive() ? "§aactive" : "§cdead";
            String nbt = persisted ? "§a✓nbt" : "§c✗nbt";
            String tp = teleporting ? " §etp" : "";

            final String name = entityName;
            source.sendFeedback(() -> Text.literal(
                "  §7- §f" + name +
                    " §8uuid=§7" + uuid.toString().substring(0, 8) + "..." +
                    " §8def=§7" + instance.getDefinitionId() +
                    " §8age=§7" + (ageMs / 1000) + "s" +
                    " " + status + " " + nbt + tp
            ), false);
        }
        return halos.size();
    }

    /**
     * /halo inspect &lt;entity&gt; — detailed runtime status for one entity.
     * Shows: UUID, definition, active flag, creation time, NBT persistence status,
     * teleport status, snap flag, position/rotation, and damping state.
     */
    private static int inspectHalo(CommandContext<ServerCommandSource> ctx) {
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

        UUID uuid = living.getUuid();
        HaloInstance instance = HaloManager.getInstance().getHaloInstance(uuid);

        source.sendFeedback(
            () -> Text.literal("§6===== Halo Inspect: §f" + living.getDisplayName().getString() + " §6====="),
            false
        );

        source.sendFeedback(
            () -> Text.literal("  §8UUID:      §7" + uuid), false
        );

        if (instance == null) {
            source.sendFeedback(
                () -> Text.literal("  §8Status:    §eNo halo attached"), false
            );

            // Still check NBT in case entity has stale data
            boolean hasNbt = HaloEntityData.hasHalo(living);
            if (hasNbt) {
                Identifier nbtDef = HaloEntityData.getHaloDefinition(living);
                source.sendFeedback(
                    () -> Text.literal("  §8NBT:       §eStale NBT found §7(def=" + nbtDef + ") §e— will restore on reload"),
                    false
                );
            } else {
                source.sendFeedback(
                    () -> Text.literal("  §8NBT:       §7No halo NBT"), false
                );
            }
            return Command.SINGLE_SUCCESS;
        }

        // --- Instance exists ---
        boolean isActive = instance.isActive();
        long ageMs = System.currentTimeMillis() - instance.getCreatedAtTime();
        boolean persisted = HaloEntityData.hasHalo(living);
        boolean teleporting = EntityHaloTracker.isTeleporting(uuid);
        boolean needsSnap = instance.isNeedsSnap();

        source.sendFeedback(() -> Text.literal(
            "  §8Definition: §f" + instance.getDefinitionId()), false
        );
        source.sendFeedback(() -> Text.literal(
            "  §8Status:    " + (isActive ? "§aactive" : "§cdeactivated") +
            "  §8Age: §7" + (ageMs / 1000) + "s" +
            "  §8Created: §7" + instance.getCreatedAtTime()), false
        );

        // NBT persistence
        source.sendFeedback(() -> Text.literal(
            "  §8NBT:       " + (persisted ? "§a✓ persisted" : "§c✗ not persisted")), false
        );

        // Teleport / snap
        source.sendFeedback(() -> Text.literal(
            "  §8Teleport:  " + (teleporting ? "§ein grace period" : "§7idle") +
            "  §8NeedsSnap: " + (needsSnap ? "§etrue" : "§7false")), false
        );

        // Entity anchor
        var anchor = living instanceof net.minecraft.entity.player.PlayerEntity p
            ? p.getEyePos()
            : living.getPos().add(0, living.getHeight() * 0.85, 0);
        source.sendFeedback(() -> Text.literal(
            "  §8Anchor:    §7(" + fmt(anchor.x) + ", " + fmt(anchor.y) + ", " + fmt(anchor.z) + ")"
                + "  §8(pose computed client-side)"), false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static String fmt(double d) {
        return String.format("%.2f", d);
    }

    /**
     * /halo debug &lt;true|false&gt; — toggle teleport/snap debug logging.
     * When enabled, the server console prints a line every time a teleport is
     * detected and every time a snap correction is applied by the physics tick.
     */
    private static int debugToggle(CommandContext<ServerCommandSource> ctx) {
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        EntityHaloTracker.setDebugMode(enabled);

        ctx.getSource().sendFeedback(
            () -> Text.literal("§aHalo debug logging: " + (enabled ? "§eON" : "§7OFF")),
            true
        );
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
