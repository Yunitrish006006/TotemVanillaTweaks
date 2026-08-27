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
    public static final int PROTOCOL_VERSION = 2;
    public static final int MAX_EFFECTS = 64;
    public static final String VARIANT_PLAYER_2X2 = "player_2x2";
    public static final String VARIANT_TABLE_3X3 = "table_3x3";
    private static final int MAX_TEXT = 256;

    private ObserverCraftingScreenPayloads() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record EffectState(String effectId, int amplifier, int durationTicks,
                              boolean ambient, boolean visible, boolean showIcon) {}

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
            boolean recipeBookVisible,
            boolean recipeBookWidthTooNarrow,
            boolean recipeBookFiltering,
            boolean recipeBookSearchActive,
            String selectedRecipeBookTab,
            int recipeBookPage,
            int recipeBookPageCount,
            boolean activeEffectsVisible,
            List<EffectState> activeEffects,
            List<ObserverNativeScreenPayloads.SlotState> slots
    ) implements CustomPacketPayload {
        public static final Type<CraftingState> TYPE = new Type<>(id("observer_native_crafting_state_v2"));
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
            boolean recipeBookVisible,
            boolean recipeBookWidthTooNarrow,
            boolean recipeBookFiltering,
            boolean recipeBookSearchActive,
            String selectedRecipeBookTab,
            int recipeBookPage,
            int recipeBookPageCount,
            boolean activeEffectsVisible,
            List<EffectState> activeEffects,
            List<ObserverNativeScreenPayloads.SlotState> slots
    ) implements CustomPacketPayload {
        public static final Type<CraftingRelay> TYPE = new Type<>(id("observer_native_crafting_relay_v2"));
        public static final StreamCodec<FriendlyByteBuf, CraftingRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.variant(), value.screenClass(), value.title(), value.contentWidth(), value.contentHeight(),
                            value.mouseX(), value.mouseY(), value.gridWidth(), value.gridHeight(), value.resultSlotIndex(),
                            value.recipeBookVisible(), value.recipeBookWidthTooNarrow(), value.recipeBookFiltering(),
                            value.recipeBookSearchActive(), value.selectedRecipeBookTab(), value.recipeBookPage(),
                            value.recipeBookPageCount(), value.activeEffectsVisible(), value.activeEffects(),
                            value.slots());
                },
                buf -> {
                    UUID targetId = buf.readUUID();
                    CraftingState state = readState(buf);
                    return new CraftingRelay(targetId, state.protocolVersion(), state.sequence(), state.open(),
                            state.familyId(), state.variant(), state.screenClass(), state.title(), state.contentWidth(),
                            state.contentHeight(), state.mouseX(), state.mouseY(), state.gridWidth(), state.gridHeight(),
                            state.resultSlotIndex(), state.recipeBookVisible(), state.recipeBookWidthTooNarrow(),
                            state.recipeBookFiltering(), state.recipeBookSearchActive(), state.selectedRecipeBookTab(),
                            state.recipeBookPage(), state.recipeBookPageCount(), state.activeEffectsVisible(),
                            state.activeEffects(), state.slots());
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static CraftingState closed(long sequence) {
        return new CraftingState(PROTOCOL_VERSION, sequence, false,
                ObserverNativeScreenPayloads.FAMILY_CRAFTING, "", "", "",
                0, 0, 0, 0, 0, 0, 0,
                false, false, false, false, "", 0, 0, false, List.of(), List.of());
    }

    public static CraftingRelay relay(UUID targetId, CraftingState state) {
        return new CraftingRelay(targetId, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.variant(), state.screenClass(), state.title(), state.contentWidth(), state.contentHeight(),
                state.mouseX(), state.mouseY(), state.gridWidth(), state.gridHeight(), state.resultSlotIndex(),
                state.recipeBookVisible(), state.recipeBookWidthTooNarrow(), state.recipeBookFiltering(),
                state.recipeBookSearchActive(), state.selectedRecipeBookTab(), state.recipeBookPage(),
                state.recipeBookPageCount(), state.activeEffectsVisible(), state.activeEffects(), state.slots());
    }

    private static void writeState(FriendlyByteBuf buf, CraftingState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.variant(),
                value.screenClass(), value.title(), value.contentWidth(), value.contentHeight(), value.mouseX(),
                value.mouseY(), value.gridWidth(), value.gridHeight(), value.resultSlotIndex(),
                value.recipeBookVisible(), value.recipeBookWidthTooNarrow(), value.recipeBookFiltering(),
                value.recipeBookSearchActive(), value.selectedRecipeBookTab(), value.recipeBookPage(),
                value.recipeBookPageCount(), value.activeEffectsVisible(), value.activeEffects(), value.slots());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String variant, String screenClass, String title,
                                    int contentWidth, int contentHeight, int mouseX, int mouseY,
                                    int gridWidth, int gridHeight, int resultSlotIndex,
                                    boolean recipeBookVisible, boolean recipeBookWidthTooNarrow,
                                    boolean recipeBookFiltering, boolean recipeBookSearchActive,
                                    String selectedRecipeBookTab, int recipeBookPage, int recipeBookPageCount,
                                    boolean activeEffectsVisible, List<EffectState> activeEffects,
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
        buf.writeBoolean(recipeBookVisible);
        buf.writeBoolean(recipeBookWidthTooNarrow);
        buf.writeBoolean(recipeBookFiltering);
        buf.writeBoolean(recipeBookSearchActive);
        buf.writeUtf(selectedRecipeBookTab, MAX_TEXT);
        buf.writeVarInt(recipeBookPage);
        buf.writeVarInt(recipeBookPageCount);
        buf.writeBoolean(activeEffectsVisible);
        int effectCount = Math.min(activeEffects.size(), MAX_EFFECTS);
        buf.writeVarInt(effectCount);
        for (int i = 0; i < effectCount; i++) {
            EffectState effect = activeEffects.get(i);
            buf.writeUtf(effect.effectId(), MAX_TEXT);
            buf.writeVarInt(effect.amplifier());
            buf.writeVarInt(effect.durationTicks());
            buf.writeBoolean(effect.ambient());
            buf.writeBoolean(effect.visible());
            buf.writeBoolean(effect.showIcon());
        }
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
        boolean recipeBookVisible = buf.readBoolean();
        boolean recipeBookWidthTooNarrow = buf.readBoolean();
        boolean recipeBookFiltering = buf.readBoolean();
        boolean recipeBookSearchActive = buf.readBoolean();
        String selectedRecipeBookTab = buf.readUtf(MAX_TEXT);
        int recipeBookPage = buf.readVarInt();
        int recipeBookPageCount = buf.readVarInt();
        boolean activeEffectsVisible = buf.readBoolean();
        int effectCount = buf.readVarInt();
        if (effectCount < 0 || effectCount > MAX_EFFECTS) {
            throw new IllegalArgumentException("Observer crafting effect count out of range: " + effectCount);
        }
        List<EffectState> activeEffects = new ArrayList<>(effectCount);
        for (int i = 0; i < effectCount; i++) {
            activeEffects.add(new EffectState(buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
        }
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
                recipeBookVisible, recipeBookWidthTooNarrow, recipeBookFiltering, recipeBookSearchActive,
                selectedRecipeBookTab, recipeBookPage, recipeBookPageCount, activeEffectsVisible,
                List.copyOf(activeEffects),
                List.copyOf(slots));
    }
}
