package network.azusake.halo.replay;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.util.Identifier;

/** Shared Minecraft envelope; raw JSON bytes follow the channel identifier without an extra length. */
public final class ReplayTransport {
    public record Payload(byte[] data) implements CustomPayload {
        public static final Id<Payload> ID=new Id<>(Identifier.of("halo","replay"));
        public static final PacketCodec<RegistryByteBuf,Payload> CODEC=PacketCodec.of(
            (payload,buf)->buf.writeBytes(payload.data),buf->{byte[] data=new byte[buf.readableBytes()];buf.readBytes(data);return new Payload(data);});
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    private ReplayTransport(){}
    public static void register(){
        PayloadTypeRegistry.playS2C().register(Payload.ID,Payload.CODEC);
        // Backends recover through their timed stream/index, avoiding packet/index double application.
        ClientPlayNetworking.registerGlobalReceiver(Payload.ID,(payload,context)->{});
    }
    public static CustomPayloadS2CPacket packet(ReplayPayloadCodec.Record record){
        return new CustomPayloadS2CPacket(new Payload(ReplayPayloadCodec.encode(record)));
    }
}
