package network.azusake.halo;

import network.azusake.halo.command.HaloConfigCommand;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.json.EntityAnchorLoader;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.lifecycle.EntityHaloTracker;
import network.azusake.halo.item.HaloItems;
import network.azusake.halo.network.HaloNetwork;
import network.azusake.halo.server.HaloServerEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(HaloMod.MOD_ID)
public class HaloMod {
    public static final String MOD_ID = "halo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public HaloMod() {
        LOGGER.info("Halo mod initializing...");
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
            () -> new IExtensionPoint.DisplayTest(
                () -> NetworkConstants.IGNORESERVERONLY,
                (remoteVersion, isServer) -> true));
        HaloJsonLoader.register();
        EntityAnchorLoader.register();
        HaloServerEvents.registerAll();
        EntityHaloTracker.register();
        HaloModConfigStore.load();
        HaloItems.register(FMLJavaModLoadingContext.get().getModEventBus());
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
            HaloConfigCommand.register(event.getDispatcher()));
        HaloNetwork.register();
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> HaloModClient::init);
        LOGGER.info("Halo mod initialized");
    }
}
