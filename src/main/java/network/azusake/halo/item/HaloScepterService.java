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
import net.minecraft.util.Identifier;
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
            player.getUuid(),
            target.getUuid(),
            target.getWorld().getRegistryKey().getValue()
        );
        HaloNetwork.sendScepterOpen(player, target);
    }

    public static void select(ServerPlayerEntity player, Identifier definitionId) {
        HaloScepterSessionStore.Session session = SESSIONS.get(player.getUuid());
        if (session == null) {
            feedback(player, "message.halo.halo_scepter.session_expired");
            HaloNetwork.sendScepterClose(player);
            return;
        }
        if (!hasPermission(player)) {
            feedback(player, "message.halo.halo_scepter.no_permission",
                HaloModConfigStore.getPermissionLevel());
            close(player.getUuid());
            HaloNetwork.sendScepterClose(player);
            return;
        }
        if (!isHoldingScepter(player)) {
            feedback(player, "message.halo.halo_scepter.not_holding");
            close(player.getUuid());
            HaloNetwork.sendScepterClose(player);
            return;
        }

        LivingEntity target = resolveTarget(player.getServer(), session);
        if (target == null) {
            feedback(player, "message.halo.halo_scepter.invalid_target");
            close(player.getUuid());
            HaloNetwork.sendScepterClose(player);
            return;
        }

        HaloManager.getInstance().showHaloOn(target, definitionId);
        feedback(player, "message.halo.halo_scepter.applied", definitionId.toString(), target.getDisplayName());
    }

    public static void remove(ServerPlayerEntity player, Entity requestedTarget, boolean selfTarget) {
        if (!hasPermission(player)) {
            feedback(player, "message.halo.halo_scepter.no_permission",
                HaloModConfigStore.getPermissionLevel());
            return;
        }
        if (!player.getMainHandStack().isOf(HaloItems.HALO_SCEPTER)) {
            feedback(player, "message.halo.halo_scepter.not_holding");
            return;
        }

        Entity resolved = selfTarget ? player : requestedTarget;
        if (!(resolved instanceof LivingEntity target) || !target.isAlive()) {
            feedback(player, "message.halo.halo_scepter.invalid_target");
            return;
        }
        if (!selfTarget && player.squaredDistanceTo(target) > MAX_DIRECT_TARGET_DISTANCE_SQUARED) {
            feedback(player, "message.halo.halo_scepter.too_far");
            return;
        }
        if (HaloManager.getInstance().getHaloInstance(target.getUuid()) == null) {
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
            boolean valid = player != null
                && player.isAlive()
                && player.getWorld().getRegistryKey().getValue().equals(session.worldId())
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
        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, session.worldId());
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
}
