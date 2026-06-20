package network.azusake.halo;

import network.azusake.halo.command.HaloConfigCommand;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.lifecycle.EntityHaloTracker;
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

        // Register resource reload listeners for JSON halo definitions
        HaloJsonLoader.register();

        // Register server-side event handlers (tick, entity, connection)
        HaloServerEvents.registerAll();

        // Register per-tick halo physics driver
        network.azusake.halo.physics.HaloTickHandler.register();

        // Register entity lifecycle tracker (teleport detection, NBT restore, cleanup)
        EntityHaloTracker.register();

        // Register /halo command tree (dump, reload, list, show, hide, config)
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) -> HaloConfigCommand.register(dispatcher)
        );

        LOGGER.info("Halo mod initialized");
    }
}
