package network.azusake.halo.network;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import network.azusake.halo.HaloMod;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.item.HaloScepterService;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import static network.azusake.halo.platform.PlatformTypes.core;

/** Server-side CustomPayload transport for the unchanged Halo packet protocol. */
public final class HaloNetwork {
    public static final net.minecraft.resources.Identifier CHANNEL_SYNC = HaloPayloads.Sync.ID.id();
    public static final net.minecraft.resources.Identifier CHANNEL_UPDATE = HaloPayloads.Update.ID.id();
    public static final net.minecraft.resources.Identifier CHANNEL_DEFS_REPORT = HaloPayloads.DefsReport.ID.id();
    public static final net.minecraft.resources.Identifier CHANNEL_HELLO = HaloPayloads.Hello.ID.id();
    public static final net.minecraft.resources.Identifier CHANNEL_SCEPTER_OPEN = HaloPayloads.ScepterOpen.ID.id();
    public static final net.minecraft.resources.Identifier CHANNEL_SCEPTER_CLOSE_SCREEN = HaloPayloads.ScepterCloseScreen.ID.id();
    public static final net.minecraft.resources.Identifier CHANNEL_SCEPTER_SELECT = HaloPayloads.ScepterSelect.ID.id();
    public static final net.minecraft.resources.Identifier CHANNEL_SCEPTER_CLOSE = HaloPayloads.ScepterClose.ID.id();
    public static final net.minecraft.resources.Identifier CHANNEL_SCEPTER_REMOVE_SELF = HaloPayloads.ScepterRemoveSelf.ID.id();

    private HaloNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        if (net.neoforged.fml.loading.FMLEnvironment.getDist().isClient()) {
            HaloNetworkClient.registerReceivers(registrar);
        } else {
            // Register outbound codecs without resolving any client GUI classes.
            registrar.playToClient(HaloPayloads.Sync.ID, HaloPayloads.Sync.CODEC, (payload, context) -> {});
            registrar.playToClient(HaloPayloads.Update.ID, HaloPayloads.Update.CODEC, (payload, context) -> {});
            registrar.playToClient(HaloPayloads.Hello.ID, HaloPayloads.Hello.CODEC, (payload, context) -> {});
            registrar.playToClient(HaloPayloads.ScepterOpen.ID, HaloPayloads.ScepterOpen.CODEC, (payload, context) -> {});
            registrar.playToClient(HaloPayloads.ScepterCloseScreen.ID, HaloPayloads.ScepterCloseScreen.CODEC, (payload, context) -> {});
        }
        registerServerReceivers(registrar);
        HaloMod.LOGGER.info("HaloNetwork: 1.21 payloads registered (sync={}, update={}, defs_report={}, hello={})",
            CHANNEL_SYNC, CHANNEL_UPDATE, CHANNEL_DEFS_REPORT, CHANNEL_HELLO);
    }

    public static void registerServerReceivers(PayloadRegistrar registrar) {
        registrar.playToServer(HaloPayloads.DefsReport.ID, HaloPayloads.DefsReport.CODEC, (payload, context) -> {
            var buf = payload.buf();
            int count = buf.readInt();
            Set<Identifier> ids = new LinkedHashSet<>(count);
            for (int i = 0; i < count; i++) ids.add(core(buf.readIdentifier()));
            context.enqueueWork(() -> HaloJsonLoader.putClientReportedDefs(((ServerPlayer) context.player()).getUUID(), ids));
        });
        registrar.playToServer(HaloPayloads.ScepterSelect.ID, HaloPayloads.ScepterSelect.CODEC, (payload, context) -> {
            Identifier definitionId = core(payload.buf().readIdentifier());
            context.enqueueWork(() -> HaloScepterService.select(((ServerPlayer) context.player()), definitionId));
        });
        registrar.playToServer(HaloPayloads.ScepterClose.ID, HaloPayloads.ScepterClose.CODEC, (payload, context) ->
            context.enqueueWork(() -> HaloScepterService.close(((ServerPlayer) context.player()).getUUID())));
        registrar.playToServer(HaloPayloads.ScepterRemoveSelf.ID, HaloPayloads.ScepterRemoveSelf.CODEC, (payload, context) ->
            context.enqueueWork(() -> HaloScepterService.remove(((ServerPlayer) context.player()), ((ServerPlayer) context.player()), true)));
    }

    public static void sendFullSync(ServerPlayer player) {
        send(player, new HaloPayloads.Sync(
            HaloPacketCodec.encodeSnapshot(HaloManager.getInstance().ownershipSnapshot())));
    }

    public static void sendHaloAttach(MinecraftServer server, UUID entityUuid, Identifier defId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, new HaloPayloads.Update(
                HaloPacketCodec.encodeUpdate(entityUuid, true, defId)));
        }
    }

    public static void sendHaloRemove(MinecraftServer server, UUID entityUuid, Identifier defId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, new HaloPayloads.Update(
                HaloPacketCodec.encodeUpdate(entityUuid, false, defId)));
        }
    }

    public static void sendHello(ServerPlayer player) {
        send(player, new HaloPayloads.Hello(new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer())));
    }

    public static void sendScepterOpen(ServerPlayer player, LivingEntity target) {
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeInt(target.getId());
        writeUuid(buf, target.getUUID());
        buf.writeUtf(target.getDisplayName().getString(), 128);
        send(player, new HaloPayloads.ScepterOpen(buf));
    }

    public static void sendScepterClose(ServerPlayer player) {
        send(player, new HaloPayloads.ScepterCloseScreen(new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer())));
    }

    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload.type())) PacketDistributor.sendToPlayer(player, payload);
    }

    public static void writeUuid(net.minecraft.network.FriendlyByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    public static UUID readUuid(net.minecraft.network.FriendlyByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }
}
