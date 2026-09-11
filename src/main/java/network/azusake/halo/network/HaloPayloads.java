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
 * {@code PayloadRegistrar}.  These
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

    /** S2C — open the selector for a server-locked target. */
    public record ScepterOpen(FriendlyByteBuf buf) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ScepterOpen> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("halo", "scepter_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ScepterOpen> CODEC =
            StreamCodec.ofMember(ScepterOpen::write, buf ->
                new ScepterOpen(new FriendlyByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(ScepterOpen payload, FriendlyByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** S2C — close a selector whose server session became invalid. */
    public record ScepterCloseScreen(FriendlyByteBuf buf) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ScepterCloseScreen> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("halo", "scepter_close_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ScepterCloseScreen> CODEC =
            StreamCodec.ofMember(ScepterCloseScreen::write, buf ->
                new ScepterCloseScreen(new FriendlyByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(ScepterCloseScreen payload, FriendlyByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** C2S — apply a definition to the current locked target. */
    public record ScepterSelect(FriendlyByteBuf buf) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ScepterSelect> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("halo", "scepter_select"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ScepterSelect> CODEC =
            StreamCodec.ofMember(ScepterSelect::write, buf ->
                new ScepterSelect(new FriendlyByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(ScepterSelect payload, FriendlyByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** C2S — release the current target lock. */
    public record ScepterClose(FriendlyByteBuf buf) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ScepterClose> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("halo", "scepter_close"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ScepterClose> CODEC =
            StreamCodec.ofMember(ScepterClose::write, buf ->
                new ScepterClose(new FriendlyByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(ScepterClose payload, FriendlyByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** C2S — crouching left-click on air/block removes the player's own halo. */
    public record ScepterRemoveSelf(FriendlyByteBuf buf) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ScepterRemoveSelf> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("halo", "scepter_remove_self"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ScepterRemoveSelf> CODEC =
            StreamCodec.ofMember(ScepterRemoveSelf::write, buf ->
                new ScepterRemoveSelf(new FriendlyByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(ScepterRemoveSelf payload, FriendlyByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
