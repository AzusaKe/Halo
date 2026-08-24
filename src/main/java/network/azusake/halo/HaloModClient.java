package network.azusake.halo;

import network.azusake.halo.api.AnchorProviderSetupEvent;
import network.azusake.halo.api.EntityAnchorProviderRegistry;
import network.azusake.halo.api.FallbackAnchorProvider;
import network.azusake.halo.client.NeoForgeHaloCommandInterceptor;
import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.compat.ysm.YsmEntityAnchorProvider;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.network.HaloNetworkClient;
import network.azusake.halo.physics.PlayerAnchorProvider;
import network.azusake.halo.physics.RenderHeadAnchorProvider;
import network.azusake.halo.render.HaloClientManager;
import network.azusake.halo.render.HaloRenderListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client-side initialisation for the NeoForge port.
 *
 * <p>Called from {@link HaloMod}'s constructor on the physical client.  All
 * client event listeners are registered here so they are never loaded on a
 * dedicated server.</p>
 */
public final class HaloModClient {

    public static final Logger LOGGER = LoggerFactory.getLogger(HaloMod.MOD_ID);

    private static final AtomicBoolean ANCHOR_SETUP_FIRED = new AtomicBoolean(false);

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

        // Register the default anchor providers immediately.  The setup event
        // is fired once at the end of the first client tick (see below) so
        // that every other mod's client setup has run by then.
        EntityAnchorProviderRegistry anchorRegistry = EntityAnchorProviderRegistry.getInstance();
        anchorRegistry.register(Player.class, PlayerAnchorProvider.getInstance());
        anchorRegistry.register(LivingEntity.class,
            new YsmEntityAnchorProvider(FallbackAnchorProvider.getInstance()));
        // Default player provider: the render-head capture provider anchors
        // the halo to the actually rendered head.  It keeps PlayerAnchorProvider
        // (backed by entity_anchors/player.json) as its no-capture fallback for
        // first-person, culled, or renderer-replaced players.  Both providers
        // are registered so external mods can still override via the setup
        // event (last-wins).
        anchorRegistry.register(Player.class, new RenderHeadAnchorProvider(PlayerAnchorProvider.getInstance()));

        // Fire AnchorProviderSetupEvent exactly once, at the end of the first
        // client tick.  All mods' client setup has run by then, so listeners
        // registered during any mod's client init are always observed.
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            if (ANCHOR_SETUP_FIRED.compareAndSet(false, true)) {
                AnchorProviderSetupEvent.EVENT.invoker().onSetup(anchorRegistry);
                LOGGER.info("Default anchor providers registered; AnchorProviderSetupEvent fired (first client tick)");
                if (HaloModConfigStore.get().isExperimentalYsmAnchorEnabled()) {
                    LOGGER.info("[YSM Compat] experimental anchor provider enabled; external setup listeners retain last-wins priority");
                }
            }
        });

        // Register the halo renderer with NeoForge's world-render pipeline
        HaloRenderListener.register();

        // Initialise the client-side halo visibility manager
        HaloClientManager.getInstance();

        // Update per-tick entity state cache (invisible, sleeping) once per
        // client tick so the render path reads cached values instead of
        // querying the entity every frame.
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            HaloClientManager.getInstance().updateEntityStateCache();
        });

        // Clean up entity cache when entities are unloaded from the client world
        NeoForge.EVENT_BUS.addListener(EntityLeaveLevelEvent.class, event -> {
            var entity = event.getEntity();
            if (entity != null) {
                HaloClientManager.getInstance().onEntityUnloaded(entity.getUUID());
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
            HaloPhaseTracker.getInstance().resetToLocal();
        });

        LOGGER.info("Halo client initialized");
    }
}
