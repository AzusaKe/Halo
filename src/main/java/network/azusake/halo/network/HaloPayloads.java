package network.azusake.halo.network;

import java.util.function.Function;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 1.21 CustomPacketPayload envelopes preserving Halo's established byte layouts. */
public final class HaloPayloads {
    private HaloPayloads() {}

    private interface BufferPayload extends CustomPacketPayload {
        byte[] data();
        default FriendlyByteBuf buf() { return new FriendlyByteBuf(Unpooled.wrappedBuffer(data())); }
    }

    private static byte[] take(FriendlyByteBuf source) {
        try {
            byte[] data = new byte[source.readableBytes()];
            source.getBytes(source.readerIndex(), data);
            return data;
        } finally {
            source.release();
        }
    }

    private static <T extends BufferPayload> StreamCodec<RegistryFriendlyByteBuf, T> codec(Function<byte[], T> factory) {
        return StreamCodec.ofMember(
            (payload, output) -> output.writeBytes(payload.data()),
            input -> {
                byte[] data = new byte[input.readableBytes()];
                input.readBytes(data);
                return factory.apply(data);
            }
        );
    }

    public record Sync(byte[] data) implements BufferPayload {
        public Sync(FriendlyByteBuf buf) { this(take(buf)); }
        public static final Type<Sync> ID = new Type<>(Identifier.fromNamespaceAndPath("halo", "sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = codec(Sync::new);
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record Update(byte[] data) implements BufferPayload {
        public Update(FriendlyByteBuf buf) { this(take(buf)); }
        public static final Type<Update> ID = new Type<>(Identifier.fromNamespaceAndPath("halo", "update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC = codec(Update::new);
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record DefsReport(byte[] data) implements BufferPayload {
        public DefsReport(FriendlyByteBuf buf) { this(take(buf)); }
        public static final Type<DefsReport> ID = new Type<>(Identifier.fromNamespaceAndPath("halo", "defs_report"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DefsReport> CODEC = codec(DefsReport::new);
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record Hello(byte[] data) implements BufferPayload {
        public Hello(FriendlyByteBuf buf) { this(take(buf)); }
        public static final Type<Hello> ID = new Type<>(Identifier.fromNamespaceAndPath("halo", "hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC = codec(Hello::new);
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ScepterOpen(byte[] data) implements BufferPayload {
        public ScepterOpen(FriendlyByteBuf buf) { this(take(buf)); }
        public static final Type<ScepterOpen> ID = new Type<>(Identifier.fromNamespaceAndPath("halo", "scepter_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ScepterOpen> CODEC = codec(ScepterOpen::new);
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ScepterCloseScreen(byte[] data) implements BufferPayload {
        public ScepterCloseScreen(FriendlyByteBuf buf) { this(take(buf)); }
        public static final Type<ScepterCloseScreen> ID = new Type<>(Identifier.fromNamespaceAndPath("halo", "scepter_close_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ScepterCloseScreen> CODEC = codec(ScepterCloseScreen::new);
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ScepterSelect(byte[] data) implements BufferPayload {
        public ScepterSelect(FriendlyByteBuf buf) { this(take(buf)); }
        public static final Type<ScepterSelect> ID = new Type<>(Identifier.fromNamespaceAndPath("halo", "scepter_select"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ScepterSelect> CODEC = codec(ScepterSelect::new);
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ScepterClose(byte[] data) implements BufferPayload {
        public ScepterClose(FriendlyByteBuf buf) { this(take(buf)); }
        public static final Type<ScepterClose> ID = new Type<>(Identifier.fromNamespaceAndPath("halo", "scepter_close"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ScepterClose> CODEC = codec(ScepterClose::new);
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ScepterRemoveSelf(byte[] data) implements BufferPayload {
        public ScepterRemoveSelf(FriendlyByteBuf buf) { this(take(buf)); }
        public static final Type<ScepterRemoveSelf> ID = new Type<>(Identifier.fromNamespaceAndPath("halo", "scepter_remove_self"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ScepterRemoveSelf> CODEC = codec(ScepterRemoveSelf::new);
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
}
