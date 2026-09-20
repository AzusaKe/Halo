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

import java.util.UUID;

/**
 * Central registry for all server-side NeoForge event callbacks.
 *
 * <p>Each static {@code register()} method wires one category of events.
 * Call {@link #registerAll()} from the mod constructor to
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
        network.azusake.halo.physics.HaloTickHandler.register();
        registerEntityEvents();
        registerConnectionEvents();
        HaloMod.LOGGER.info("HaloServerEvents: all server event handlers registered");
    }

    // ---------------------------------------------------------------
    // Per-category registrations (package-private for test visibility)
    // ---------------------------------------------------------------

    static void registerEntityEvents() {
        NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) -> {
            if (!(event.getLevel() instanceof ServerLevel world)) return;
            var entity = event.getEntity();
            HaloMod.LOGGER.debug(
                "HaloServerEvents: entity unloaded – uuid={}, type={}",
                entity.getUUID(), entity.getType().getDescription().getString()
            );
            HaloManager.getInstance().entityUnloaded(entity);
            HaloScepterService.invalidateTarget(world.getServer(), entity.getUUID());
        });
    }

    static void registerConnectionEvents() {
        // Player join → send full halo state snapshot
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            HaloMod.LOGGER.debug(
                "HaloServerEvents: player joined – uuid={}, name={}",
                player.getUUID(), player.getName().getString()
            );
            network.azusake.halo.network.HaloNetwork.sendFullSync(player);
            network.azusake.halo.network.HaloNetwork.sendHello(player);
            HaloManager.getInstance().notifyPriorityConflicts(player);
        });

        // Player disconnect → clear runtime halo and reported definitions.
        // Note: this does NOT touch the world-level ownership record
        // (HaloWorldSaveData) — a player keeps their halo across a reconnect,
        // just as they keep it across a respawn.  Only /halo show and /halo hide
        // may modify ownership.
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
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
