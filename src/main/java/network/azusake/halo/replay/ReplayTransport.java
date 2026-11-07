package network.azusake.halo.replay;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;

/** Shared Minecraft envelope; raw JSON bytes follow the channel identifier without an extra length. */
public final class ReplayTransport {
    public record Payload(byte[] data) implements CustomPacketPayload {
        public static final Type<Payload> ID=new Type<>(Identifier.fromNamespaceAndPath("halo","replay"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Payload> CODEC=StreamCodec.ofMember(
            (payload,buf)->buf.writeBytes(payload.data),buf->{byte[] data=new byte[buf.readableBytes()];buf.readBytes(data);return new Payload(data);});
        @Override public Type<? extends CustomPacketPayload> type(){return ID;}
    }
    private ReplayTransport(){}
    public static void register(){
        PayloadTypeRegistry.clientboundPlay().register(Payload.ID,Payload.CODEC);
        // Backends recover through their timed stream/index, avoiding packet/index double application.
        ClientPlayNetworking.registerGlobalReceiver(Payload.ID,(payload,context)->{});
    }
    public static ClientboundCustomPayloadPacket packet(ReplayPayloadCodec.Record record){
        return new ClientboundCustomPayloadPacket(new Payload(ReplayPayloadCodec.encode(record)));
    }
}
