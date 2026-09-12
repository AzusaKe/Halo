package network.azusake.halo.network;

import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import network.azusake.halo.core.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HaloPacketCodecTest {
    private static final UUID ENTITY = new UUID(0, 1);
    private static final Identifier ID = new Identifier("halo:ring");
    // Original protocol: two big-endian UUID longs, then the existing boolean/UTF-8 identifier.
    private static final String UUID_HEX = "00000000000000000000000000000001";
    private static final String ID_HEX = "0968616c6f3a72696e67";

    @Test void snapshotMatchesLegacyBytesAndReadsLegacyBytes() {
        var expected = HexFormat.of().parseHex("00000001" + UUID_HEX + ID_HEX);
        var encoded = HaloPacketCodec.encodeSnapshot(Map.of(ENTITY, ID));
        try {
            var bytes = new byte[encoded.readableBytes()]; encoded.readBytes(bytes);
            assertArrayEquals(expected, bytes);
        } finally { encoded.release(); }
        var old = new PacketByteBuf(Unpooled.wrappedBuffer(expected));
        try { assertEquals(Map.of(ENTITY, ID), HaloPacketCodec.decodeSnapshot(old)); assertEquals(0, old.readableBytes()); }
        finally { old.release(); }
    }

    @Test void attachAndRemovePreserveLegacyLayout() {
        for (boolean attach : List.of(true, false)) {
            var expected = HexFormat.of().parseHex(UUID_HEX + (attach ? "01" : "00") + ID_HEX);
            var encoded = HaloPacketCodec.encodeUpdate(ENTITY, attach, ID);
            try {
                var bytes = new byte[encoded.readableBytes()]; encoded.getBytes(0, bytes);
                assertArrayEquals(expected, bytes);
                assertEquals(new HaloPacketCodec.Update(ENTITY, attach, ID), HaloPacketCodec.decodeUpdate(encoded));
            } finally { encoded.release(); }
        }
    }
}
