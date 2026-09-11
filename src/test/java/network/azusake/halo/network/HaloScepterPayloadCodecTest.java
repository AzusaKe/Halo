package network.azusake.halo.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HaloScepterPayloadCodecTest {

    @Test
    void openScreenPayloadRoundTrips() {
        UUID targetUuid = UUID.randomUUID();
        FriendlyByteBuf body = new FriendlyByteBuf(Unpooled.buffer());
        RegistryFriendlyByteBuf encoded =
            new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        FriendlyByteBuf decodedBody = null;
        try {
            body.writeInt(42);
            HaloNetwork.writeUuid(body, targetUuid);
            body.writeUtf("Test Target", 128);

            HaloPayloads.ScepterOpen.CODEC.encode(encoded, new HaloPayloads.ScepterOpen(body));
            decodedBody = HaloPayloads.ScepterOpen.CODEC.decode(encoded).buf();

            assertEquals(42, decodedBody.readInt());
            assertEquals(targetUuid, HaloNetwork.readUuid(decodedBody));
            assertEquals("Test Target", decodedBody.readUtf(128));
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
        Identifier definitionId =
            Identifier.fromNamespaceAndPath("halo", "ring_default");
        FriendlyByteBuf body = new FriendlyByteBuf(Unpooled.buffer());
        RegistryFriendlyByteBuf encoded =
            new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        FriendlyByteBuf decodedBody = null;
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
