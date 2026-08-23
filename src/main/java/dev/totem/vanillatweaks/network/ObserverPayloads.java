package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.vanillatweaks.observer.ObserverFrameRules;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Network messages used by the privileged spectator UI observer. */
public final class ObserverPayloads {
    private static final int MAX_TEXT = 256;

    private ObserverPayloads() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record CaptureControl(boolean enabled, int maxWidth, int maxHeight, int fps)
            implements CustomPacketPayload {
        public static final Type<CaptureControl> TYPE = new Type<>(id("observer_capture_control"));
        public static final StreamCodec<FriendlyByteBuf, CaptureControl> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBoolean(value.enabled);
                    buf.writeVarInt(value.maxWidth);
                    buf.writeVarInt(value.maxHeight);
                    buf.writeVarInt(value.fps);
                },
                buf -> new CaptureControl(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Session(boolean active, UUID targetId, String targetName) implements CustomPacketPayload {
        public static final Type<Session> TYPE = new Type<>(id("observer_session"));
        public static final StreamCodec<FriendlyByteBuf, Session> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBoolean(value.active);
                    buf.writeUUID(value.targetId);
                    buf.writeUtf(value.targetName, MAX_TEXT);
                },
                buf -> new Session(buf.readBoolean(), buf.readUUID(), buf.readUtf(MAX_TEXT))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ScreenState(boolean open, String screenClass, String title) implements CustomPacketPayload {
        public static final Type<ScreenState> TYPE = new Type<>(id("observer_screen_state"));
        public static final StreamCodec<FriendlyByteBuf, ScreenState> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBoolean(value.open);
                    buf.writeUtf(value.screenClass, MAX_TEXT);
                    buf.writeUtf(value.title, MAX_TEXT);
                },
                buf -> new ScreenState(buf.readBoolean(), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ScreenRelay(UUID targetId, boolean open, String screenClass, String title)
            implements CustomPacketPayload {
        public static final Type<ScreenRelay> TYPE = new Type<>(id("observer_screen_relay"));
        public static final StreamCodec<FriendlyByteBuf, ScreenRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId);
                    buf.writeBoolean(value.open);
                    buf.writeUtf(value.screenClass, MAX_TEXT);
                    buf.writeUtf(value.title, MAX_TEXT);
                },
                buf -> new ScreenRelay(buf.readUUID(), buf.readBoolean(), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record FrameChunk(
            long frameId,
            int chunkIndex,
            int chunkCount,
            int frameWidth,
            int frameHeight,
            int sourceWidth,
            int sourceHeight,
            int mouseX,
            int mouseY,
            byte[] data
    ) implements CustomPacketPayload {
        public static final Type<FrameChunk> TYPE = new Type<>(id("observer_frame_chunk"));
        public static final StreamCodec<FriendlyByteBuf, FrameChunk> CODEC = StreamCodec.of(
                ObserverPayloads::writeFrameChunk,
                ObserverPayloads::readFrameChunk
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record FrameRelay(
            UUID targetId,
            long frameId,
            int chunkIndex,
            int chunkCount,
            int frameWidth,
            int frameHeight,
            int sourceWidth,
            int sourceHeight,
            int mouseX,
            int mouseY,
            byte[] data
    ) implements CustomPacketPayload {
        public static final Type<FrameRelay> TYPE = new Type<>(id("observer_frame_relay"));
        public static final StreamCodec<FriendlyByteBuf, FrameRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId);
                    writeFrameFields(buf, value.frameId, value.chunkIndex, value.chunkCount,
                            value.frameWidth, value.frameHeight, value.sourceWidth, value.sourceHeight,
                            value.mouseX, value.mouseY, value.data);
                },
                buf -> {
                    UUID targetId = buf.readUUID();
                    FrameChunk chunk = readFrameChunk(buf);
                    return new FrameRelay(targetId, chunk.frameId, chunk.chunkIndex, chunk.chunkCount,
                            chunk.frameWidth, chunk.frameHeight, chunk.sourceWidth, chunk.sourceHeight,
                            chunk.mouseX, chunk.mouseY, chunk.data);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Stop() implements CustomPacketPayload {
        public static final Type<Stop> TYPE = new Type<>(id("observer_stop"));
        public static final StreamCodec<FriendlyByteBuf, Stop> CODEC = StreamCodec.of(
                (buf, value) -> {
                },
                buf -> new Stop()
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeFrameChunk(FriendlyByteBuf buf, FrameChunk value) {
        writeFrameFields(buf, value.frameId, value.chunkIndex, value.chunkCount,
                value.frameWidth, value.frameHeight, value.sourceWidth, value.sourceHeight,
                value.mouseX, value.mouseY, value.data);
    }

    private static FrameChunk readFrameChunk(FriendlyByteBuf buf) {
        long frameId = buf.readLong();
        int chunkIndex = buf.readVarInt();
        int chunkCount = buf.readVarInt();
        int frameWidth = buf.readVarInt();
        int frameHeight = buf.readVarInt();
        int sourceWidth = buf.readVarInt();
        int sourceHeight = buf.readVarInt();
        int mouseX = buf.readVarInt();
        int mouseY = buf.readVarInt();
        byte[] data = buf.readByteArray(ObserverFrameRules.CHUNK_BYTES);
        return new FrameChunk(frameId, chunkIndex, chunkCount, frameWidth, frameHeight,
                sourceWidth, sourceHeight, mouseX, mouseY, data);
    }

    private static void writeFrameFields(
            FriendlyByteBuf buf,
            long frameId,
            int chunkIndex,
            int chunkCount,
            int frameWidth,
            int frameHeight,
            int sourceWidth,
            int sourceHeight,
            int mouseX,
            int mouseY,
            byte[] data
    ) {
        buf.writeLong(frameId);
        buf.writeVarInt(chunkIndex);
        buf.writeVarInt(chunkCount);
        buf.writeVarInt(frameWidth);
        buf.writeVarInt(frameHeight);
        buf.writeVarInt(sourceWidth);
        buf.writeVarInt(sourceHeight);
        buf.writeVarInt(mouseX);
        buf.writeVarInt(mouseY);
        buf.writeByteArray(data);
    }
}
