package network.azusake.halo.replay;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.util.Identifier;

/** 1.20.1 envelope: channel identifier followed by schema-1 JSON, without an extra length. */
public final class ReplayTransport {
    public static final Identifier CHANNEL = new Identifier(ReplayPayloadCodec.CHANNEL);

    private ReplayTransport() {}

    public static void register() {
        // The timed original-stream index owns restoration in both Full and Quick mode.
        // Consuming this packet again would duplicate events and reset presentation.
        ClientPlayNetworking.registerGlobalReceiver(CHANNEL, (client, handler, buf, sender) -> {});
    }

    public static CustomPayloadS2CPacket packet(ReplayPayloadCodec.Record record) {
        var data = PacketByteBufs.create();
        data.writeBytes(ReplayPayloadCodec.encode(record));
        return new CustomPayloadS2CPacket(CHANNEL, data);
    }
}
