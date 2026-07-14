package network.azusake.halo;

import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.network.HaloNetworkClient;
import network.azusake.halo.render.HaloClientManager;
import network.azusake.halo.render.HaloRenderListener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HaloModClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(HaloMod.MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Halo client initializing...");

        // Register halo-definition resource loader on the client side so
        // definitions are available for rendering in single-player and when
        // definitions are bundled in a client resource pack.
        HaloJsonLoader.registerClientResources();

        // Register entity-anchor profile loader on the client side
        network.azusake.halo.json.EntityAnchorLoader.registerClientResources();

        // Register the halo renderer with Fabric's world-render pipeline
        HaloRenderListener.register();

        // Initialise the client-side halo visibility manager
        HaloClientManager.getInstance();

        // Clean up entity cache when entities are unloaded from the client world
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity != null) {
                HaloClientManager.getInstance().onEntityUnloaded(entity.getUuid());
            }
        });

        // Register networking packet receivers for multiplayer halo sync.
        // These write directly into HaloManager so the existing single-player
        // rendering pipeline works unchanged on dedicated-server clients.
        HaloNetworkClient.registerReceivers();

        // Send local definition IDs to the server on join and on resource reloads.
        // This listener fires on the initial load cycle AND every /reload, so it
        // covers both bootstrap and incremental updates.  The sendDefsReport()
        // method safely no-ops when not connected to a server world.
        ResourceManagerHelper
            .get(ResourceType.CLIENT_RESOURCES)
            .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                @Override
                public Identifier getFabricId() {
                    return new Identifier(HaloMod.MOD_ID, "defs_report_trigger");
                }
                @Override
                public void reload(ResourceManager manager) {
                    HaloNetworkClient.sendDefsReport();
                }
            });

        // Clear network-populated halo state when disconnecting from a server
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            network.azusake.halo.manager.HaloManager.getInstance().getActiveHalos().clear();
        });

        LOGGER.info("Halo client initialized");
    }
}
