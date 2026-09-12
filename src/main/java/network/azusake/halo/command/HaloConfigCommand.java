package network.azusake.halo.command;

import network.azusake.halo.config.HaloConfig;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.data.HaloDefinition;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.lifecycle.EntityHaloTracker;
import network.azusake.halo.lifecycle.HaloWorldSaveData;
import network.azusake.halo.manager.HaloManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import network.azusake.halo.core.Identifier;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import network.azusake.halo.util.HaloIdMatcher;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Brigadier command tree for {@code /halo}.
 *
 * <p>All sub-commands require the permission level configured in
 * {@link network.azusake.halo.config.HaloModConfig} (default 2, operator).</p>
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
            .requires(source -> source.hasPermissionLevel(HaloModConfigStore.getPermissionLevel()));

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
        // Use IdentifierArgumentType which allows ':' in unquoted input, unlike word()/string()
        haloNode.then(literal("show")
            .then(argument("target", EntityArgumentType.entity())
                .then(argument("definition", IdentifierArgumentType.identifier())
                    .suggests(HaloConfigCommand::suggestDefinitions)
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
            .then(argument("value", DoubleArgumentType.doubleArg(0.1, Double.MAX_VALUE))
                .executes(ctx -> configSet(ctx, "scale",
                    DoubleArgumentType.getDouble(ctx, "value")))
            )
        );

        configNode.then(literal("allow-angular-momentum")
            .then(argument("value", BoolArgumentType.bool())
                .executes(ctx -> configSetBool(ctx, "allow-angular-momentum",
                    BoolArgumentType.getBool(ctx, "value")))
            )
        );

        configNode.then(literal("angular-momentum-factor")
            .then(argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                .executes(ctx -> configSet(ctx, "angular-momentum-factor",
                    DoubleArgumentType.getDouble(ctx, "value")))
            )
        );

        configNode.then(literal("max-angular-momentum-degrees")
            .then(argument("value", DoubleArgumentType.doubleArg(1.0, Double.MAX_VALUE))
                .executes(ctx -> configSet(ctx, "max-angular-momentum-degrees",
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
     * /halo list — show a compact listing of all server-loaded halo definitions
     * plus any definition IDs currently in use that the server doesn't know about
     * (provided by client resource packs).
     */
    private static int listDefinitions(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        Map<Identifier, HaloDefinition> defs = HaloJsonLoader.getDefinitions();
        Set<Identifier> clientIds = HaloJsonLoader.getClientReportedDefIds();

        // Current player's own reported IDs — highlighted in green
        UUID playerUuid = source.getPlayer() != null ? source.getPlayer().getUuid() : null;
        Set<Identifier> myDefs = playerUuid != null
            ? HaloJsonLoader.getClientReportedDefs(playerUuid) : Set.of();

        // Client-only IDs: reported by clients but not in the server registry
        Set<Identifier> clientOnlyIds = new LinkedHashSet<>();
        for (Identifier id : clientIds) {
            if (!defs.containsKey(id)) {
                clientOnlyIds.add(id);
            }
        }

        int total = defs.size() + clientOnlyIds.size();
        if (total == 0) {
            source.sendFeedback(() -> Text.literal("§eNo halo definitions loaded."), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendFeedback(() -> Text.literal("§aLoaded halo definitions (" + total + "):"), false);
        for (Identifier id : defs.keySet()) {
            source.sendFeedback(() -> Text.literal("  §7- §f" + id), false);
        }
        for (Identifier id : clientOnlyIds) {
            if (myDefs.contains(id)) {
                source.sendFeedback(() -> Text.literal("  §7- §a" + id + " §8(client-side, installed locally)"), false);
            } else {
                source.sendFeedback(() -> Text.literal("  §7- §d" + id + " §8(client-side)"), false);
            }
        }
        return total;
    }

    /**
     * /halo dump — detailed dump of all loaded definitions with shape/animation/damping info.
     */
    private static int dumpDefinitions(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        Map<Identifier, HaloDefinition> defs = HaloJsonLoader.getDefinitions();

        UUID playerUuid = source.getPlayer() != null ? source.getPlayer().getUuid() : null;
        Set<Identifier> myDefs = playerUuid != null
            ? HaloJsonLoader.getClientReportedDefs(playerUuid) : Set.of();

        if (defs.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§eNo halo definitions loaded. Run §f/reload§e first."), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendFeedback(() -> Text.literal("§a=== Loaded Halo Definitions (" + defs.size() + ") ==="), false);
        for (HaloDefinition def : defs.values()) {
            source.sendFeedback(() -> Text.literal(
                "§7  - §f" + def.id() +
                    " §8v=§7" + def.schemaVersion() +
                    " §8layers=§7" + def.model().groups().size() +
                    " §8anim=§7" + (def.animation().isPresent() ? "yes" : "no") +
                    " §8damping=§7k=" + def.damping().linearFactor()
            ), false);
        }

        // Also show client-side-only definitions currently known
        Set<Identifier> clientIds = HaloJsonLoader.getClientReportedDefIds();
        Set<Identifier> clientOnlyIds = new LinkedHashSet<>();
        for (Identifier id : clientIds) {
            if (!defs.containsKey(id)) {
                clientOnlyIds.add(id);
            }
        }
        if (!clientOnlyIds.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§d=== Client-side definitions in use (" + clientOnlyIds.size() + ") ===\n"
                + "§8(JSON not installed on server — provided by client resource packs)"), false);
            for (Identifier id : clientOnlyIds) {
                if (myDefs.contains(id)) {
                    source.sendFeedback(() -> Text.literal("  §7- §a" + id + " §8(installed locally)"), false);
                } else {
                    source.sendFeedback(() -> Text.literal("  §7- §d" + id), false);
                }
            }
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
     *
     * <p>The server accepts any valid {@link Identifier} — it does not require
     * the definition JSON to be installed locally.  Clients are responsible for
     * providing the actual halo definition via their own resource packs.</p>
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

        Identifier defId = network.azusake.halo.platform.PlatformTypes.core(IdentifierArgumentType.getIdentifier(ctx, "definition"));

        // (no namespace fallback — the server is a thin authority that accepts any
        // valid identifier; the namespace comes directly from tab-completion)

        // Capture a final copy for use in lambdas below
        final Identifier resolvedId = defId;

        HaloManager.getInstance().showHaloOn(living, resolvedId);

        source.sendFeedback(() -> Text.literal("§aHalo §f" + resolvedId + "§a shown on §f" + living.getDisplayName().getString()), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Provides tab-completion suggestions for halo definition identifiers.
     *
     * <p>Two matching strategies are used:
     * <ol>
     *   <li><b>Full prefix match:</b> the definition ID starts with the remaining
     *       text (e.g., "halo:rin" matches "halo:ring_default")</li>
     *   <li><b>Path-only match:</b> when the user omits the namespace, the
     *       definition's path starts with the remaining text
     *       (e.g., "ring" matches "halo:ring_default", "sh" matches
     *       "abydos:shiroko")</li>
     * </ol>
     *
     * @param ctx     the command context (unused)
     * @param builder the suggestions builder
     * @return a future resolving to the filtered suggestions
     */
    private static CompletableFuture<Suggestions> suggestDefinitions(
        CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemaining().toLowerCase();
        Set<Identifier> allIds = HaloJsonLoader.getAllKnownDefinitionIds();

        for (Identifier id : allIds) {
            suggestIfMatch(builder, id, remaining);
        }

        return builder.buildFuture();
    }

    private static void suggestIfMatch(SuggestionsBuilder builder, Identifier id, String remaining) {
        if (HaloIdMatcher.matches(id, remaining)) {
            builder.suggest(id.toString());
        }
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
     * /halo save — trigger a world save so the halo ownership record
     * (HaloWorldSaveData) is flushed to disk.
     *
     * <p>The world-level state is maintained incrementally by {@code /halo show}
     * and {@code /halo hide}; this command simply forces a save-all (useful when
     * cheats are not enabled), it does not rebuild the record from the runtime map.</p>
     */
    private static int saveHaloData(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        MinecraftServer server = source.getServer();

        // Touch the persistent state so it is (re)marked dirty, then save-all.
        ServerWorld overworld = server.getOverworld();
        if (overworld != null) {
            HaloWorldSaveData.get(overworld);
        }

        // Trigger save-all so the ownership record and entity NBT are written
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

            boolean persisted = HaloWorldSaveData.get(server.getOverworld()).contains(uuid);
            boolean teleporting = EntityHaloTracker.isTeleporting(uuid);
            long ageMs = System.currentTimeMillis() - instance.getCreatedAtTime();

            String status = instance.isActive() ? "§aactive" : "§cdead";
            String nbt = persisted ? "§a✓persist" : "§c✗persist";
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

            // Check the world-level ownership record for a stale entry
            // (e.g. the owning entity died permanently and was not pruned).
            MinecraftServer server = source.getServer();
            ServerWorld overworld = server.getOverworld();
            Identifier persistedDef = overworld != null
                ? HaloWorldSaveData.get(overworld).get(uuid)
                : null;
            if (persistedDef != null) {
                source.sendFeedback(
                    () -> Text.literal("  §8Persist:    §eStale ownership found §7(def=" + persistedDef + ")"),
                    false
                );
            } else {
                source.sendFeedback(
                    () -> Text.literal("  §8Persist:    §7No halo ownership"), false
                );
            }
            return Command.SINGLE_SUCCESS;
        }

        // --- Instance exists ---
        boolean isActive = instance.isActive();
        long ageMs = System.currentTimeMillis() - instance.getCreatedAtTime();
        boolean persisted = HaloWorldSaveData.get(source.getServer().getOverworld()).contains(uuid);
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

        // World-save ownership persistence
        source.sendFeedback(() -> Text.literal(
            "  §8Persist:   " + (persisted ? "§a✓ persisted" : "§c✗ not persisted")), false
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

        HaloManager.getInstance().publishConfig();
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

        if (!config.setNumber(param, value)) {
            ctx.getSource().sendError(Text.literal("Unknown config parameter: " + param));
            return 0;
        }

        HaloManager.getInstance().publishConfig();
        ctx.getSource().sendFeedback(
            () -> Text.literal("§aSet §f" + param + "§a to §f" + value),
            true
        );
        return Command.SINGLE_SUCCESS;
    }

    /**
     * /halo config &lt;param&gt; &lt;bool&gt; — set a boolean runtime configuration value.
     */
    private static int configSetBool(CommandContext<ServerCommandSource> ctx, String param, boolean value) {
        HaloConfig config = HaloManager.getInstance().getConfig();

        switch (param) {
            case "allow-angular-momentum" -> config.setAllowAngularMomentum(value);
            default -> {
                ctx.getSource().sendError(Text.literal("Unknown config parameter: " + param));
                return 0;
            }
        }

        HaloManager.getInstance().publishConfig();
        ctx.getSource().sendFeedback(
            () -> Text.literal("§aSet §f" + param + "§a to §f" + value),
            true
        );
        return Command.SINGLE_SUCCESS;
    }
}
