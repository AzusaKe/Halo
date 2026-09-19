package network.azusake.halo.network;

import java.util.function.Function;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** 1.21 CustomPayload envelopes preserving Halo's established byte layouts. */
public final class HaloPayloads {
    private HaloPayloads() {}

    private interface BufferPayload extends CustomPayload {
        byte[] data();
        default PacketByteBuf buf() { return new PacketByteBuf(Unpooled.wrappedBuffer(data())); }
    }

    private static byte[] take(PacketByteBuf source) {
        try {
            byte[] data = new byte[source.readableBytes()];
            source.getBytes(source.readerIndex(), data);
            return data;
        } finally {
            source.release();
        }
    }

    private static <T extends BufferPayload> PacketCodec<RegistryByteBuf, T> codec(Function<byte[], T> factory) {
        return PacketCodec.of(
            (payload, output) -> output.writeBytes(payload.data()),
            input -> {
                byte[] data = new byte[input.readableBytes()];
                input.readBytes(data);
                return factory.apply(data);
            }
        );
    }

    public record Sync(byte[] data) implements BufferPayload {
        public Sync(PacketByteBuf buf) { this(take(buf)); }
        public static final Id<Sync> ID = new Id<>(Identifier.of("halo", "sync"));
        public static final PacketCodec<RegistryByteBuf, Sync> CODEC = codec(Sync::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Update(byte[] data) implements BufferPayload {
        public Update(PacketByteBuf buf) { this(take(buf)); }
        public static final Id<Update> ID = new Id<>(Identifier.of("halo", "update"));
        public static final PacketCodec<RegistryByteBuf, Update> CODEC = codec(Update::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record DefsReport(byte[] data) implements BufferPayload {
        public DefsReport(PacketByteBuf buf) { this(take(buf)); }
        public static final Id<DefsReport> ID = new Id<>(Identifier.of("halo", "defs_report"));
        public static final PacketCodec<RegistryByteBuf, DefsReport> CODEC = codec(DefsReport::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Hello(byte[] data) implements BufferPayload {
        public Hello(PacketByteBuf buf) { this(take(buf)); }
        public static final Id<Hello> ID = new Id<>(Identifier.of("halo", "hello"));
        public static final PacketCodec<RegistryByteBuf, Hello> CODEC = codec(Hello::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ScepterOpen(byte[] data) implements BufferPayload {
        public ScepterOpen(PacketByteBuf buf) { this(take(buf)); }
        public static final Id<ScepterOpen> ID = new Id<>(Identifier.of("halo", "scepter_open"));
        public static final PacketCodec<RegistryByteBuf, ScepterOpen> CODEC = codec(ScepterOpen::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ScepterCloseScreen(byte[] data) implements BufferPayload {
        public ScepterCloseScreen(PacketByteBuf buf) { this(take(buf)); }
        public static final Id<ScepterCloseScreen> ID = new Id<>(Identifier.of("halo", "scepter_close_screen"));
        public static final PacketCodec<RegistryByteBuf, ScepterCloseScreen> CODEC = codec(ScepterCloseScreen::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ScepterSelect(byte[] data) implements BufferPayload {
        public ScepterSelect(PacketByteBuf buf) { this(take(buf)); }
        public static final Id<ScepterSelect> ID = new Id<>(Identifier.of("halo", "scepter_select"));
        public static final PacketCodec<RegistryByteBuf, ScepterSelect> CODEC = codec(ScepterSelect::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ScepterClose(byte[] data) implements BufferPayload {
        public ScepterClose(PacketByteBuf buf) { this(take(buf)); }
        public static final Id<ScepterClose> ID = new Id<>(Identifier.of("halo", "scepter_close"));
        public static final PacketCodec<RegistryByteBuf, ScepterClose> CODEC = codec(ScepterClose::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ScepterRemoveSelf(byte[] data) implements BufferPayload {
        public ScepterRemoveSelf(PacketByteBuf buf) { this(take(buf)); }
        public static final Id<ScepterRemoveSelf> ID = new Id<>(Identifier.of("halo", "scepter_remove_self"));
        public static final PacketCodec<RegistryByteBuf, ScepterRemoveSelf> CODEC = codec(ScepterRemoveSelf::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
}
