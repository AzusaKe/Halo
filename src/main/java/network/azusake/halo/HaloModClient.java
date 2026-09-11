package network.azusake.halo;

import network.azusake.halo.anchor.AnchorCaptureCoordinator;
import network.azusake.halo.client.ForgeHaloCommandInterceptor;
import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.client.HaloScepterClientInput;
import network.azusake.halo.compat.emf.EmfCompatChatNotifier;
import network.azusake.halo.json.EntityAnchorLoader;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.network.HaloNetworkClient;
import network.azusake.halo.render.HaloClientManager;
import network.azusake.halo.render.HaloRenderListener;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Physical-client initialization; never loaded on a dedicated server. */
public final class HaloModClient {
    private HaloModClient() {
    }

    public static void init() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        EmfCompatChatNotifier.register();
        HaloPhaseTracker.getInstance();
        HaloJsonLoader.registerClientResources(modBus);
        EntityAnchorLoader.registerClientResources(modBus);

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                HaloClientManager.getInstance().updateEntityStateCache();
                HaloScepterClientInput.tick(Minecraft.getInstance());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) -> {
            if (event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel) {
                HaloClientManager.getInstance().onEntityUnloaded(event.getEntity().getUUID());
                AnchorCaptureCoordinator.clearEntity(event.getEntity().getUUID());
            }
        });
        HaloRenderListener.register();
        HaloClientManager.getInstance();
        new ForgeHaloCommandInterceptor().register();
        HaloNetworkClient.registerReceivers();
        modBus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(
            new SimplePreparableReloadListener<Void>() {
                @Override
                protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                    return null;
                }

                @Override
                protected void apply(Void ignored, ResourceManager manager, ProfilerFiller profiler) {
                    Minecraft.getInstance().execute(HaloNetworkClient::sendDefsReport);
                }
            }));
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            HaloPhaseTracker.getInstance().resetToLocal();
            HaloNetworkClient.sendDefsReport();
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            HaloManager.getInstance().clearAllClientHalos();
            AnchorCaptureCoordinator.clearCaptures();
            HaloPhaseTracker.getInstance().resetToLocal();
        });
    }
}
