package network.azusake.halo;

import network.azusake.halo.command.HaloConfigCommand;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.item.HaloItems;
import network.azusake.halo.json.EntityAnchorLoader;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.lifecycle.EntityHaloTracker;
import network.azusake.halo.network.HaloNetwork;
import network.azusake.halo.server.HaloServerEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(HaloMod.MOD_ID)
public final class HaloMod {
    public static final String MOD_ID = "halo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public HaloMod() {
        LOGGER.info("Halo mod initializing...");
        network.azusake.halo.core.Diagnostics.setSink((level, message, args) -> {
            switch (level) {
                case "warn" -> LOGGER.warn(message, args);
                case "info" -> LOGGER.info(message, args);
                case "trace" -> LOGGER.trace(message, args);
                default -> LOGGER.debug(message, args);
            }
        });

        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
            () -> new IExtensionPoint.DisplayTest(
                () -> NetworkConstants.IGNORESERVERONLY,
                (remoteVersion, isServer) -> true));

        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        HaloJsonLoader.register();
        EntityAnchorLoader.register();
        HaloServerEvents.registerAll();
        EntityHaloTracker.register();
        HaloModConfigStore.load();
        network.azusake.halo.config.HaloSourcePriorityStore.load();
        HaloItems.register(modBus);
        HaloNetwork.register();

        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
            HaloConfigCommand.register(event.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent event) ->
            network.azusake.halo.manager.HaloManager.getInstance().bind(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            network.azusake.halo.manager.HaloManager.getInstance().stop();
            EntityHaloTracker.clear();
            network.azusake.halo.data.HaloEntityData.clear();
            HaloJsonLoader.clearServerResources();
            EntityAnchorLoader.clearServerResources();
        });

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HaloModClient.init(modBus));
        LOGGER.info("Halo mod initialized");
    }
}
