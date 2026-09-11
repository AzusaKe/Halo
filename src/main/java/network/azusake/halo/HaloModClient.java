package network.azusake.halo;

import network.azusake.halo.anchor.AnchorCaptureCoordinator;
import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.client.HaloScepterClientInput;
import network.azusake.halo.client.NeoForgeHaloCommandInterceptor;
import network.azusake.halo.compat.emf.EmfCompatChatNotifier;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.network.HaloNetworkClient;
import network.azusake.halo.render.HaloClientManager;
import network.azusake.halo.render.HaloRenderListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-side initialisation for the NeoForge port. */
public final class HaloModClient {
    public static final Logger LOGGER = LoggerFactory.getLogger(HaloMod.MOD_ID);

    private HaloModClient() {
    }

    public static void init(IEventBus modEventBus) {
        LOGGER.info("Halo client initializing...");
        EmfCompatChatNotifier.register();
        HaloPhaseTracker.getInstance();
        HaloJsonLoader.registerClientResources(modEventBus);
        network.azusake.halo.json.EntityAnchorLoader.registerClientResources(modEventBus);
        HaloRenderListener.register();
        HaloClientManager.getInstance();

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            HaloClientManager.getInstance().updateEntityStateCache();
            HaloScepterClientInput.tick(net.minecraft.client.Minecraft.getInstance());
        });
        NeoForge.EVENT_BUS.addListener(EntityLeaveLevelEvent.class, event -> {
            var entity = event.getEntity();
            if (entity != null) {
                HaloClientManager.getInstance().onEntityUnloaded(entity.getUUID());
                AnchorCaptureCoordinator.clearEntity(entity.getUUID());
            }
        });

        new NeoForgeHaloCommandInterceptor().register();
        modEventBus.addListener(RegisterClientReloadListenersEvent.class, event ->
            event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
                @Override
                protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                    return null;
                }

                @Override
                protected void apply(Void data, ResourceManager manager, ProfilerFiller profiler) {
                    net.minecraft.client.Minecraft.getInstance().execute(HaloNetworkClient::sendDefsReport);
                }
            }));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> {
            HaloPhaseTracker.getInstance().resetToLocal();
            HaloNetworkClient.sendDefsReport();
        });
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            HaloManager.getInstance().clearAllClientHalos();
            AnchorCaptureCoordinator.clearCaptures();
            HaloPhaseTracker.getInstance().resetToLocal();
        });
        LOGGER.info("Halo client initialized");
    }
}
