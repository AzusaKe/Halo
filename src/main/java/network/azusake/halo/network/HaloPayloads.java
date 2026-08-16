package network.azusake.halo.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * {@code CustomPayload} records for the halo synchronisation channels.
 *
 * <p>Fabric API 1.20.5+ removed the legacy {@code Identifier}-based networking
 * API, so every channel now rides on a {@link CustomPayload} registered in
 * {@link net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry}.  These
 * payloads wrap the exact byte layout of the old 1.20.x {@code PacketByteBuf}
 * channels, so the on-wire format is unchanged.</p>
 *
 * <p>Handlers must read {@link #buf()} synchronously inside the receiver
 * callback (the byte buffer is only valid for the duration of decoding).</p>
 */
public final class HaloPayloads {

    private HaloPayloads() {
        // utility class
    }

    /** Full-state snapshot — sent to a player on join (S2C). */
    public record Sync(PacketByteBuf buf) implements CustomPayload {
        public static final CustomPayload.Id<Sync> ID = new CustomPayload.Id<>(Identifier.of("halo", "sync"));
        public static final PacketCodec<RegistryByteBuf, Sync> CODEC =
            PacketCodec.of(Sync::write, buf -> new Sync(new PacketByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(Sync payload, PacketByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Incremental attach / remove — broadcast to all players (S2C). */
    public record Update(PacketByteBuf buf) implements CustomPayload {
        public static final CustomPayload.Id<Update> ID = new CustomPayload.Id<>(Identifier.of("halo", "update"));
        public static final PacketCodec<RegistryByteBuf, Update> CODEC =
            PacketCodec.of(Update::write, buf -> new Update(new PacketByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(Update payload, PacketByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** C2S — client reports its locally-available definition IDs. */
    public record DefsReport(PacketByteBuf buf) implements CustomPayload {
        public static final CustomPayload.Id<DefsReport> ID = new CustomPayload.Id<>(Identifier.of("halo", "defs_report"));
        public static final PacketCodec<RegistryByteBuf, DefsReport> CODEC =
            PacketCodec.of(DefsReport::write, buf -> new DefsReport(new PacketByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(DefsReport payload, PacketByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** S2C — handshake, sent on player join to signal "server has the mod installed". */
    public record Hello(PacketByteBuf buf) implements CustomPayload {
        public static final CustomPayload.Id<Hello> ID = new CustomPayload.Id<>(Identifier.of("halo", "hello"));
        public static final PacketCodec<RegistryByteBuf, Hello> CODEC =
            PacketCodec.of(Hello::write, buf -> new Hello(new PacketByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(Hello payload, PacketByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
