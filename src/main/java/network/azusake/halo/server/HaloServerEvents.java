package network.azusake.halo.server;

import network.azusake.halo.HaloMod;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.item.HaloScepterService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

/**
 * Central registry for all server-side NeoForge event callbacks.
 *
 * <p>Each static {@code register()} method wires one category of events.
 * Call {@link #registerAll()} from {@link HaloMod}'s constructor to
 * activate all server-side behaviour in one shot.</p>
 */
public final class HaloServerEvents {

    private HaloServerEvents() {
        // utility class – no instances
    }

    /**
     * Register every server-side event handler.
     */
    public static void registerAll() {
        registerTickHandler();
        registerEntityEvents();
        registerConnectionEvents();
        HaloMod.LOGGER.info("HaloServerEvents: all server event handlers registered");
    }

    // ---------------------------------------------------------------
    // Per-category registrations (package-private for test visibility)
    // ---------------------------------------------------------------

    static void registerTickHandler() {
        ServerTickHandler tickHandler = new ServerTickHandler();
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
            event -> tickHandler.onEndTick(event.getServer()));
        HaloMod.LOGGER.debug("HaloServerEvents: ServerTickHandler registered");
    }

    static void registerEntityEvents() {
        NeoForge.EVENT_BUS.addListener(EntityLeaveLevelEvent.class, event -> {
            // Only the logical server side unloads are handled here; the
            // client-side unloads are handled by HaloModClient.
            if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
                return;
            }
            var entity = event.getEntity();
            HaloMod.LOGGER.debug(
                "HaloServerEvents: entity unloaded – uuid={}, type={}",
                entity.getUUID(), entity.getType().getDescription().getString()
            );
            HaloManager.getInstance().removeHalo(entity.getUUID(), serverLevel.getServer());
            HaloScepterService.invalidateTarget(serverLevel.getServer(), entity.getUUID());
        });
    }

    static void registerConnectionEvents() {
        // Player join → send full halo state snapshot
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            HaloMod.LOGGER.debug(
                "HaloServerEvents: player joined – uuid={}, name={}",
                player.getUUID(), player.getName().getString()
            );
            network.azusake.halo.network.HaloNetwork.sendFullSync(player);
            network.azusake.halo.network.HaloNetwork.sendHello(player);
        });

        // Player disconnect → clear runtime halo and reported definitions.
        // Note: this does NOT touch the world-level ownership record
        // (HaloWorldSaveData) — a player keeps their halo across a reconnect,
        // just as they keep it across a respawn.  Only /halo show and /halo hide
        // may modify ownership.
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            UUID uuid = player.getUUID();
            HaloMod.LOGGER.debug(
                "HaloServerEvents: player disconnected – uuid={}, name={}",
                uuid, player.getName().getString()
            );
            HaloManager.getInstance().removeHalo(uuid, player.level().getServer());
            HaloJsonLoader.removeClientReportedDefs(uuid);
            HaloScepterService.close(uuid);
        });
    }
}
