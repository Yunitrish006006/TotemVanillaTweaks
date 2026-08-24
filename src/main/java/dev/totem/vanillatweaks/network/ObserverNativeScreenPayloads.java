package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Structured GUI transport kept separate from the protocol-v2 gameplay/HUD stream. */
public final class ObserverNativeScreenPayloads {
    public static final int SCREEN_PROTOCOL_VERSION = 1;
    public static final int MAX_SLOTS = 128;
    private static final int MAX_TEXT = 256;

    private ObserverNativeScreenPayloads() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record SlotState(
            int index,
            int x,
            int y,
            String itemId,
            int count,
            int damage
    ) {
    }

    public record ContainerState(
            int protocolVersion,
            long sequence,
            boolean open,
            String screenClass,
            String title,
            int contentWidth,
            int contentHeight,
            int mouseX,
            int mouseY,
            List<SlotState> slots
    ) implements CustomPacketPayload {
        public static final Type<ContainerState> TYPE =
                new Type<>(id("observer_native_container_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, ContainerState> CODEC = StreamCodec.of(
                ObserverNativeScreenPayloads::writeContainerState,
                ObserverNativeScreenPayloads::readContainerState
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ContainerRelay(
            UUID targetId,
            int protocolVersion,
            long sequence,
            boolean open,
            String screenClass,
            String title,
            int contentWidth,
            int contentHeight,
            int mouseX,
            int mouseY,
            List<SlotState> slots
    ) implements CustomPacketPayload {
        public static final Type<ContainerRelay> TYPE =
                new Type<>(id("observer_native_container_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, ContainerRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeContainerFields(
                            buf,
                            value.protocolVersion(),
                            value.sequence(),
                            value.open(),
                            value.screenClass(),
                            value.title(),
                            value.contentWidth(),
                            value.contentHeight(),
                            value.mouseX(),
                            value.mouseY(),
                            value.slots()
                    );
                },
                buf -> {
                    UUID targetId = buf.readUUID();
                    ContainerState state = readContainerState(buf);
                    return new ContainerRelay(
                            targetId,
                            state.protocolVersion(),
                            state.sequence(),
                            state.open(),
                            state.screenClass(),
                            state.title(),
                            state.contentWidth(),
                            state.contentHeight(),
                            state.mouseX(),
                            state.mouseY(),
                            state.slots()
                    );
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeContainerState(FriendlyByteBuf buf, ContainerState value) {
        writeContainerFields(
                buf,
                value.protocolVersion(),
                value.sequence(),
                value.open(),
                value.screenClass(),
                value.title(),
                value.contentWidth(),
                value.contentHeight(),
                value.mouseX(),
                value.mouseY(),
                value.slots()
        );
    }

    private static void writeContainerFields(
            FriendlyByteBuf buf,
            int protocolVersion,
            long sequence,
            boolean open,
            String screenClass,
            String title,
            int contentWidth,
            int contentHeight,
            int mouseX,
            int mouseY,
            List<SlotState> slots
    ) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeVarInt(contentWidth);
        buf.writeVarInt(contentHeight);
        buf.writeVarInt(mouseX);
        buf.writeVarInt(mouseY);
        int slotCount = Math.min(slots.size(), MAX_SLOTS);
        buf.writeVarInt(slotCount);
        for (int i = 0; i < slotCount; i++) {
            SlotState slot = slots.get(i);
            buf.writeVarInt(slot.index());
            buf.writeVarInt(slot.x());
            buf.writeVarInt(slot.y());
            buf.writeUtf(slot.itemId(), MAX_TEXT);
            buf.writeVarInt(slot.count());
            buf.writeVarInt(slot.damage());
        }
    }

    private static ContainerState readContainerState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        int contentWidth = buf.readVarInt();
        int contentHeight = buf.readVarInt();
        int mouseX = buf.readVarInt();
        int mouseY = buf.readVarInt();
        int slotCount = buf.readVarInt();
        if (slotCount < 0 || slotCount > MAX_SLOTS) {
            throw new IllegalArgumentException("Observer container slot count out of range: " + slotCount);
        }
        List<SlotState> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new SlotState(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(MAX_TEXT),
                    buf.readVarInt(),
                    buf.readVarInt()
            ));
        }
        return new ContainerState(
                protocolVersion,
                sequence,
                open,
                screenClass,
                title,
                contentWidth,
                contentHeight,
                mouseX,
                mouseY,
                List.copyOf(slots)
        );
    }
}
