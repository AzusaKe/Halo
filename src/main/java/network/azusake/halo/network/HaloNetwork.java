package network.azusake.halo.network;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(HaloPayloads.Sync.ID, HaloPayloads.Sync.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HaloPayloads.Update.ID, HaloPayloads.Update.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HaloPayloads.Hello.ID, HaloPayloads.Hello.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HaloPayloads.ScepterOpen.ID, HaloPayloads.ScepterOpen.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HaloPayloads.ScepterCloseScreen.ID, HaloPayloads.ScepterCloseScreen.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HaloPayloads.DefsReport.ID, HaloPayloads.DefsReport.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HaloPayloads.ScepterSelect.ID, HaloPayloads.ScepterSelect.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HaloPayloads.ScepterClose.ID, HaloPayloads.ScepterClose.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HaloPayloads.ScepterRemoveSelf.ID, HaloPayloads.ScepterRemoveSelf.CODEC);
        registerServerReceivers();
        HaloMod.LOGGER.info("HaloNetwork: 1.21 payloads registered (sync={}, update={}, defs_report={}, hello={})",
            CHANNEL_SYNC, CHANNEL_UPDATE, CHANNEL_DEFS_REPORT, CHANNEL_HELLO);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(HaloPayloads.DefsReport.ID, (payload, context) -> {
            var buf = payload.buf();
            int count = buf.readInt();
            Set<Identifier> ids = new LinkedHashSet<>(count);
            for (int i = 0; i < count; i++) ids.add(core(buf.readIdentifier()));
            context.server().execute(() -> HaloJsonLoader.putClientReportedDefs(context.player().getUUID(), ids));
        });
        ServerPlayNetworking.registerGlobalReceiver(HaloPayloads.ScepterSelect.ID, (payload, context) -> {
            Identifier definitionId = core(payload.buf().readIdentifier());
            context.server().execute(() -> HaloScepterService.select(context.player(), definitionId));
        });
        ServerPlayNetworking.registerGlobalReceiver(HaloPayloads.ScepterClose.ID, (payload, context) ->
            context.server().execute(() -> HaloScepterService.close(context.player().getUUID())));
        ServerPlayNetworking.registerGlobalReceiver(HaloPayloads.ScepterRemoveSelf.ID, (payload, context) ->
            context.server().execute(() -> HaloScepterService.remove(context.player(), context.player(), true)));
    }

    public static void sendFullSync(ServerPlayer player) {
        ServerPlayNetworking.send(player, new HaloPayloads.Sync(
            HaloPacketCodec.encodeSnapshot(HaloManager.getInstance().ownershipSnapshot())));
    }

    public static void sendHaloAttach(MinecraftServer server, UUID entityUuid, Identifier defId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, new HaloPayloads.Update(
                HaloPacketCodec.encodeUpdate(entityUuid, true, defId)));
        }
    }

    public static void sendHaloRemove(MinecraftServer server, UUID entityUuid, Identifier defId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, new HaloPayloads.Update(
                HaloPacketCodec.encodeUpdate(entityUuid, false, defId)));
        }
    }

    public static void sendHello(ServerPlayer player) {
        ServerPlayNetworking.send(player, new HaloPayloads.Hello(FriendlyByteBufs.create()));
    }

    public static void sendScepterOpen(ServerPlayer player, LivingEntity target) {
        var buf = FriendlyByteBufs.create();
        buf.writeInt(target.getId());
        writeUuid(buf, target.getUUID());
        buf.writeUtf(target.getDisplayName().getString(), 128);
        ServerPlayNetworking.send(player, new HaloPayloads.ScepterOpen(buf));
    }

    public static void sendScepterClose(ServerPlayer player) {
        ServerPlayNetworking.send(player, new HaloPayloads.ScepterCloseScreen(FriendlyByteBufs.create()));
    }

    public static void writeUuid(net.minecraft.network.FriendlyByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    public static UUID readUuid(net.minecraft.network.FriendlyByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }
}
