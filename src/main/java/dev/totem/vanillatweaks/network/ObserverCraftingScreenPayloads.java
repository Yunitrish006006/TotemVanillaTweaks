package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Structured crafting-family transport for player 2x2 and crafting-table 3x3 screens. */
public final class ObserverCraftingScreenPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final String VARIANT_PLAYER_2X2 = "player_2x2";
    public static final String VARIANT_TABLE_3X3 = "table_3x3";
    private static final int MAX_TEXT = 256;

    private ObserverCraftingScreenPayloads() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record CraftingState(
            int protocolVersion,
            long sequence,
            boolean open,
            String familyId,
            String variant,
            String screenClass,
            String title,
            int contentWidth,
            int contentHeight,
            int mouseX,
            int mouseY,
            int gridWidth,
            int gridHeight,
            int resultSlotIndex,
            List<ObserverNativeScreenPayloads.SlotState> slots
    ) implements CustomPacketPayload {
        public static final Type<CraftingState> TYPE = new Type<>(id("observer_native_crafting_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, CraftingState> CODEC = StreamCodec.of(
                ObserverCraftingScreenPayloads::writeState,
                ObserverCraftingScreenPayloads::readState
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CraftingRelay(
            UUID targetId,
            int protocolVersion,
            long sequence,
            boolean open,
            String familyId,
            String variant,
            String screenClass,
            String title,
            int contentWidth,
            int contentHeight,
            int mouseX,
            int mouseY,
            int gridWidth,
            int gridHeight,
            int resultSlotIndex,
            List<ObserverNativeScreenPayloads.SlotState> slots
    ) implements CustomPacketPayload {
        public static final Type<CraftingRelay> TYPE = new Type<>(id("observer_native_crafting_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, CraftingRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.variant(), value.screenClass(), value.title(), value.contentWidth(), value.contentHeight(),
                            value.mouseX(), value.mouseY(), value.gridWidth(), value.gridHeight(), value.resultSlotIndex(),
                            value.slots());
                },
                buf -> {
                    UUID targetId = buf.readUUID();
                    CraftingState state = readState(buf);
                    return new CraftingRelay(targetId, state.protocolVersion(), state.sequence(), state.open(),
                            state.familyId(), state.variant(), state.screenClass(), state.title(), state.contentWidth(),
                            state.contentHeight(), state.mouseX(), state.mouseY(), state.gridWidth(), state.gridHeight(),
                            state.resultSlotIndex(), state.slots());
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeState(FriendlyByteBuf buf, CraftingState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.variant(),
                value.screenClass(), value.title(), value.contentWidth(), value.contentHeight(), value.mouseX(),
                value.mouseY(), value.gridWidth(), value.gridHeight(), value.resultSlotIndex(), value.slots());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String variant, String screenClass, String title,
                                    int contentWidth, int contentHeight, int mouseX, int mouseY,
                                    int gridWidth, int gridHeight, int resultSlotIndex,
                                    List<ObserverNativeScreenPayloads.SlotState> slots) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(variant, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeVarInt(contentWidth);
        buf.writeVarInt(contentHeight);
        buf.writeVarInt(mouseX);
        buf.writeVarInt(mouseY);
        buf.writeVarInt(gridWidth);
        buf.writeVarInt(gridHeight);
        buf.writeVarInt(resultSlotIndex);
        int count = Math.min(slots.size(), ObserverNativeScreenPayloads.MAX_SLOTS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            ObserverNativeScreenPayloads.SlotState slot = slots.get(i);
            buf.writeVarInt(slot.index());
            buf.writeVarInt(slot.x());
            buf.writeVarInt(slot.y());
            buf.writeUtf(slot.itemId(), MAX_TEXT);
            buf.writeVarInt(slot.count());
            buf.writeVarInt(slot.damage());
        }
    }

    private static CraftingState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String variant = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        int contentWidth = buf.readVarInt();
        int contentHeight = buf.readVarInt();
        int mouseX = buf.readVarInt();
        int mouseY = buf.readVarInt();
        int gridWidth = buf.readVarInt();
        int gridHeight = buf.readVarInt();
        int resultSlotIndex = buf.readVarInt();
        int slotCount = buf.readVarInt();
        if (slotCount < 0 || slotCount > ObserverNativeScreenPayloads.MAX_SLOTS) {
            throw new IllegalArgumentException("Observer crafting slot count out of range: " + slotCount);
        }
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new ObserverNativeScreenPayloads.SlotState(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt()));
        }
        return new CraftingState(protocolVersion, sequence, open, familyId, variant, screenClass, title,
                contentWidth, contentHeight, mouseX, mouseY, gridWidth, gridHeight, resultSlotIndex,
                List.copyOf(slots));
    }
}
