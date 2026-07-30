package dev.totem.vanillatweaks.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server request to sort one side of the currently open container menu. */
public record SortBackpackPayload(Target target) implements CustomPacketPayload {
    public enum Target {
        CONTAINER,
        PLAYER
    }

    public static final Type<SortBackpackPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "sort_backpack"));

    public static final StreamCodec<FriendlyByteBuf, SortBackpackPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeEnum(payload.target()),
                    buf -> new SortBackpackPayload(buf.readEnum(Target.class))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
