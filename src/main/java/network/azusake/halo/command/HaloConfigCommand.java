package network.azusake.halo.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import network.azusake.halo.config.HaloConfig;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.data.HaloDefinition;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.lifecycle.EntityHaloTracker;
import network.azusake.halo.lifecycle.HaloWorldSaveData;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.util.HaloIdMatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Forge/Mojmap server command tree. */
public final class HaloConfigCommand {
    private HaloConfigCommand() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var halo = Commands.literal("halo").requires(s -> s.hasPermission(HaloModConfigStore.getPermissionLevel()));
        halo.then(Commands.literal("list").executes(HaloConfigCommand::list));
        halo.then(Commands.literal("dump").executes(HaloConfigCommand::dump));
        halo.then(Commands.literal("reload").executes(c -> feedback(c, "§eUse §f/reload§e to reload halo definitions.")));
        halo.then(Commands.literal("show").then(Commands.argument("target", EntityArgument.entity())
            .then(Commands.argument("definition", ResourceLocationArgument.id()).suggests(HaloConfigCommand::suggest)
                .executes(HaloConfigCommand::show))));
        halo.then(Commands.literal("hide").then(Commands.argument("target", EntityArgument.entity()).executes(HaloConfigCommand::hide)));
        halo.then(Commands.literal("active").executes(HaloConfigCommand::active));
        halo.then(Commands.literal("save").executes(HaloConfigCommand::save));
        halo.then(Commands.literal("inspect").then(Commands.argument("target", EntityArgument.entity()).executes(HaloConfigCommand::inspect)));
        halo.then(Commands.literal("debug").then(Commands.argument("enabled", BoolArgumentType.bool()).executes(HaloConfigCommand::debug)));
        var config = Commands.literal("config");
        for (String name : new String[]{"linear-damping","angular-damping","max-linear-distance","max-angular-degrees","scale","angular-momentum-factor","max-angular-momentum-degrees"})
            config.then(Commands.literal(name).then(Commands.argument("value", DoubleArgumentType.doubleArg(0)).executes(c -> setConfig(c,name))));
        config.then(Commands.literal("allow-angular-momentum").then(Commands.argument("value", BoolArgumentType.bool()).executes(c -> setBool(c,"allow-angular-momentum"))));
        halo.then(config);
        dispatcher.register(halo);
    }
    private static int feedback(CommandContext<CommandSourceStack> c, String text) { c.getSource().sendSuccess(() -> Component.literal(text), false); return Command.SINGLE_SUCCESS; }
    private static int list(CommandContext<CommandSourceStack> c) {
        Set<ResourceLocation> ids = HaloJsonLoader.getAllKnownDefinitionIds();
        c.getSource().sendSuccess(() -> Component.literal("§aLoaded halo definitions ("+ids.size()+")"), false);
        ids.forEach(id -> c.getSource().sendSuccess(() -> Component.literal("  §7- §f"+id), false)); return ids.size();
    }
    private static int dump(CommandContext<CommandSourceStack> c) { return list(c); }
    private static int show(CommandContext<CommandSourceStack> c) {
        try { Entity e=EntityArgument.getEntity(c,"target"); if (!(e instanceof LivingEntity living)) return feedback(c,"§cTarget must be living.");
            ResourceLocation id=ResourceLocationArgument.getId(c,"definition"); HaloManager.getInstance().showHaloOn(living,id); return feedback(c,"§aHalo shown: §f"+id); }
        catch (Exception e) { return feedback(c,"§cInvalid entity selector."); }
    }
    private static int hide(CommandContext<CommandSourceStack> c) {
        try { Entity e=EntityArgument.getEntity(c,"target"); if (!(e instanceof LivingEntity living)) return feedback(c,"§cTarget must be living."); HaloManager.getInstance().hideHaloOn(living); return feedback(c,"§aHalo hidden."); }
        catch (Exception e) { return feedback(c,"§cInvalid entity selector."); }
    }
    private static int active(CommandContext<CommandSourceStack> c) {
        var map=HaloManager.getInstance().getActiveHalos(); feedback(c,"§aActive halos (§f"+map.size()+"§a):"); map.forEach((u,i)->feedback(c,"  §7- §f"+u+" §8def=§7"+i.getDefinitionId())); return map.size();
    }
    private static int save(CommandContext<CommandSourceStack> c) { c.getSource().getServer().saveEverything(true,true,true); return feedback(c,"§aWorld saved."); }
    private static int inspect(CommandContext<CommandSourceStack> c) { try { LivingEntity e=(LivingEntity)EntityArgument.getEntity(c,"target"); HaloInstance i=HaloManager.getInstance().getHaloInstance(e.getUUID()); return feedback(c,i==null?"§eNo halo attached":"§aHalo: §f"+i.getDefinitionId()); } catch(Exception e){return feedback(c,"§cInvalid entity selector.");} }
    private static int debug(CommandContext<CommandSourceStack> c) { EntityHaloTracker.setDebugMode(BoolArgumentType.getBool(c,"enabled")); return Command.SINGLE_SUCCESS; }
    private static int setConfig(CommandContext<CommandSourceStack> c,String key) { double v=DoubleArgumentType.getDouble(c,"value"); HaloConfig cfg=HaloManager.getInstance().getConfig(); switch(key){case "linear-damping"->cfg.setLinearDampingFactor(v);case "angular-damping"->cfg.setAngularDampingFactor(v);case "max-linear-distance"->cfg.setMaxLinearDistance(v);case "max-angular-degrees"->cfg.setMaxAngularDegrees(v);case "scale"->cfg.setHaloScale(v);case "angular-momentum-factor"->cfg.setAngularMomentumFactor(v);case "max-angular-momentum-degrees"->cfg.setMaxAngularMomentumDegrees(v);} return feedback(c,"§aSet §f"+key+"§a to §f"+v); }
    private static int setBool(CommandContext<CommandSourceStack> c,String key) { boolean v=BoolArgumentType.getBool(c,"value"); HaloManager.getInstance().getConfig().setAllowAngularMomentum(v); return feedback(c,"§aSet §f"+key+"§a to §f"+v); }
    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> c,SuggestionsBuilder b) { String r=b.getRemaining(); for(ResourceLocation id:HaloJsonLoader.getAllKnownDefinitionIds()) if(HaloIdMatcher.matches(id,r)) b.suggest(id.toString()); return b.buildFuture(); }
}
