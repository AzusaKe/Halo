package com.example.halo.server;

import com.example.halo.HaloMod;
import com.example.halo.manager.HaloManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Central registry for all server-side Fabric API event callbacks.
 *
 * <p>Each static {@code register()} method wires one category of events.
 * Call {@link #registerAll()} from {@link HaloMod#onInitialize()} to
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
        ServerTickEvents.END_SERVER_TICK.register(tickHandler);
        HaloMod.LOGGER.debug("HaloServerEvents: ServerTickHandler registered");
    }

    static void registerEntityEvents() {
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            HaloMod.LOGGER.debug(
                "HaloServerEvents: entity unloaded – uuid={}, type={}",
                entity.getUuid(), entity.getType().getName().getString()
            );
            HaloManager.getInstance().removeHalo(entity.getUuid());
        });
    }

    static void registerConnectionEvents() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            HaloMod.LOGGER.debug(
                "HaloServerEvents: player disconnected – uuid={}, name={}",
                handler.getPlayer().getUuid(), handler.getPlayer().getName().getString()
            );
            HaloManager.getInstance().removeHalo(handler.getPlayer().getUuid());
        });
    }
}
