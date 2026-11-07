package network.azusake.halo.replay;

import java.util.Map;
import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import network.azusake.halo.api.v2.replay.ReplaySnapshot;
import network.azusake.halo.api.v2.replay.ReplayUiState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayTransportTest {
    @Test void nativePayloadCodecPreservesRawSchemaOneBytes() {
        var record = new ReplayPayloadCodec.Record(UUID.randomUUID(),
            new ReplaySnapshot(0, Map.of(), Map.of(), ReplayUiState.CLOSED), null);
        var packet = ReplayTransport.packet(record);
        assertEquals(ReplayPayloadCodec.CHANNEL, packet.payload().type().id().toString());
        var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            var payload = (ReplayTransport.Payload)packet.payload();
            ReplayTransport.Payload.CODEC.encode(wire, payload);
            byte[] bytes = new byte[wire.readableBytes()];
            wire.getBytes(wire.readerIndex(), bytes);
            assertArrayEquals(ReplayPayloadCodec.encode(record), bytes);
            assertEquals(record, ReplayPayloadCodec.decode(ReplayTransport.Payload.CODEC.decode(wire).data()));
            assertEquals(0, wire.readableBytes());
        } finally { wire.release(); }
    }
}
