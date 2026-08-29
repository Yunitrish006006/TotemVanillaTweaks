package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import io.netty.buffer.Unpooled;

/** Stable capability and bounded packets for the independent remote cursor stream. */
public final class ObserverRemoteCursorPayloads {
    public static final long CAPABILITY = 1L << 24;
    public static final int PROTOCOL_VERSION = 2;
    public static final int MAX_CONTENT_SIZE = 4096;
    public static final int MAX_UPDATES_PER_SECOND = 20;
    private static final int MAX_FAMILY = 64;
    private static final int MAX_VARIANT = 64;
    private static final int MAX_CURSOR_STACK_BYTES = 16 * 1024;

    private ObserverRemoteCursorPayloads() { }

    public static boolean valid(long sequence, float x, float y, int width, int height) {
        return sequence >= 0 && Float.isFinite(x) && Float.isFinite(y)
                && width >= 1 && width <= MAX_CONTENT_SIZE
                && height >= 1 && height <= MAX_CONTENT_SIZE
                && x >= 0 && x < width && y >= 0 && y < height;
    }

    public record State(int protocolVersion, long sequence, String familyId, String variant, int screenProtocol,
                        float x, float y, int contentWidth, int contentHeight, ItemStack carried)
            implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(id("observer_remote_cursor_state_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of(
                (buf, value) -> write(buf, value.protocolVersion(), value.sequence(), value.familyId(), value.variant(),
                        value.screenProtocol(), value.x(), value.y(), value.contentWidth(), value.contentHeight(), value.carried()),
                buf -> new State(buf.readVarInt(), buf.readVarLong(), buf.readUtf(MAX_FAMILY), buf.readUtf(MAX_VARIANT),
                        buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                        readStack(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Relay(UUID targetId, int protocolVersion, long sequence, String familyId, String variant, int screenProtocol,
                        float x, float y, int contentWidth, int contentHeight, ItemStack carried)
            implements CustomPacketPayload {
        public static final Type<Relay> TYPE = new Type<>(id("observer_remote_cursor_relay_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Relay> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeUUID(value.targetId()); write(buf, value.protocolVersion(), value.sequence(),
                        value.familyId(), value.variant(), value.screenProtocol(), value.x(), value.y(), value.contentWidth(), value.contentHeight(), value.carried()); },
                buf -> new Relay(buf.readUUID(), buf.readVarInt(), buf.readVarLong(), buf.readUtf(MAX_FAMILY),
                        buf.readUtf(MAX_VARIANT), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                        readStack(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void write(RegistryFriendlyByteBuf buf, int protocol, long sequence, String family, String variant, int screenProtocol,
                              float x, float y, int width, int height, ItemStack carried) {
        buf.writeVarInt(protocol); buf.writeVarLong(sequence); buf.writeUtf(family, MAX_FAMILY);
        buf.writeUtf(variant, MAX_VARIANT); buf.writeVarInt(screenProtocol); buf.writeFloat(x); buf.writeFloat(y); buf.writeVarInt(width);
        buf.writeVarInt(height);
        RegistryFriendlyByteBuf encoded = new RegistryFriendlyByteBuf(Unpooled.buffer(), buf.registryAccess());
        try {
            ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(encoded, carried);
            if (encoded.readableBytes() > MAX_CURSOR_STACK_BYTES)
                throw new IllegalArgumentException("Observer carried stack is too large");
            byte[] bytes = new byte[encoded.readableBytes()]; encoded.getBytes(encoded.readerIndex(), bytes);
            buf.writeByteArray(bytes);
        } finally { encoded.release(); }
    }

    private static ItemStack readStack(RegistryFriendlyByteBuf buf) {
        byte[] bytes = buf.readByteArray(MAX_CURSOR_STACK_BYTES);
        RegistryFriendlyByteBuf encoded = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), buf.registryAccess());
        try {
            ItemStack stack = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(encoded);
            if (encoded.readableBytes() != 0) throw new IllegalArgumentException("Trailing Observer carried stack bytes");
            return stack;
        } finally { encoded.release(); }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }
}
