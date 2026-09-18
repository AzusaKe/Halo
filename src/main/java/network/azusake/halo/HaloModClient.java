package network.azusake.halo;

import network.azusake.halo.anchor.AnchorCaptureCoordinator;
import network.azusake.halo.client.ForgeHaloCommandInterceptor;
import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.client.HaloScepterClientInput;
import network.azusake.halo.compat.emf.EmfCompatChatNotifier;
import network.azusake.halo.network.HaloNetworkClient;
import network.azusake.halo.render.HaloClientManager;
import network.azusake.halo.render.HaloRenderListener;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Physical-client initialization; never loaded on a dedicated server. */
public final class HaloModClient {
    public static final Logger LOGGER = LoggerFactory.getLogger(HaloMod.MOD_ID);
    private HaloModClient() {}

    public static void init(IEventBus modBus) {
        LOGGER.info("Halo client initializing...");
        network.azusake.halo.platform.IntegratedBridge.config = snapshot ->
            Minecraft.getInstance().execute(() ->
                network.azusake.halo.platform.HaloClientState.get().setConfig(snapshot.toConfig()));
        network.azusake.halo.platform.IntegratedBridge.teleport = uuid ->
            Minecraft.getInstance().execute(() ->
                network.azusake.halo.platform.HaloClientState.get().teleport(uuid));

        EmfCompatChatNotifier.register();
        HaloPhaseTracker.getInstance();
        network.azusake.halo.json.HaloJsonLoader.registerClientResources(modBus);
        network.azusake.halo.json.EntityAnchorLoader.registerClientResources(modBus);
        network.azusake.halo.render.HaloMeshShader.register(modBus);
        HaloRenderListener.register();
        HaloClientManager.getInstance();
        new ForgeHaloCommandInterceptor().register();
        HaloNetworkClient.registerReceivers();

        MinecraftForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> {
            network.azusake.halo.render.PlayerPreviewRenderer.clearAutomaticViews();
            network.azusake.halo.render.HaloRenderer.getInstance().shutdown();
        });
        modBus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(
            new SimplePreparableReloadListener<Void>() {
                @Override protected Void prepare(ResourceManager manager, ProfilerFiller profiler) { return null; }
                @Override protected void apply(Void ignored, ResourceManager manager, ProfilerFiller profiler) {
                    Minecraft.getInstance().execute(HaloNetworkClient::sendDefsReport);
                }
            }));

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END) return;
            network.azusake.halo.render.HaloMeshResources.refreshDefinitions();
            HaloScepterClientInput.tick(Minecraft.getInstance());
        });
        MinecraftForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) -> {
            if (!(event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel)) return;
            var entity = event.getEntity();
            var runtime = network.azusake.halo.platform.HaloClientState.get();
            if (entity instanceof net.minecraft.world.entity.LivingEntity living && !living.isAlive()) {
                runtime.died(entity.getUUID(), entity instanceof net.minecraft.world.entity.player.Player);
            } else {
                runtime.unload(entity.getUUID());
            }
            AnchorCaptureCoordinator.clearEntity(entity.getUUID());
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            HaloPhaseTracker.getInstance().resetToLocal();
            HaloNetworkClient.sendDefsReport();
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            network.azusake.halo.render.PlayerPreviewRenderer.clearAutomaticViews();
            network.azusake.halo.platform.HaloClientState.get().clearAllClientHalos();
            network.azusake.halo.platform.IntegratedBridge.clearDiagnostics();
            AnchorCaptureCoordinator.clearCaptures();
            network.azusake.halo.render.HaloRenderer.getInstance().clearWorld();
            HaloPhaseTracker.getInstance().resetToLocal();
        });
        LOGGER.info("Halo client initialized");
    }
}
