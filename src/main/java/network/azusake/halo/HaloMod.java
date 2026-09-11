package network.azusake.halo;

import network.azusake.halo.command.HaloConfigCommand;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.lifecycle.EntityHaloTracker;
import network.azusake.halo.item.HaloItems;
import network.azusake.halo.network.HaloNetwork;
import network.azusake.halo.server.HaloServerEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(HaloMod.MOD_ID)
public class HaloMod {

    public static final String MOD_ID = "halo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public HaloMod(IEventBus modEventBus) {
        LOGGER.info("Halo mod initializing...");

        // Register resource reload listeners for JSON halo definitions
        HaloJsonLoader.register();

        // Register resource reload listeners for entity anchor profiles
        network.azusake.halo.json.EntityAnchorLoader.register();

        // Register server-side event handlers (tick, entity, connection)
        HaloServerEvents.registerAll();

        // Register per-tick halo physics driver
        network.azusake.halo.physics.HaloTickHandler.register();

        // Register entity lifecycle tracker (teleport detection, NBT restore, cleanup)
        EntityHaloTracker.register();

        // Load the file-backed mod config (permission level etc.) before the
        // /halo command tree registers, so the permission gate reads it.
        HaloModConfigStore.load();

        // Register the halo scepter and its server-authoritative interactions.
        HaloItems.register(modEventBus);

        // Register /halo command tree (dump, reload, list, show, hide, config)
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class,
            event -> HaloConfigCommand.register(event.getDispatcher()));

        // Register networking channels for multiplayer halo synchronisation
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, HaloNetwork::register);

        // Client-side initialisation (physical client only)
        if (FMLEnvironment.dist.isClient()) {
            HaloModClient.init(modEventBus);
        }

        LOGGER.info("Halo mod initialized");
    }
}
