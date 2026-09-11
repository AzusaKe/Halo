package network.azusake.halo;

import network.azusake.halo.anchor.AnchorCaptureCoordinator;
import network.azusake.halo.client.NeoForgeHaloCommandInterceptor;
import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.client.HaloScepterClientInput;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.compat.emf.EmfCompatChatNotifier;
import network.azusake.halo.network.HaloNetworkClient;
import network.azusake.halo.render.HaloClientManager;
import network.azusake.halo.render.HaloRenderListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side initialisation for the NeoForge port.
 *
 * <p>Called from {@link HaloMod}'s constructor on the physical client.  All
 * client event listeners are registered here so they are never loaded on a
 * dedicated server.</p>
 */
public final class HaloModClient {

    public static final Logger LOGGER = LoggerFactory.getLogger(HaloMod.MOD_ID);

    private HaloModClient() {
        // utility class — use init()
    }

    /**
     * Initialise the client: phase tracker, resource reload listeners, anchor
     * providers, render pipeline, command interceptor and networking receivers.
     *
     * @param modEventBus the mod event bus (for MOD-bus client events)
     */
    public static void init(IEventBus modEventBus) {
        LOGGER.info("Halo client initializing...");
        EmfCompatChatNotifier.register();

        // Force initialisation of the phase tracker singleton.  The client
        // starts in LOCAL phase and transitions to MULTIPLAYER when
        // halo:hello is received from a modded server.
        HaloPhaseTracker.getInstance();

        // Register halo-definition resource loader on the client side so
        // definitions are available for rendering in single-player and when
        // definitions are bundled in a client resource pack.
        HaloJsonLoader.registerClientResources(modEventBus);

        // Register entity-anchor profile loader on the client side
        network.azusake.halo.json.EntityAnchorLoader.registerClientResources(modEventBus);

        // Register the halo renderer with NeoForge's world-render pipeline
        HaloRenderListener.register();

        // Initialise the client-side halo visibility manager
        HaloClientManager.getInstance();

        // Update per-tick entity state cache (invisible, sleeping) once per
        // client tick so the render path reads cached values instead of
        // querying the entity every frame.
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            HaloClientManager.getInstance().updateEntityStateCache();
            HaloScepterClientInput.tick(net.minecraft.client.Minecraft.getInstance());
        });

        // Clean up entity cache when entities are unloaded from the client world
        NeoForge.EVENT_BUS.addListener(EntityLeaveLevelEvent.class, event -> {
            var entity = event.getEntity();
            if (entity != null) {
                HaloClientManager.getInstance().onEntityUnloaded(entity.getUUID());
                AnchorCaptureCoordinator.clearEntity(entity.getUUID());
            }
        });

        // Register the command interceptor — the implementation hooks into
        // RegisterClientCommandsEvent and dispatches /halo commands either
        // locally (LOCAL phase) or to the server (MULTIPLAYER / singleplayer).
        new NeoForgeHaloCommandInterceptor().register();

        // Send local definition IDs to the server on join and on resource reloads.
        // This listener fires on the initial load cycle AND every /reload, so it
        // covers both bootstrap and incremental updates.  The sendDefsReport()
        // method safely no-ops when not connected to a server world.
        modEventBus.addListener(AddClientReloadListenersEvent.class, event ->
            event.addListener(Identifier.fromNamespaceAndPath(HaloMod.MOD_ID, "defs_report_trigger"),
                new SimplePreparableReloadListener<Void>() {
                    @Override
                    protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                        return null;
                    }

                    @Override
                    protected void apply(Void data, ResourceManager manager, ProfilerFiller profiler) {
                        net.minecraft.client.Minecraft.getInstance().execute(HaloNetworkClient::sendDefsReport);
                    }
                }));

        // Reset phase to LOCAL on every join, BEFORE the server can send
        // halo:hello.  This prevents state pollution from a previous session
        // (e.g. exiting singleplayer → joining a vanilla server — phase was
        // still MULTIPLAYER because integrated-server disconnect doesn't fire
        // the disconnect callback, and no hello arrives from the vanilla server).
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> {
            HaloPhaseTracker.getInstance().resetToLocal();
            HaloNetworkClient.sendDefsReport();
        });

        // Clear runtime halo state when disconnecting from a server.
        // Only HaloManager (runtime) is cleared; HaloLocalManager (persistent)
        // retains local halos so they survive reconnects to the same server.
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            HaloManager.getInstance().clearAllClientHalos();
            AnchorCaptureCoordinator.clearCaptures();
            HaloPhaseTracker.getInstance().resetToLocal();
        });

        LOGGER.info("Halo client initialized");
    }
}
