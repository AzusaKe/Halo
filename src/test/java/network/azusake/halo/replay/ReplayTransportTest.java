package network.azusake.halo.replay;

import java.util.Map;
import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import network.azusake.halo.api.v2.replay.ReplaySnapshot;
import network.azusake.halo.api.v2.replay.ReplayUiState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayTransportTest {
    @Test void legacyPacketStoresChannelThenRawSchemaOnePayload() {
        var record = new ReplayPayloadCodec.Record(UUID.randomUUID(),
            new ReplaySnapshot(0, Map.of(), Map.of(), ReplayUiState.CLOSED), null);
        var packet = ReplayTransport.packet(record);
        var wire = new PacketByteBuf(Unpooled.buffer());
        try {
            packet.write(wire);
            assertEquals(ReplayPayloadCodec.CHANNEL, wire.readIdentifier().toString());
            byte[] payload = new byte[wire.readableBytes()];
            wire.readBytes(payload);
            assertArrayEquals(ReplayPayloadCodec.encode(record), payload);
            assertEquals(record, ReplayPayloadCodec.decode(payload));
        } finally {
            wire.release();
            packet.getData().release();
        }
    }
}
