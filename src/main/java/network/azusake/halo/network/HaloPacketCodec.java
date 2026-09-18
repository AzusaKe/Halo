package network.azusake.halo.network;

import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import network.azusake.halo.core.Identifier;
import static network.azusake.halo.platform.PlatformTypes.*;

/** The existing 1.3.0 wire format. No animation or ownership decisions belong here. */
public final class HaloPacketCodec {
    public record Update(UUID entity, boolean attach, Identifier definition) {}
    private HaloPacketCodec() {}

    public static FriendlyByteBuf encodeSnapshot(Map<UUID, Identifier> snapshot) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(snapshot.size());
        snapshot.forEach((uuid, id) -> { writeUuid(buf, uuid); buf.writeResourceLocation(game(id)); });
        return buf;
    }

    public static Map<UUID, Identifier> decodeSnapshot(FriendlyByteBuf buf) {
        int count = buf.readInt();
        if (count < 0 || count > buf.readableBytes() / 17) throw new IllegalArgumentException("Invalid halo snapshot count");
        var values = new LinkedHashMap<UUID, Identifier>();
        for (int i = 0; i < count; i++) values.put(readUuid(buf), core(buf.readResourceLocation()));
        return Map.copyOf(values);
    }

    public static FriendlyByteBuf encodeUpdate(UUID uuid, boolean attach, Identifier definition) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        writeUuid(buf, uuid);
        buf.writeBoolean(attach);
        buf.writeResourceLocation(game(definition));
        return buf;
    }

    public static Update decodeUpdate(FriendlyByteBuf buf) {
        return new Update(readUuid(buf), buf.readBoolean(), core(buf.readResourceLocation()));
    }

    private static void writeUuid(FriendlyByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(FriendlyByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }
}
