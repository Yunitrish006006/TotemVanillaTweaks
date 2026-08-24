package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Small lifecycle/screen-metadata messages shared by protocol-native Observer View. */
public final class ObserverPayloads {
    private static final int MAX_TEXT = 256;

    private ObserverPayloads() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
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
}
