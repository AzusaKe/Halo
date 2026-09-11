package network.azusake.halo.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HaloScepterPayloadCodecTest {

    @Test
    void openScreenPayloadRoundTrips() {
        UUID targetUuid = UUID.randomUUID();
        PacketByteBuf body = new PacketByteBuf(Unpooled.buffer());
        RegistryByteBuf encoded = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        PacketByteBuf decodedBody = null;
        try {
            body.writeInt(42);
            HaloNetwork.writeUuid(body, targetUuid);
            body.writeString("Test Target", 128);

            HaloPayloads.ScepterOpen.CODEC.encode(encoded, new HaloPayloads.ScepterOpen(body));
            decodedBody = HaloPayloads.ScepterOpen.CODEC.decode(encoded).buf();

            assertEquals(42, decodedBody.readInt());
            assertEquals(targetUuid, HaloNetwork.readUuid(decodedBody));
            assertEquals("Test Target", decodedBody.readString(128));
        } finally {
            if (decodedBody != null) {
                decodedBody.release();
            }
            encoded.release();
            body.release();
        }
    }

    @Test
    void selectionPayloadRoundTrips() {
        Identifier definitionId = Identifier.of("halo", "ring_default");
        PacketByteBuf body = new PacketByteBuf(Unpooled.buffer());
        RegistryByteBuf encoded = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        PacketByteBuf decodedBody = null;
        try {
            body.writeIdentifier(definitionId);

            HaloPayloads.ScepterSelect.CODEC.encode(encoded, new HaloPayloads.ScepterSelect(body));
            decodedBody = HaloPayloads.ScepterSelect.CODEC.decode(encoded).buf();

            assertEquals(definitionId, decodedBody.readIdentifier());
        } finally {
            if (decodedBody != null) {
                decodedBody.release();
            }
            encoded.release();
            body.release();
        }
    }
}
