package network.azusake.halo.network;

import network.azusake.halo.HaloMod;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.item.HaloScepterService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Supplier;

/** Forge 47.4 compatible SimpleChannel network protocol. */
public final class HaloNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final ResourceLocation CHANNEL_SYNC = new ResourceLocation("halo", "sync");
    public static final ResourceLocation CHANNEL_UPDATE = new ResourceLocation("halo", "update");
    public static final ResourceLocation CHANNEL_DEFS_REPORT = new ResourceLocation("halo", "defs_report");
    public static final ResourceLocation CHANNEL_HELLO = new ResourceLocation("halo", "hello");
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("halo", "main"), () -> PROTOCOL_VERSION,
        NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
        NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION));
    private static int id;
    private static boolean registered;
    private HaloNetwork() {}
    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(id++, SyncMessage.class, SyncMessage::encode, SyncMessage::decode,
            SyncMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, UpdateMessage.class, UpdateMessage::encode, UpdateMessage::decode,
            UpdateMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, HelloMessage.class, HelloMessage::encode, b -> new HelloMessage(),
            HelloMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, DefsReportMessage.class, DefsReportMessage::encode, DefsReportMessage::decode,
            DefsReportMessage::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ScepterOpenMessage.class, ScepterOpenMessage::encode, ScepterOpenMessage::decode,
            ScepterOpenMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ScepterCloseScreenMessage.class, ScepterCloseScreenMessage::encode, b -> new ScepterCloseScreenMessage(),
            ScepterCloseScreenMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ScepterSelectMessage.class, ScepterSelectMessage::encode, ScepterSelectMessage::decode,
            ScepterSelectMessage::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ScepterCloseMessage.class, ScepterCloseMessage::encode, b -> new ScepterCloseMessage(),
            ScepterCloseMessage::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ScepterRemoveSelfMessage.class, ScepterRemoveSelfMessage::encode, b -> new ScepterRemoveSelfMessage(),
            ScepterRemoveSelfMessage::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        HaloMod.LOGGER.info("HaloNetwork registered SimpleChannel messages");
    }
    public static void registerServerReceivers() { register(); }
    public static void sendFullSync(ServerPlayer player) {
        if (!remote(player)) return;
        Map<UUID, ResourceLocation> values = new LinkedHashMap<>();
        for (HaloInstance instance : HaloManager.getInstance().getAllInstances())
            if (instance.isActive()) values.put(instance.getEntityUuid(), instance.getDefinitionId());
        CHANNEL.sendTo(new SyncMessage(values), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
    public static void sendHaloAttach(MinecraftServer server, UUID uuid, ResourceLocation def) {
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            if (remote(player)) CHANNEL.sendTo(new UpdateMessage(uuid, true, def), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
    public static void sendHaloRemove(MinecraftServer server, UUID uuid, ResourceLocation def) {
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            if (remote(player)) CHANNEL.sendTo(new UpdateMessage(uuid, false, def), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
    public static void sendHello(ServerPlayer player) {
        if (remote(player)) CHANNEL.sendTo(new HelloMessage(), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
    public static void sendScepterOpen(ServerPlayer player, LivingEntity target) {
        if (remote(player)) CHANNEL.sendTo(
            new ScepterOpenMessage(target.getId(), target.getUUID(), target.getDisplayName().getString()),
            player.connection.connection,
            NetworkDirection.PLAY_TO_CLIENT
        );
    }
    public static void sendScepterClose(ServerPlayer player) {
        if (remote(player)) CHANNEL.sendTo(
            new ScepterCloseScreenMessage(),
            player.connection.connection,
            NetworkDirection.PLAY_TO_CLIENT
        );
    }
    private static boolean remote(ServerPlayer p) { return CHANNEL.isRemotePresent(p.connection.connection); }
    public static void writeUuid(FriendlyByteBuf buf, UUID uuid) { buf.writeUUID(uuid); }
    public static UUID readUuid(FriendlyByteBuf buf) { return buf.readUUID(); }

    public static final class SyncMessage {
        final Map<UUID, ResourceLocation> values;
        SyncMessage(Map<UUID, ResourceLocation> values) { this.values = values; }
        static void encode(SyncMessage m, FriendlyByteBuf b) { b.writeVarInt(m.values.size()); m.values.forEach((u,d)->{b.writeUUID(u);b.writeResourceLocation(d);}); }
        static SyncMessage decode(FriendlyByteBuf b) { int n=b.readVarInt(); Map<UUID,ResourceLocation> m=new LinkedHashMap<>(); for(int i=0;i<n;i++)m.put(b.readUUID(),b.readResourceLocation()); return new SyncMessage(m); }
        static void handle(SyncMessage m, Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) { ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HaloNetworkClient.handleSync(m.values))); ctx.get().setPacketHandled(true); }
    }
    public static final class UpdateMessage {
        final UUID uuid; final boolean attach; final ResourceLocation definition;
        UpdateMessage(UUID uuid, boolean attach, ResourceLocation definition) { this.uuid=uuid;this.attach=attach;this.definition=definition; }
        static void encode(UpdateMessage m,FriendlyByteBuf b){b.writeUUID(m.uuid);b.writeBoolean(m.attach);b.writeResourceLocation(m.definition);}
        static UpdateMessage decode(FriendlyByteBuf b){return new UpdateMessage(b.readUUID(),b.readBoolean(),b.readResourceLocation());}
        static void handle(UpdateMessage m,Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->HaloNetworkClient.handleUpdate(m.uuid,m.attach,m.definition)));ctx.get().setPacketHandled(true);}
    }
    public static final class HelloMessage {
        static void encode(HelloMessage m,FriendlyByteBuf b) {}
        static void handle(HelloMessage m,Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->HaloNetworkClient::handleHello));ctx.get().setPacketHandled(true);}
    }
    public static final class DefsReportMessage {
        final Set<ResourceLocation> ids;
        DefsReportMessage(Set<ResourceLocation> ids){this.ids=ids;}
        static void encode(DefsReportMessage m,FriendlyByteBuf b){b.writeVarInt(m.ids.size());m.ids.forEach(b::writeResourceLocation);}
        static DefsReportMessage decode(FriendlyByteBuf b){int n=b.readVarInt();Set<ResourceLocation>s=new LinkedHashSet<>();for(int i=0;i<n;i++)s.add(b.readResourceLocation());return new DefsReportMessage(s);}
        static void handle(DefsReportMessage m,Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx){var c=ctx.get(); c.enqueueWork(()->{if(c.getSender()!=null)HaloJsonLoader.putClientReportedDefs(c.getSender().getUUID(),m.ids);});c.setPacketHandled(true);}
    }
    public static final class ScepterOpenMessage {
        final int targetEntityId;
        final UUID targetUuid;
        final String targetName;
        ScepterOpenMessage(int targetEntityId, UUID targetUuid, String targetName) {
            this.targetEntityId = targetEntityId;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
        }
        static void encode(ScepterOpenMessage m, FriendlyByteBuf b) {
            b.writeVarInt(m.targetEntityId);
            b.writeUUID(m.targetUuid);
            b.writeUtf(m.targetName, 128);
        }
        static ScepterOpenMessage decode(FriendlyByteBuf b) {
            return new ScepterOpenMessage(b.readVarInt(), b.readUUID(), b.readUtf(128));
        }
        static void handle(ScepterOpenMessage m, Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                HaloNetworkClient.handleScepterOpen(m.targetEntityId, m.targetUuid, m.targetName)));
            c.setPacketHandled(true);
        }
    }
    public static final class ScepterCloseScreenMessage {
        static void encode(ScepterCloseScreenMessage m, FriendlyByteBuf b) {}
        static void handle(ScepterCloseScreenMessage m, Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> HaloNetworkClient::handleScepterClose));
            c.setPacketHandled(true);
        }
    }
    public static final class ScepterSelectMessage {
        final ResourceLocation definitionId;
        ScepterSelectMessage(ResourceLocation definitionId) { this.definitionId = definitionId; }
        static void encode(ScepterSelectMessage m, FriendlyByteBuf b) { b.writeResourceLocation(m.definitionId); }
        static ScepterSelectMessage decode(FriendlyByteBuf b) { return new ScepterSelectMessage(b.readResourceLocation()); }
        static void handle(ScepterSelectMessage m, Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> {
                if (c.getSender() != null) HaloScepterService.select(c.getSender(), m.definitionId);
            });
            c.setPacketHandled(true);
        }
    }
    public static final class ScepterCloseMessage {
        static void encode(ScepterCloseMessage m, FriendlyByteBuf b) {}
        static void handle(ScepterCloseMessage m, Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> {
                if (c.getSender() != null) HaloScepterService.close(c.getSender().getUUID());
            });
            c.setPacketHandled(true);
        }
    }
    public static final class ScepterRemoveSelfMessage {
        static void encode(ScepterRemoveSelfMessage m, FriendlyByteBuf b) {}
        static void handle(ScepterRemoveSelfMessage m, Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> {
                if (c.getSender() != null) HaloScepterService.remove(c.getSender(), c.getSender(), true);
            });
            c.setPacketHandled(true);
        }
    }
}
