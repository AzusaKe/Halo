package network.azusake.halo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * {@code CustomPayload} records for the halo synchronisation channels.
 *
 * <p>Fabric API 1.20.5+ removed the legacy {@code Identifier}-based networking
 * API, so every channel now rides on a {@link CustomPacketPayload} registered in
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
    public record Sync(FriendlyByteBuf buf) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Sync> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("halo", "sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC =
            StreamCodec.ofMember(Sync::write, buf -> new Sync(new FriendlyByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(Sync payload, FriendlyByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Incremental attach / remove — broadcast to all players (S2C). */
    public record Update(FriendlyByteBuf buf) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Update> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("halo", "update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC =
            StreamCodec.ofMember(Update::write, buf -> new Update(new FriendlyByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(Update payload, FriendlyByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** C2S — client reports its locally-available definition IDs. */
    public record DefsReport(FriendlyByteBuf buf) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DefsReport> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("halo", "defs_report"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DefsReport> CODEC =
            StreamCodec.ofMember(DefsReport::write, buf -> new DefsReport(new FriendlyByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(DefsReport payload, FriendlyByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** S2C — handshake, sent on player join to signal "server has the mod installed". */
    public record Hello(FriendlyByteBuf buf) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Hello> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("halo", "hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC =
            StreamCodec.ofMember(Hello::write, buf -> new Hello(new FriendlyByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(Hello payload, FriendlyByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
