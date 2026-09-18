package network.azusake.halo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import network.azusake.halo.HaloMod;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.item.HaloScepterService;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;

import java.util.*;
import java.util.function.Supplier;
import static network.azusake.halo.platform.PlatformTypes.*;

/** Forge network adapter. The optional channel permits client-only and server-only installations. */
public final class HaloNetwork {
    private static final String PROTOCOL_VERSION = "2.4.0";
    public static final ResourceLocation CHANNEL_SYNC = new ResourceLocation("halo", "sync");
    public static final ResourceLocation CHANNEL_UPDATE = new ResourceLocation("halo", "update");
    public static final ResourceLocation CHANNEL_DEFS_REPORT = new ResourceLocation("halo", "defs_report");
    public static final ResourceLocation CHANNEL_HELLO = new ResourceLocation("halo", "hello");
    public static final ResourceLocation CHANNEL_SCEPTER_OPEN = new ResourceLocation("halo", "scepter_open");
    public static final ResourceLocation CHANNEL_SCEPTER_CLOSE_SCREEN = new ResourceLocation("halo", "scepter_close_screen");
    public static final ResourceLocation CHANNEL_SCEPTER_SELECT = new ResourceLocation("halo", "scepter_select");
    public static final ResourceLocation CHANNEL_SCEPTER_CLOSE = new ResourceLocation("halo", "scepter_close");
    public static final ResourceLocation CHANNEL_SCEPTER_REMOVE_SELF = new ResourceLocation("halo", "scepter_remove_self");
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("halo", "main"), () -> PROTOCOL_VERSION,
        NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION), NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION));
    private static int discriminator;
    private static boolean registered;

    private HaloNetwork() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(discriminator++, SyncMessage.class, SyncMessage::encode, SyncMessage::decode,
            SyncMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(discriminator++, UpdateMessage.class, UpdateMessage::encode, UpdateMessage::decode,
            UpdateMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(discriminator++, HelloMessage.class, HelloMessage::encode, ignored -> new HelloMessage(),
            HelloMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(discriminator++, DefsReportMessage.class, DefsReportMessage::encode, DefsReportMessage::decode,
            DefsReportMessage::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(discriminator++, ScepterOpenMessage.class, ScepterOpenMessage::encode, ScepterOpenMessage::decode,
            ScepterOpenMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(discriminator++, ScepterCloseScreenMessage.class, ScepterCloseScreenMessage::encode,
            ignored -> new ScepterCloseScreenMessage(), ScepterCloseScreenMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(discriminator++, ScepterSelectMessage.class, ScepterSelectMessage::encode, ScepterSelectMessage::decode,
            ScepterSelectMessage::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(discriminator++, ScepterCloseMessage.class, ScepterCloseMessage::encode,
            ignored -> new ScepterCloseMessage(), ScepterCloseMessage::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(discriminator++, ScepterRemoveSelfMessage.class, ScepterRemoveSelfMessage::encode,
            ignored -> new ScepterRemoveSelfMessage(), ScepterRemoveSelfMessage::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        HaloMod.LOGGER.info("Registered {} optional Halo network messages", discriminator);
    }

    public static void registerServerReceivers() { register(); }

    public static void sendFullSync(ServerPlayer player) {
        send(player, new SyncMessage(HaloManager.getInstance().ownershipSnapshot()));
    }
    public static void sendHaloAttach(MinecraftServer server, UUID uuid, Identifier definition) {
        broadcast(server, new UpdateMessage(uuid, true, definition));
    }
    public static void sendHaloRemove(MinecraftServer server, UUID uuid, Identifier definition) {
        broadcast(server, new UpdateMessage(uuid, false, definition));
    }
    public static void sendHello(ServerPlayer player) { send(player, new HelloMessage()); }
    public static void sendScepterOpen(ServerPlayer player, LivingEntity target) {
        send(player, new ScepterOpenMessage(target.getId(), target.getUUID(), target.getDisplayName().getString()));
    }
    public static void sendScepterClose(ServerPlayer player) { send(player, new ScepterCloseScreenMessage()); }

    private static void send(ServerPlayer player, Object message) {
        if (CHANNEL.isRemotePresent(player.connection.connection))
            CHANNEL.sendTo(message, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
    private static void broadcast(MinecraftServer server, UpdateMessage message) {
        server.getPlayerList().getPlayers().forEach(player -> send(player, message));
    }
    public static void writeUuid(FriendlyByteBuf buf, UUID uuid) { buf.writeLong(uuid.getMostSignificantBits()); buf.writeLong(uuid.getLeastSignificantBits()); }
    public static UUID readUuid(FriendlyByteBuf buf) { return new UUID(buf.readLong(), buf.readLong()); }

    public record SyncMessage(Map<UUID, Identifier> values) {
        static void encode(SyncMessage m, FriendlyByteBuf b) { b.writeInt(m.values.size()); m.values.forEach((u,d)->{writeUuid(b,u);b.writeResourceLocation(game(d));}); }
        static SyncMessage decode(FriendlyByteBuf b) { int n=checkedCount(b,b.readInt(),17,"snapshot"); Map<UUID,Identifier> m=new LinkedHashMap<>(); for(int i=0;i<n;i++)m.put(readUuid(b),core(b.readResourceLocation())); return new SyncMessage(Map.copyOf(m)); }
        static void handle(SyncMessage m, Supplier<NetworkEvent.Context> c) { clientWork(c,()->HaloNetworkClient.handleSync(m.values)); }
    }
    public record UpdateMessage(UUID entity, boolean attach, Identifier definition) {
        static void encode(UpdateMessage m,FriendlyByteBuf b){writeUuid(b,m.entity);b.writeBoolean(m.attach);b.writeResourceLocation(game(m.definition));}
        static UpdateMessage decode(FriendlyByteBuf b){return new UpdateMessage(readUuid(b),b.readBoolean(),core(b.readResourceLocation()));}
        static void handle(UpdateMessage m,Supplier<NetworkEvent.Context> c){clientWork(c,()->HaloNetworkClient.handleUpdate(m.entity,m.attach,m.definition));}
    }
    public static final class HelloMessage {
        static void encode(HelloMessage m,FriendlyByteBuf b){}
        static void handle(HelloMessage m,Supplier<NetworkEvent.Context> c){clientWork(c,HaloNetworkClient::handleHello);}
    }
    public record DefsReportMessage(Set<Identifier> ids) {
        static void encode(DefsReportMessage m,FriendlyByteBuf b){b.writeInt(m.ids.size());m.ids.forEach(id->b.writeResourceLocation(game(id)));}
        static DefsReportMessage decode(FriendlyByteBuf b){int n=checkedCount(b,b.readInt(),1,"definition report");Set<Identifier>s=new LinkedHashSet<>();for(int i=0;i<n;i++)s.add(core(b.readResourceLocation()));return new DefsReportMessage(Set.copyOf(s));}
        static void handle(DefsReportMessage m,Supplier<NetworkEvent.Context> supplier){NetworkEvent.Context c=supplier.get();c.enqueueWork(()->{ServerPlayer p=c.getSender();if(p!=null)HaloJsonLoader.putClientReportedDefs(p.getUUID(),m.ids);});c.setPacketHandled(true);}
    }
    public record ScepterOpenMessage(int entityId, UUID entityUuid, String entityName) {
        static void encode(ScepterOpenMessage m,FriendlyByteBuf b){b.writeInt(m.entityId);writeUuid(b,m.entityUuid);b.writeUtf(m.entityName,128);}
        static ScepterOpenMessage decode(FriendlyByteBuf b){return new ScepterOpenMessage(b.readInt(),readUuid(b),b.readUtf(128));}
        static void handle(ScepterOpenMessage m,Supplier<NetworkEvent.Context> c){clientWork(c,()->HaloNetworkClient.handleScepterOpen(m.entityId,m.entityUuid,m.entityName));}
    }
    public static final class ScepterCloseScreenMessage {
        static void encode(ScepterCloseScreenMessage m,FriendlyByteBuf b){}
        static void handle(ScepterCloseScreenMessage m,Supplier<NetworkEvent.Context> c){clientWork(c,HaloNetworkClient::handleScepterClose);}
    }
    public record ScepterSelectMessage(Identifier definitionId) {
        static void encode(ScepterSelectMessage m,FriendlyByteBuf b){b.writeResourceLocation(game(m.definitionId));}
        static ScepterSelectMessage decode(FriendlyByteBuf b){return new ScepterSelectMessage(core(b.readResourceLocation()));}
        static void handle(ScepterSelectMessage m,Supplier<NetworkEvent.Context> c){serverWork(c,p->HaloScepterService.select(p,m.definitionId));}
    }
    public static final class ScepterCloseMessage {
        static void encode(ScepterCloseMessage m,FriendlyByteBuf b){}
        static void handle(ScepterCloseMessage m,Supplier<NetworkEvent.Context> c){serverWork(c,p->HaloScepterService.close(p.getUUID()));}
    }
    public static final class ScepterRemoveSelfMessage {
        static void encode(ScepterRemoveSelfMessage m,FriendlyByteBuf b){}
        static void handle(ScepterRemoveSelfMessage m,Supplier<NetworkEvent.Context> c){serverWork(c,p->HaloScepterService.remove(p,p,true));}
    }

    private static void clientWork(Supplier<NetworkEvent.Context> supplier,Runnable task){NetworkEvent.Context c=supplier.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->task.run()));c.setPacketHandled(true);}
    private static void serverWork(Supplier<NetworkEvent.Context> supplier,java.util.function.Consumer<ServerPlayer> task){NetworkEvent.Context c=supplier.get();c.enqueueWork(()->{if(c.getSender()!=null)task.accept(c.getSender());});c.setPacketHandled(true);}
    private static int checkedCount(FriendlyByteBuf b,int count,int minBytes,String kind){if(count<0||count>65_536||count>b.readableBytes()/minBytes)throw new IllegalArgumentException("Invalid halo "+kind+" count: "+count);return count;}
}
