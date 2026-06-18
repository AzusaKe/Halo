package network.azusake.halo;

import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.render.HaloClientManager;
import network.azusake.halo.render.HaloRenderListener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
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

        LOGGER.info("Halo client initialized");
    }
}
