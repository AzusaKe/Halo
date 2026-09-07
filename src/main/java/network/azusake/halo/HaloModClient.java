package network.azusake.halo;

import network.azusake.halo.client.FabricHaloCommandInterceptor;
import network.azusake.halo.client.HaloLocalManager;
import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.api.AnchorProviderSetupEvent;
import network.azusake.halo.api.EntityAnchorProviderRegistry;
import network.azusake.halo.compat.emf.EmfCompatChatNotifier;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.network.HaloNetworkClient;
import network.azusake.halo.physics.PlayerAnchorProvider;
import network.azusake.halo.physics.RenderHeadAnchorProvider;
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
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class HaloModClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(HaloMod.MOD_ID);

    private static final AtomicBoolean ANCHOR_SETUP_FIRED = new AtomicBoolean(false);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Halo client initializing...");

        // EMF compatibility is enabled by default.  The optional Mixin
        // plugin decides whether the loaded EMF version/ABI is usable; this
        // notifier only surfaces its actionable failure message in-game.
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

        // Register the default anchor providers immediately.  The setup event
        // is fired once at the end of the first client tick (see below) so
        // that every other mod's client entrypoint has run by then — Fabric
        // gives no cross-mod ordering guarantee for entrypoints, so firing
        // here could race with other mods registering their listeners.
        EntityAnchorProviderRegistry anchorRegistry = EntityAnchorProviderRegistry.getInstance();
        anchorRegistry.register(Player.class, PlayerAnchorProvider.getInstance());
        // Default player provider: the render-head capture provider anchors
        // the halo to the actually rendered head.  It keeps PlayerAnchorProvider
        // (backed by entity_anchors/player.json) as its no-capture fallback for
        // first-person, culled, or renderer-replaced players.  Both providers
        // are registered so external mods can still override via the setup
        // event (last-wins).
        anchorRegistry.register(Player.class, new RenderHeadAnchorProvider(PlayerAnchorProvider.getInstance()));

        // Fire AnchorProviderSetupEvent exactly once, at the end of the first
        // client tick.  All mod entrypoints have run by then, so listeners
        // registered in any onInitializeClient are always observed.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (ANCHOR_SETUP_FIRED.compareAndSet(false, true)) {
                AnchorProviderSetupEvent.EVENT.invoker().onSetup(anchorRegistry);
                LOGGER.info("Default anchor providers registered; AnchorProviderSetupEvent fired (first client tick)");
            }
        });

        // Register the halo renderer with Fabric's world-render pipeline
        HaloRenderListener.register();

        // Initialise the client-side halo visibility manager
        HaloClientManager.getInstance();

        // Update per-tick entity state cache (invisible, sleeping) once per
        // client tick so the render path reads cached values instead of
        // querying the entity every frame.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            HaloClientManager.getInstance().updateEntityStateCache();
        });

        // Clean up entity cache when entities are unloaded from the client world
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity != null) {
                HaloClientManager.getInstance().onEntityUnloaded(entity.getUUID());
            }
        });

        // Register the command interceptor — the Fabric implementation hooks
        // into ClientCommandRegistrationCallback and dispatches /halo commands
        // either locally (LOCAL phase) or to the server (MULTIPLAYER / singleplayer).
        new FabricHaloCommandInterceptor().register();

        // Register networking packet receivers for multiplayer halo sync.
        // These write directly into HaloManager so the existing single-player
        // rendering pipeline works unchanged on dedicated-server clients.
        HaloNetworkClient.registerReceivers();

        // Send local definition IDs to the server on join and on resource reloads.
        // This listener fires on the initial load cycle AND every /reload, so it
        // covers both bootstrap and incremental updates.  The sendDefsReport()
        // method safely no-ops when not connected to a server world.
        ResourceManagerHelper
            .get(PackType.CLIENT_RESOURCES)
            .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                @Override
                public Identifier getFabricId() {
                    return Identifier.fromNamespaceAndPath(HaloMod.MOD_ID, "defs_report_trigger");
                }
                @Override
                public void onResourceManagerReload(ResourceManager manager) {
                    net.minecraft.client.Minecraft.getInstance().execute(() -> {
                        HaloNetworkClient.sendDefsReport();
                    });
                }
            });

        // Reset phase to LOCAL on every join, BEFORE the server can send
        // halo:hello.  This prevents state pollution from a previous session
        // (e.g. exiting singleplayer → joining a vanilla server — phase was
        // still MULTIPLAYER because integrated-server disconnect doesn't fire
        // DISCONNECT callback, and no hello arrives from the vanilla server).
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            HaloPhaseTracker.getInstance().resetToLocal();
            HaloNetworkClient.sendDefsReport();
        });

        // Clear runtime halo state when disconnecting from a server.
        // Only HaloManager (runtime) is cleared; HaloLocalManager (persistent)
        // retains local halos so they survive reconnects to the same server.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            network.azusake.halo.manager.HaloManager.getInstance().clearAllClientHalos();
            HaloPhaseTracker.getInstance().resetToLocal();
        });

        LOGGER.info("Halo client initialized");
    }
}
