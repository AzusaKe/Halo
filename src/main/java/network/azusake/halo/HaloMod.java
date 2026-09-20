package network.azusake.halo;

import network.azusake.halo.command.HaloConfigCommand;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.item.HaloItems;
import network.azusake.halo.json.EntityAnchorLoader;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.lifecycle.EntityHaloTracker;
import network.azusake.halo.network.HaloNetwork;
import network.azusake.halo.server.HaloServerEvents;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(HaloMod.MOD_ID)
public final class HaloMod {
    public static final String MOD_ID = "halo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public HaloMod(net.neoforged.bus.api.IEventBus modBus) {
        LOGGER.info("Halo mod initializing...");
        network.azusake.halo.core.Diagnostics.setSink((level, message, args) -> {
            switch (level) {
                case "warn" -> LOGGER.warn(message, args);
                case "info" -> LOGGER.info(message, args);
                case "trace" -> LOGGER.trace(message, args);
                default -> LOGGER.debug(message, args);
            }
        });

        HaloJsonLoader.register();
        EntityAnchorLoader.register();
        HaloServerEvents.registerAll();
        EntityHaloTracker.register();
        HaloModConfigStore.load();
        network.azusake.halo.config.HaloSourcePriorityStore.load();
        HaloItems.register(modBus);
        modBus.addListener(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent.class, HaloNetwork::register);

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
            HaloConfigCommand.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) ->
            network.azusake.halo.manager.HaloManager.getInstance().bind(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            network.azusake.halo.manager.HaloManager.getInstance().stop();
            EntityHaloTracker.clear();
            network.azusake.halo.data.HaloEntityData.clear();
            HaloJsonLoader.clearServerResources();
            EntityAnchorLoader.clearServerResources();
        });

        if (net.neoforged.fml.loading.FMLEnvironment.getDist().isClient()) HaloModClient.init(modBus);
        LOGGER.info("Halo mod initialized");
    }
}
