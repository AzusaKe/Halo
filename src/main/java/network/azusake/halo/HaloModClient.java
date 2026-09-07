package network.azusake.halo;

import network.azusake.halo.api.AnchorProviderSetupEvent;
import network.azusake.halo.api.EntityAnchorProviderRegistry;
import network.azusake.halo.api.FallbackAnchorProvider;
import network.azusake.halo.client.ForgeHaloCommandInterceptor;
import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.compat.emf.EmfCompatChatNotifier;
import network.azusake.halo.compat.ysm.YsmEntityAnchorProvider;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.json.EntityAnchorLoader;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.network.HaloNetworkClient;
import network.azusake.halo.physics.PlayerAnchorProvider;
import network.azusake.halo.physics.RenderHeadAnchorProvider;
import network.azusake.halo.render.HaloClientManager;
import network.azusake.halo.render.HaloRenderListener;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.concurrent.atomic.AtomicBoolean;

/** Physical-client initialization; never loaded on a dedicated server. */
public final class HaloModClient {
    private static final AtomicBoolean ANCHOR_SETUP_FIRED = new AtomicBoolean();

    private HaloModClient() {
    }

    public static void init() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        EmfCompatChatNotifier.register();
        HaloPhaseTracker.getInstance();
        HaloJsonLoader.registerClientResources(modBus);
        EntityAnchorLoader.registerClientResources(modBus);

        EntityAnchorProviderRegistry registry = EntityAnchorProviderRegistry.getInstance();
        registry.register(Player.class, PlayerAnchorProvider.getInstance());
        registry.register(
            LivingEntity.class,
            new YsmEntityAnchorProvider(FallbackAnchorProvider.getInstance()));
        registry.register(Player.class, new RenderHeadAnchorProvider(PlayerAnchorProvider.getInstance()));

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            if (ANCHOR_SETUP_FIRED.compareAndSet(false, true)) {
                MinecraftForge.EVENT_BUS.post(new AnchorProviderSetupEvent(registry));
                HaloMod.LOGGER.info(
                    "Default anchor providers registered; AnchorProviderSetupEvent fired (first client tick)");
                logYsmProviderOverrides(registry);
            }
            HaloClientManager.getInstance().updateEntityStateCache();
        });
        MinecraftForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) -> {
            if (event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel) {
                HaloClientManager.getInstance().onEntityUnloaded(event.getEntity().getUUID());
            }
        });
        HaloRenderListener.register();
        HaloClientManager.getInstance();
        new ForgeHaloCommandInterceptor().register();
        HaloNetworkClient.registerReceivers();
        modBus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(
            new SimplePreparableReloadListener<Void>() {
                @Override
                protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                    return null;
                }

                @Override
                protected void apply(Void ignored, ResourceManager manager, ProfilerFiller profiler) {
                    Minecraft.getInstance().execute(HaloNetworkClient::sendDefsReport);
                }
            }));
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            HaloPhaseTracker.getInstance().resetToLocal();
            HaloNetworkClient.sendDefsReport();
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            HaloManager.getInstance().clearAllClientHalos();
            HaloPhaseTracker.getInstance().resetToLocal();
        });
    }

    private static void logYsmProviderOverrides(EntityAnchorProviderRegistry registry) {
        if (!HaloModConfigStore.get().isExperimentalYsmAnchorEnabled()) {
            return;
        }

        var playerProvider = registry.getProvider(Player.class);
        if (!(playerProvider instanceof RenderHeadAnchorProvider)) {
            HaloMod.LOGGER.warn(
                "[YSM Compat] player anchor provider was overridden by {}; that provider controls YSM anchors",
                playerProvider.getClass().getName());
        }
        var livingProvider = registry.getProvider(LivingEntity.class);
        if (!(livingProvider instanceof YsmEntityAnchorProvider)) {
            HaloMod.LOGGER.warn(
                "[YSM Compat] generic living-entity anchor provider was overridden by {}; "
                    + "that provider controls non-player YSM anchors",
                livingProvider.getClass().getName());
        }
    }
}
