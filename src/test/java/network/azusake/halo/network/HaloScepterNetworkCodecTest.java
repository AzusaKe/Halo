package network.azusake.halo.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HaloScepterNetworkCodecTest {

    @Test
    void openScreenMessageRoundTrips() {
        UUID targetUuid = UUID.randomUUID();
        HaloNetwork.ScepterOpenMessage original =
            new HaloNetwork.ScepterOpenMessage(42, targetUuid, "Test Target");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            HaloNetwork.ScepterOpenMessage.encode(original, buffer);
            HaloNetwork.ScepterOpenMessage decoded = HaloNetwork.ScepterOpenMessage.decode(buffer);

            assertEquals(42, decoded.targetEntityId);
            assertEquals(targetUuid, decoded.targetUuid);
            assertEquals("Test Target", decoded.targetName);
        } finally {
            buffer.release();
        }
    }

    @Test
    void selectionMessageRoundTrips() {
        ResourceLocation definitionId = new ResourceLocation("halo", "ring_default");
        HaloNetwork.ScepterSelectMessage original =
            new HaloNetwork.ScepterSelectMessage(definitionId);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            HaloNetwork.ScepterSelectMessage.encode(original, buffer);
            HaloNetwork.ScepterSelectMessage decoded = HaloNetwork.ScepterSelectMessage.decode(buffer);

            assertEquals(definitionId, decoded.definitionId);
        } finally {
            buffer.release();
        }
    }
}
