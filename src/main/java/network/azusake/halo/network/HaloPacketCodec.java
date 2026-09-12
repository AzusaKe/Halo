package network.azusake.halo.network;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import network.azusake.halo.core.Identifier;
import static network.azusake.halo.platform.PlatformTypes.*;

/** The existing 1.3.0 wire format. No animation or ownership decisions belong here. */
public final class HaloPacketCodec {
    public record Update(UUID entity, boolean attach, Identifier definition) {}
    private HaloPacketCodec() {}

    public static PacketByteBuf encodeSnapshot(Map<UUID, Identifier> snapshot) {
        var buf = PacketByteBufs.create();
        buf.writeInt(snapshot.size());
        snapshot.forEach((uuid, id) -> { HaloNetwork.writeUuid(buf, uuid); buf.writeIdentifier(game(id)); });
        return buf;
    }

    public static Map<UUID, Identifier> decodeSnapshot(PacketByteBuf buf) {
        int count = buf.readInt();
        if (count < 0 || count > buf.readableBytes() / 17) throw new IllegalArgumentException("Invalid halo snapshot count");
        var values = new LinkedHashMap<UUID, Identifier>();
        for (int i = 0; i < count; i++) values.put(HaloNetwork.readUuid(buf), core(buf.readIdentifier()));
        return Map.copyOf(values);
    }

    public static PacketByteBuf encodeUpdate(UUID uuid, boolean attach, Identifier definition) {
        var buf = PacketByteBufs.create();
        HaloNetwork.writeUuid(buf, uuid);
        buf.writeBoolean(attach);
        buf.writeIdentifier(game(definition));
        return buf;
    }

    public static Update decodeUpdate(PacketByteBuf buf) {
        return new Update(HaloNetwork.readUuid(buf), buf.readBoolean(), core(buf.readIdentifier()));
    }
}
