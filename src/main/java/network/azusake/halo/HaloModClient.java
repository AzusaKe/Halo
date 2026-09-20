package network.azusake.halo;

import network.azusake.halo.anchor.AnchorCaptureCoordinator;
import network.azusake.halo.client.NeoForgeHaloCommandInterceptor;
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
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.bus.api.IEventBus;
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
        HaloRenderListener.register();
        HaloClientManager.getInstance();
        new NeoForgeHaloCommandInterceptor().register();

        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> {
            network.azusake.halo.render.PlayerPreviewRenderer.clearAutomaticViews();
            network.azusake.halo.render.HaloRenderer.getInstance().shutdown();
        });
        modBus.addListener((AddClientReloadListenersEvent event) -> event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath("halo", "defs_report_trigger"),
            new SimplePreparableReloadListener<Void>() {
                @Override protected Void prepare(ResourceManager manager, ProfilerFiller profiler) { return null; }
                @Override protected void apply(Void ignored, ResourceManager manager, ProfilerFiller profiler) {
                    Minecraft.getInstance().execute(HaloNetworkClient::sendDefsReport);
                }
            }));

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            network.azusake.halo.render.HaloMeshResources.refreshDefinitions();
            HaloScepterClientInput.tick(Minecraft.getInstance());
        });
        NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) -> {
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
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            HaloPhaseTracker.getInstance().resetToLocal();
            HaloNetworkClient.sendDefsReport();
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
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
