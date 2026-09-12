package network.azusake.halo.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import net.minecraft.text.Text;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.network.HaloNetwork;

import java.util.UUID;

/** Server-authoritative implementation of all halo-scepter actions. */
public final class HaloScepterService {

    private static final double MAX_DIRECT_TARGET_DISTANCE_SQUARED = 36.0;
    private static final HaloScepterSessionStore SESSIONS = new HaloScepterSessionStore();

    private HaloScepterService() {
    }

    public static void open(ServerPlayerEntity player, Entity requestedTarget) {
        var failure=network.azusake.halo.core.runtime.ScepterPolicy.open(hasPermission(player),isHoldingScepter(player),
            requestedTarget instanceof LivingEntity && requestedTarget.isAlive());
        if(failure!=null){deny(player,failure,requestedTarget);return;}
        LivingEntity target=(LivingEntity)requestedTarget;

        SESSIONS.open(
            player.getUuid(),
            target.getUuid(),
            network.azusake.halo.platform.PlatformTypes.core(target.getWorld().getRegistryKey().getValue())
        );
        HaloNetwork.sendScepterOpen(player, target);
    }

    public static void select(ServerPlayerEntity player, Identifier definitionId) {
        HaloScepterSessionStore.Session session = SESSIONS.get(player.getUuid());
        LivingEntity target=session==null?null:resolveTarget(player.getServer(),session);
        var failure=network.azusake.halo.core.runtime.ScepterPolicy.select(session!=null,hasPermission(player),
            isHoldingScepter(player),target!=null);
        if(failure!=null){deny(player,failure,target);close(player.getUuid());HaloNetwork.sendScepterClose(player);return;}

        HaloManager.getInstance().showHaloOn(target, definitionId);
        feedback(player, "message.halo.halo_scepter.applied", definitionId.toString(), target.getDisplayName());
    }

    public static void remove(ServerPlayerEntity player, Entity requestedTarget, boolean selfTarget) {
        Entity resolved=selfTarget?player:requestedTarget;
        var failure=network.azusake.halo.core.runtime.ScepterPolicy.remove(hasPermission(player),
            player.getMainHandStack().isOf(HaloItems.HALO_SCEPTER),resolved instanceof LivingEntity && resolved.isAlive(),
            selfTarget,resolved==null?Double.POSITIVE_INFINITY:player.squaredDistanceTo(resolved),
            resolved!=null && HaloManager.getInstance().getHaloInstance(resolved.getUuid())!=null);
        if(failure!=null){deny(player,failure,resolved);return;}
        LivingEntity target=(LivingEntity)resolved;

        HaloManager.getInstance().hideHaloOn(target);
        feedback(player, "message.halo.halo_scepter.removed", target.getDisplayName());
    }

    public static void close(UUID playerUuid) {
        SESSIONS.close(playerUuid);
    }

    public static void invalidateTarget(MinecraftServer server, UUID targetUuid) {
        for (HaloScepterSessionStore.Session session : SESSIONS.closeTarget(targetUuid)) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(session.playerUuid());
            if (player != null) {
                HaloNetwork.sendScepterClose(player);
            }
        }
    }

    /** Clear sessions whose player or locked target is no longer valid. */
    public static void tick(MinecraftServer server) {
        for (HaloScepterSessionStore.Session session : SESSIONS.snapshot()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(session.playerUuid());
            LivingEntity target = resolveTarget(server, session);
            boolean valid = network.azusake.halo.core.runtime.ScepterPolicy.sessionValid(
                player!=null && player.isAlive(),player!=null && player.getWorld().getRegistryKey().getValue().toString().equals(session.worldId().toString()),target!=null);
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
        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, network.azusake.halo.platform.PlatformTypes.game(session.worldId()));
        ServerWorld world = server.getWorld(worldKey);
        if (world == null) {
            return null;
        }
        Entity entity = world.getEntity(session.targetUuid());
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    private static boolean hasPermission(ServerPlayerEntity player) {
        return player.hasPermissionLevel(HaloModConfigStore.getPermissionLevel());
    }

    private static boolean isHoldingScepter(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(HaloItems.HALO_SCEPTER)
            || player.getOffHandStack().isOf(HaloItems.HALO_SCEPTER);
    }

    private static void feedback(ServerPlayerEntity player, String key, Object... args) {
        player.sendMessage(Text.translatable(key, args), true);
    }
    private static void deny(ServerPlayerEntity player,network.azusake.halo.core.runtime.ScepterPolicy.Failure failure,Entity target) {
        String key="message.halo.halo_scepter."+failure.name().toLowerCase(java.util.Locale.ROOT);
        if(failure==network.azusake.halo.core.runtime.ScepterPolicy.Failure.NO_PERMISSION)feedback(player,key,HaloModConfigStore.getPermissionLevel());
        else if(failure==network.azusake.halo.core.runtime.ScepterPolicy.Failure.NO_HALO)feedback(player,key,target.getDisplayName());
        else feedback(player,key);
    }
}
