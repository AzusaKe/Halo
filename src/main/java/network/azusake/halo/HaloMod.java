package network.azusake.halo;

import network.azusake.halo.command.HaloConfigCommand;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.lifecycle.EntityHaloTracker;
import network.azusake.halo.item.HaloItems;
import network.azusake.halo.server.HaloServerEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HaloMod implements ModInitializer {

    public static final String MOD_ID = "halo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Halo mod initializing...");
        network.azusake.halo.core.Diagnostics.setSink((level, message, args) -> {
            switch (level) {
                case "warn" -> LOGGER.warn(message, args);
                case "info" -> LOGGER.info(message, args);
                case "trace" -> LOGGER.trace(message, args);
                default -> LOGGER.debug(message, args);
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(
            server -> network.azusake.halo.manager.HaloManager.getInstance().bind(server));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            network.azusake.halo.manager.HaloManager.getInstance().stop();
            network.azusake.halo.lifecycle.EntityHaloTracker.clear();
            network.azusake.halo.data.HaloEntityData.clear();
            network.azusake.halo.json.HaloJsonLoader.clearServerResources();
            network.azusake.halo.json.EntityAnchorLoader.clearServerResources();
        });

        // Register resource reload listeners for JSON halo definitions
        HaloJsonLoader.register();

        // Register resource reload listeners for entity anchor profiles
        network.azusake.halo.json.EntityAnchorLoader.register();

        // Register server-side event handlers (tick, entity, connection)
        HaloServerEvents.registerAll();

        // Register entity lifecycle tracker (teleport detection, NBT restore, cleanup)
        EntityHaloTracker.register();

        // Load the file-backed mod config (permission level etc.) before the
        // /halo command tree registers, so the permission gate reads it.
        HaloModConfigStore.load();

        // Register the halo scepter and its server-authoritative interactions.
        HaloItems.register();

        // Register /halo command tree (dump, reload, list, show, hide, config)
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) -> HaloConfigCommand.register(dispatcher)
        );

        // Register networking channels for multiplayer halo synchronisation
        network.azusake.halo.network.HaloNetwork.register();

        LOGGER.info("Halo mod initialized");
    }
}
