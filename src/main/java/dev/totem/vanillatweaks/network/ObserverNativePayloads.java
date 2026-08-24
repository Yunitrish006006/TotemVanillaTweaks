package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Versioned structured-state transport for the protocol-native Observer View migration. */
public final class ObserverNativePayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final int TARGET_STATE_FPS = 10;
    private static final int MAX_TEXT = 256;

    private ObserverNativePayloads() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record NativeControl(
            boolean enabled,
            int protocolVersion,
            int stateFps,
            boolean captureGameplayFrames
    ) implements CustomPacketPayload {
        public static final Type<NativeControl> TYPE = new Type<>(id("observer_native_control"));
        public static final StreamCodec<FriendlyByteBuf, NativeControl> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBoolean(value.enabled);
                    buf.writeVarInt(value.protocolVersion);
                    buf.writeVarInt(value.stateFps);
                    buf.writeBoolean(value.captureGameplayFrames);
                },
                buf -> new NativeControl(
                        buf.readBoolean(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readBoolean()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record NativeSession(boolean active, UUID targetId, String targetName, int protocolVersion)
            implements CustomPacketPayload {
        public static final Type<NativeSession> TYPE = new Type<>(id("observer_native_session"));
        public static final StreamCodec<FriendlyByteBuf, NativeSession> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBoolean(value.active);
                    buf.writeUUID(value.targetId);
                    buf.writeUtf(value.targetName, MAX_TEXT);
                    buf.writeVarInt(value.protocolVersion);
                },
                buf -> new NativeSession(
                        buf.readBoolean(),
                        buf.readUUID(),
                        buf.readUtf(MAX_TEXT),
                        buf.readVarInt()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record NativeViewState(
            int protocolVersion,
            long sequence,
            float yaw,
            float pitch,
            float health,
            float maxHealth,
            int food,
            float saturation,
            boolean sprinting,
            boolean crouching,
            boolean usingItem
    ) implements CustomPacketPayload {
        public static final Type<NativeViewState> TYPE = new Type<>(id("observer_native_view_state"));
        public static final StreamCodec<FriendlyByteBuf, NativeViewState> CODEC = StreamCodec.of(
                ObserverNativePayloads::writeViewState,
                ObserverNativePayloads::readViewState
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record NativeViewRelay(
            UUID targetId,
            int protocolVersion,
            long sequence,
            float yaw,
            float pitch,
            float health,
            float maxHealth,
            int food,
            float saturation,
            boolean sprinting,
            boolean crouching,
            boolean usingItem
    ) implements CustomPacketPayload {
        public static final Type<NativeViewRelay> TYPE = new Type<>(id("observer_native_view_relay"));
        public static final StreamCodec<FriendlyByteBuf, NativeViewRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId);
                    writeViewFields(
                            buf,
                            value.protocolVersion,
                            value.sequence,
                            value.yaw,
                            value.pitch,
                            value.health,
                            value.maxHealth,
                            value.food,
                            value.saturation,
                            value.sprinting,
                            value.crouching,
                            value.usingItem
                    );
                },
                buf -> {
                    UUID targetId = buf.readUUID();
                    NativeViewState state = readViewState(buf);
                    return new NativeViewRelay(
                            targetId,
                            state.protocolVersion,
                            state.sequence,
                            state.yaw,
                            state.pitch,
                            state.health,
                            state.maxHealth,
                            state.food,
                            state.saturation,
                            state.sprinting,
                            state.crouching,
                            state.usingItem
                    );
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeViewState(FriendlyByteBuf buf, NativeViewState value) {
        writeViewFields(
                buf,
                value.protocolVersion,
                value.sequence,
                value.yaw,
                value.pitch,
                value.health,
                value.maxHealth,
                value.food,
                value.saturation,
                value.sprinting,
                value.crouching,
                value.usingItem
        );
    }

    private static NativeViewState readViewState(FriendlyByteBuf buf) {
        return new NativeViewState(
                buf.readVarInt(),
                buf.readLong(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean()
        );
    }

    private static void writeViewFields(
            FriendlyByteBuf buf,
            int protocolVersion,
            long sequence,
            float yaw,
            float pitch,
            float health,
            float maxHealth,
            int food,
            float saturation,
            boolean sprinting,
            boolean crouching,
            boolean usingItem
    ) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeFloat(health);
        buf.writeFloat(maxHealth);
        buf.writeVarInt(food);
        buf.writeFloat(saturation);
        buf.writeBoolean(sprinting);
        buf.writeBoolean(crouching);
        buf.writeBoolean(usingItem);
    }
}
