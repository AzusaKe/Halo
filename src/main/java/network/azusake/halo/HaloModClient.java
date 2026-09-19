package network.azusake.halo;

import network.azusake.halo.client.FabricHaloCommandInterceptor;
import network.azusake.halo.client.HaloLocalManager;
import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.client.HaloScepterClientInput;
import network.azusake.halo.compat.emf.EmfCompatChatNotifier;
import network.azusake.halo.anchor.AnchorCaptureCoordinator;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.network.HaloNetworkClient;
import network.azusake.halo.render.HaloClientManager;
import network.azusake.halo.render.HaloRenderListener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HaloModClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(HaloMod.MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Halo client initializing...");
        network.azusake.halo.platform.IntegratedBridge.config = snapshot ->
            net.minecraft.client.Minecraft.getInstance().execute(() ->
                network.azusake.halo.platform.HaloClientState.get().setConfig(snapshot.toConfig()));
        network.azusake.halo.platform.IntegratedBridge.teleport = uuid ->
            net.minecraft.client.Minecraft.getInstance().execute(() ->
                network.azusake.halo.platform.HaloClientState.get().teleport(uuid));
        EmfCompatChatNotifier.register();

        // Force initialisation of the phase tracker singleton.  The client
        // starts in LOCAL phase and transitions to MULTIPLAYER when
        // halo:hello is received from a modded server.
        HaloPhaseTracker.getInstance();

        // Register halo-definition resource loader on the client side so
        // definitions are available for rendering in single-player and when
        // definitions are bundled in a client resource pack.
        HaloJsonLoader.registerClientResources();

        // Register entity-anchor profile loader on the client side
        network.azusake.halo.json.EntityAnchorLoader.registerClientResources();

        // Register the halo renderer with Fabric's world-render pipeline
        HaloRenderListener.register();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            network.azusake.halo.render.PlayerPreviewRenderer.clearAutomaticViews();
            network.azusake.halo.render.HaloRenderer.getInstance().shutdown();
        });

        // Initialise the client-side halo visibility manager
        HaloClientManager.getInstance();

        // Input polling belongs to the client tick; scene facts are sampled once per render frame.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            network.azusake.halo.render.HaloMeshResources.refreshDefinitions();
            HaloScepterClientInput.tick(client);
        });

        // Clean up entity cache when entities are unloaded from the client world
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity != null) {
                var runtime = network.azusake.halo.platform.HaloClientState.get();
                if (entity instanceof net.minecraft.world.entity.LivingEntity living && !living.isAlive()) {
                    runtime.died(entity.getUUID(), entity instanceof net.minecraft.world.entity.player.Player);
                } else {
                    runtime.unload(entity.getUUID());
                }
                AnchorCaptureCoordinator.clearEntity(entity.getUUID());
            }
        });

        // Register the command interceptor — the Fabric implementation hooks
        // into ClientCommandRegistrationCallback and dispatches /halo commands
        // either locally (LOCAL phase) or to the server (MULTIPLAYER / singleplayer).
        new FabricHaloCommandInterceptor().register();

        // Register networking packet receivers for multiplayer halo sync.
        // Received messages update only the client core runtime.
        HaloNetworkClient.registerReceivers();

        // Reset the phase before the server's hello, including transitions to a vanilla
        // server. Reload reports are sent by HaloJsonLoader after visual publication.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            HaloPhaseTracker.getInstance().resetToLocal();
            HaloNetworkClient.sendDefsReport();
        });

        // Clear runtime halo state when disconnecting from a server.
        // Only the client replica is cleared; HaloLocalManager (persistent)
        // retains local halos so they survive reconnects to the same server.
        // Fabric can dispatch disconnect from the network thread. Preview scopes, runtime
        // state and render caches must all be cleared on the client/render thread.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            network.azusake.halo.render.PlayerPreviewRenderer.clearAutomaticViews();
            network.azusake.halo.platform.HaloClientState.get().clearAllClientHalos();
            network.azusake.halo.platform.IntegratedBridge.clearDiagnostics();
            AnchorCaptureCoordinator.clearCaptures();
            network.azusake.halo.render.HaloRenderer.getInstance().clearWorld();
            HaloPhaseTracker.getInstance().resetToLocal();
        }));

        LOGGER.info("Halo client initialized");
    }
}
