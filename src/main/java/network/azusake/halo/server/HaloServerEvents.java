package network.azusake.halo.server;

import network.azusake.halo.HaloMod;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.network.HaloNetwork;
import network.azusake.halo.physics.HaloTickHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.EventPriority;
import java.util.UUID;

/** Common Forge event registrations for server lifecycle and networking. */
public final class HaloServerEvents {
    private static boolean registered;
    private static boolean tickRegistered;
    private static boolean entityRegistered;
    private static boolean connectionRegistered;
    private HaloServerEvents() {}

    public static void registerAll() {
        registerAll(MinecraftForge.EVENT_BUS);
    }

    static void registerAll(IEventBus eventBus) {
        if (registered) return;
        registerTickHandler(eventBus);
        registerEntityEvents(eventBus);
        registerConnectionEvents(eventBus);
        registered = true;
        HaloMod.LOGGER.info("HaloServerEvents registered");
    }

    static void registerTickHandler() {
        registerTickHandler(MinecraftForge.EVENT_BUS);
    }

    static void registerTickHandler(IEventBus eventBus) {
        if (tickRegistered) return;
        ServerTickHandler.register(eventBus);
        HaloTickHandler.register(eventBus);
        tickRegistered = true;
    }

    static void registerEntityEvents() {
        registerEntityEvents(MinecraftForge.EVENT_BUS);
    }

    static void registerEntityEvents(IEventBus eventBus) {
        if (entityRegistered) return;
        eventBus.addListener(
            EventPriority.NORMAL,
            false,
            EntityLeaveLevelEvent.class,
            event -> {
                if (event.getLevel() instanceof ServerLevel level)
                    HaloManager.getInstance().removeHalo(event.getEntity().getUUID(), level.getServer());
            }
        );
        entityRegistered = true;
    }

    static void registerConnectionEvents() {
        registerConnectionEvents(MinecraftForge.EVENT_BUS);
    }

    static void registerConnectionEvents(IEventBus eventBus) {
        if (connectionRegistered) return;
        eventBus.addListener(
            EventPriority.NORMAL,
            false,
            PlayerEvent.PlayerLoggedInEvent.class,
            event -> {
                if (event.getEntity() instanceof ServerPlayer player) {
                    HaloNetwork.sendFullSync(player);
                    HaloNetwork.sendHello(player);
                }
            }
        );
        eventBus.addListener(
            EventPriority.NORMAL,
            false,
            PlayerEvent.PlayerLoggedOutEvent.class,
            event -> {
                if (event.getEntity() instanceof ServerPlayer player) {
                    UUID uuid = player.getUUID();
                    HaloManager.getInstance().removeHalo(uuid, player.level().getServer());
                    HaloJsonLoader.removeClientReportedDefs(uuid);
                }
            }
        );
        connectionRegistered = true;
    }
}
