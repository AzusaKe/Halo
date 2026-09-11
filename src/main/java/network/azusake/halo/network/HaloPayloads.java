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

    /** S2C — open the selector for a server-locked target. */
    public record ScepterOpen(PacketByteBuf buf) implements CustomPayload {
        public static final CustomPayload.Id<ScepterOpen> ID =
            new CustomPayload.Id<>(Identifier.of("halo", "scepter_open"));
        public static final PacketCodec<RegistryByteBuf, ScepterOpen> CODEC =
            PacketCodec.of(ScepterOpen::write, buf ->
                new ScepterOpen(new PacketByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(ScepterOpen payload, PacketByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** S2C — close a selector whose server session became invalid. */
    public record ScepterCloseScreen(PacketByteBuf buf) implements CustomPayload {
        public static final CustomPayload.Id<ScepterCloseScreen> ID =
            new CustomPayload.Id<>(Identifier.of("halo", "scepter_close_screen"));
        public static final PacketCodec<RegistryByteBuf, ScepterCloseScreen> CODEC =
            PacketCodec.of(ScepterCloseScreen::write, buf ->
                new ScepterCloseScreen(new PacketByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(ScepterCloseScreen payload, PacketByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** C2S — apply a definition to the current locked target. */
    public record ScepterSelect(PacketByteBuf buf) implements CustomPayload {
        public static final CustomPayload.Id<ScepterSelect> ID =
            new CustomPayload.Id<>(Identifier.of("halo", "scepter_select"));
        public static final PacketCodec<RegistryByteBuf, ScepterSelect> CODEC =
            PacketCodec.of(ScepterSelect::write, buf ->
                new ScepterSelect(new PacketByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(ScepterSelect payload, PacketByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** C2S — release the current target lock. */
    public record ScepterClose(PacketByteBuf buf) implements CustomPayload {
        public static final CustomPayload.Id<ScepterClose> ID =
            new CustomPayload.Id<>(Identifier.of("halo", "scepter_close"));
        public static final PacketCodec<RegistryByteBuf, ScepterClose> CODEC =
            PacketCodec.of(ScepterClose::write, buf ->
                new ScepterClose(new PacketByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(ScepterClose payload, PacketByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** C2S — crouching left-click on air/block removes the player's own halo. */
    public record ScepterRemoveSelf(PacketByteBuf buf) implements CustomPayload {
        public static final CustomPayload.Id<ScepterRemoveSelf> ID =
            new CustomPayload.Id<>(Identifier.of("halo", "scepter_remove_self"));
        public static final PacketCodec<RegistryByteBuf, ScepterRemoveSelf> CODEC =
            PacketCodec.of(ScepterRemoveSelf::write, buf ->
                new ScepterRemoveSelf(new PacketByteBuf(buf.readBytes(buf.readableBytes()))));

        private static void write(ScepterRemoveSelf payload, PacketByteBuf buf) {
            buf.writeBytes(payload.buf().slice());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
