package network.azusake.halo.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.runtime.ScepterPolicy.Failure;
import network.azusake.halo.core.runtime.ServerRuntime.Selection;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.network.HaloNetwork;
import network.azusake.halo.lifecycle.HaloWorldSaveData;

import java.util.UUID;

/** Server-authoritative implementation of all halo-scepter actions. */
public final class HaloScepterService {

    private static final double MAX_DIRECT_TARGET_DISTANCE_SQUARED = 36.0;
    private static final HaloScepterSessionStore SESSIONS = new HaloScepterSessionStore();

    private HaloScepterService() {
    }

    public static void open(ServerPlayer player, Entity requestedTarget) {
        var failure=network.azusake.halo.core.runtime.ScepterPolicy.open(hasPermission(player),isHoldingScepter(player),
            requestedTarget instanceof LivingEntity && requestedTarget.isAlive());
        if(failure!=null){deny(player,failure,requestedTarget);return;}
        LivingEntity target=(LivingEntity)requestedTarget;

        SESSIONS.open(
            player.getUUID(),
            target.getUUID(),
            network.azusake.halo.platform.PlatformTypes.core(target.level().dimension().location())
        );
        HaloNetwork.sendScepterOpen(player, target);
    }

    public static void select(ServerPlayer player, Identifier definitionId) {
        HaloScepterSessionStore.Session session = SESSIONS.get(player.getUUID());
        LivingEntity target=session==null?null:resolveTarget(player.getServer(),session);
        var failure=network.azusake.halo.core.runtime.ScepterPolicy.select(session!=null,hasPermission(player),
            isHoldingScepter(player),target!=null);
        if(failure!=null){deny(player,failure,target);close(player.getUUID());HaloNetwork.sendScepterClose(player);return;}

        HaloManager.getInstance().showHaloOn(target, definitionId);
        feedback(player, "message.halo.halo_scepter.applied", definitionId.toString(), target.getDisplayName());
    }

    public static void remove(ServerPlayer player, Entity requestedTarget, boolean selfTarget) {
        Entity resolved=selfTarget?player:requestedTarget;
        boolean hasWorldData = resolved != null && player.getServer() != null
            && HaloWorldSaveData.get(player.getServer().overworld()).contains(resolved.getUUID());
        var failure=network.azusake.halo.core.runtime.ScepterPolicy.remove(hasPermission(player),
            player.getMainHandItem().is(HaloItems.HALO_SCEPTER.get()),resolved instanceof LivingEntity && resolved.isAlive(),
            selfTarget,resolved==null?Double.POSITIVE_INFINITY:player.distanceToSqr(resolved),
            hasWorldData);
        if(failure!=null){deny(player,failure,resolved);return;}
        LivingEntity target=(LivingEntity)resolved;

        HaloManager.getInstance().hideHaloOn(target);
        feedback(player, "message.halo.halo_scepter.removed", target.getDisplayName());
        var selected = HaloManager.getInstance().selection(target.getUUID());
        if (selected != null) player.displayClientMessage(Component.literal("[Halo] Source '" + selected.sourceId()
            + "' remains selected at priority " + selected.priority()), true);
    }

    public static void close(UUID playerUuid) {
        SESSIONS.close(playerUuid);
    }

    public static void invalidateTarget(MinecraftServer server, UUID targetUuid) {
        for (HaloScepterSessionStore.Session session : SESSIONS.closeTarget(targetUuid)) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerUuid());
            if (player != null) {
                HaloNetwork.sendScepterClose(player);
            }
        }
    }

    /** Clear sessions whose player or locked target is no longer valid. */
    public static void tick(MinecraftServer server) {
        for (HaloScepterSessionStore.Session session : SESSIONS.snapshot()) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerUuid());
            LivingEntity target = resolveTarget(server, session);
            boolean valid = network.azusake.halo.core.runtime.ScepterPolicy.sessionValid(
                player!=null && player.isAlive(),player!=null && player.level().dimension().location().toString().equals(session.worldId().toString()),target!=null);
            if (!valid) {
                SESSIONS.close(session.playerUuid());
                if (player != null) {
                    HaloNetwork.sendScepterClose(player);
                }
            }
        }
    }

    static HaloScepterSessionStore sessionsForTests() {
        return SESSIONS;
    }

    private static LivingEntity resolveTarget(MinecraftServer server, HaloScepterSessionStore.Session session) {
        if (server == null) {
            return null;
        }
        ResourceKey<Level> worldKey = ResourceKey.create(Registries.DIMENSION, network.azusake.halo.platform.PlatformTypes.game(session.worldId()));
        ServerLevel world = server.getLevel(worldKey);
        if (world == null) {
            return null;
        }
        Entity entity = world.getEntity(session.targetUuid());
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    private static boolean hasPermission(ServerPlayer player) {
        return player.hasPermissions(HaloModConfigStore.getPermissionLevel());
    }

    private static boolean isHoldingScepter(ServerPlayer player) {
        return player.getMainHandItem().is(HaloItems.HALO_SCEPTER.get())
            || player.getOffhandItem().is(HaloItems.HALO_SCEPTER.get());
    }

    private static void feedback(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }
    private static void deny(ServerPlayer player,network.azusake.halo.core.runtime.ScepterPolicy.Failure failure,Entity target) {
        String key="message.halo.halo_scepter."+failure.name().toLowerCase(java.util.Locale.ROOT);
        if(failure==network.azusake.halo.core.runtime.ScepterPolicy.Failure.NO_PERMISSION)feedback(player,key,HaloModConfigStore.getPermissionLevel());
        else if(failure==network.azusake.halo.core.runtime.ScepterPolicy.Failure.NO_HALO)feedback(player,key,target.getDisplayName());
        else feedback(player,key);
    }
}
