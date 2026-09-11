package network.azusake.halo.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
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

    public static void open(ServerPlayer player, Entity requestedTarget) {
        if (!hasPermission(player)) {
            feedback(player, "message.halo.halo_scepter.no_permission",
                HaloModConfigStore.getPermissionLevel());
            return;
        }
        if (!isHoldingScepter(player)) {
            feedback(player, "message.halo.halo_scepter.not_holding");
            return;
        }
        if (!(requestedTarget instanceof LivingEntity target) || !target.isAlive()) {
            feedback(player, "message.halo.halo_scepter.invalid_target");
            return;
        }

        SESSIONS.open(
            player.getUUID(),
            target.getUUID(),
            target.level().dimension().identifier()
        );
        HaloNetwork.sendScepterOpen(player, target);
    }

    public static void select(ServerPlayer player, Identifier definitionId) {
        HaloScepterSessionStore.Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            feedback(player, "message.halo.halo_scepter.session_expired");
            HaloNetwork.sendScepterClose(player);
            return;
        }
        if (!hasPermission(player)) {
            feedback(player, "message.halo.halo_scepter.no_permission",
                HaloModConfigStore.getPermissionLevel());
            close(player.getUUID());
            HaloNetwork.sendScepterClose(player);
            return;
        }
        if (!isHoldingScepter(player)) {
            feedback(player, "message.halo.halo_scepter.not_holding");
            close(player.getUUID());
            HaloNetwork.sendScepterClose(player);
            return;
        }

        LivingEntity target = resolveTarget(player.level().getServer(), session);
        if (target == null) {
            feedback(player, "message.halo.halo_scepter.invalid_target");
            close(player.getUUID());
            HaloNetwork.sendScepterClose(player);
            return;
        }

        HaloManager.getInstance().showHaloOn(target, definitionId);
        feedback(player, "message.halo.halo_scepter.applied", definitionId.toString(), target.getDisplayName());
    }

    public static void remove(ServerPlayer player, Entity requestedTarget, boolean selfTarget) {
        if (!hasPermission(player)) {
            feedback(player, "message.halo.halo_scepter.no_permission",
                HaloModConfigStore.getPermissionLevel());
            return;
        }
        if (!player.getMainHandItem().is(HaloItems.HALO_SCEPTER)) {
            feedback(player, "message.halo.halo_scepter.not_holding");
            return;
        }

        Entity resolved = selfTarget ? player : requestedTarget;
        if (!(resolved instanceof LivingEntity target) || !target.isAlive()) {
            feedback(player, "message.halo.halo_scepter.invalid_target");
            return;
        }
        if (!selfTarget && player.distanceToSqr(target) > MAX_DIRECT_TARGET_DISTANCE_SQUARED) {
            feedback(player, "message.halo.halo_scepter.too_far");
            return;
        }
        if (HaloManager.getInstance().getHaloInstance(target.getUUID()) == null) {
            feedback(player, "message.halo.halo_scepter.no_halo", target.getDisplayName());
            return;
        }

        HaloManager.getInstance().hideHaloOn(target);
        feedback(player, "message.halo.halo_scepter.removed", target.getDisplayName());
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
            boolean valid = player != null
                && player.isAlive()
                && player.level().dimension().identifier().equals(session.worldId())
                && target != null;
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
        ResourceKey<Level> worldKey = ResourceKey.create(Registries.DIMENSION, session.worldId());
        ServerLevel world = server.getLevel(worldKey);
        if (world == null) {
            return null;
        }
        Entity entity = world.getEntity(session.targetUuid());
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    private static boolean hasPermission(ServerPlayer player) {
        return player.permissions().hasPermission(
            new Permission.HasCommandLevel(
                PermissionLevel.byId(HaloModConfigStore.getPermissionLevel())
            )
        );
    }

    private static boolean isHoldingScepter(ServerPlayer player) {
        return player.getMainHandItem().is(HaloItems.HALO_SCEPTER)
            || player.getOffhandItem().is(HaloItems.HALO_SCEPTER);
    }

    private static void feedback(ServerPlayer player, String key, Object... args) {
        player.sendOverlayMessage(Component.translatable(key, args));
    }
}
