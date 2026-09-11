package network.azusake.halo;

import network.azusake.halo.anchor.AnchorCaptureCoordinator;
import network.azusake.halo.client.FabricHaloCommandInterceptor;
import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.client.HaloScepterClientInput;
import network.azusake.halo.compat.emf.EmfCompatChatNotifier;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.network.HaloNetworkClient;
import network.azusake.halo.render.HaloClientManager;
import network.azusake.halo.render.HaloRenderListener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HaloModClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(HaloMod.MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Halo client initializing...");
        EmfCompatChatNotifier.register();
        HaloPhaseTracker.getInstance();
        HaloJsonLoader.registerClientResources();
        network.azusake.halo.json.EntityAnchorLoader.registerClientResources();
        HaloRenderListener.register();
        HaloClientManager.getInstance();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            HaloClientManager.getInstance().updateEntityStateCache();
            HaloScepterClientInput.tick(client);
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity != null) {
                HaloClientManager.getInstance().onEntityUnloaded(entity.getUUID());
                AnchorCaptureCoordinator.clearEntity(entity.getUUID());
            }
        });
        new FabricHaloCommandInterceptor().register();
        HaloNetworkClient.registerReceivers();
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            new SimpleSynchronousResourceReloadListener() {
                @Override public Identifier getFabricId() {
                    return Identifier.fromNamespaceAndPath(HaloMod.MOD_ID, "defs_report_trigger");
                }
                @Override public void onResourceManagerReload(ResourceManager manager) {
                    net.minecraft.client.Minecraft.getInstance().execute(HaloNetworkClient::sendDefsReport);
                }
            });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            HaloPhaseTracker.getInstance().resetToLocal();
            HaloNetworkClient.sendDefsReport();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            network.azusake.halo.manager.HaloManager.getInstance().clearAllClientHalos();
            AnchorCaptureCoordinator.clearCaptures();
            HaloPhaseTracker.getInstance().resetToLocal();
        });
        LOGGER.info("Halo client initialized");
    }
}
