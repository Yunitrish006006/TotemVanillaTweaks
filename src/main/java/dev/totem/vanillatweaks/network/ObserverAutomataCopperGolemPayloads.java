package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Optional TotemAutomata Copper Golem screen semantic transport. API keys are never carried. */
public final class ObserverAutomataCopperGolemPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_BINDINGS = 128;
    public static final int MAX_VALUES = 64;
    private static final int MAX_TEXT = 256;
    private static final int MAX_LONG_TEXT = 2048;

    private ObserverAutomataCopperGolemPayloads() {}

    public record BindingState(String dimension, int x, int y, int z, String blockId, String itemId,
                               boolean loaded, boolean available, boolean llmEnabled, String llmPrompt,
                               int llmCachedItemIds, int llmCachedTags, List<String> llmAllowedItemIds,
                               List<String> llmDeniedItemIds, List<String> llmAllowedTags,
                               List<String> llmDeniedTags) {}

    public record GatheringAreaState(String dimension, boolean hasCornerA, int cornerAX, int cornerAY, int cornerAZ,
                                     boolean hasCornerB, int cornerBX, int cornerBY, int cornerBZ) {}

    public record CopperGolemState(
            int protocolVersion, long sequence, boolean open, String familyId, String screenClass, String title,
            boolean running, String mode, String activity, String tab, int selectedBinding, int bindingScroll,
            boolean bindingDetailVisible, boolean filterTextEntryVisible, boolean filterTextEntryAllowed,
            boolean cacheValueIsTag, boolean targetBlocksVisible,
            String fuelItemId, int fuelCount, int fuelTicks, boolean infiniteFuel,
            String toolItemId, int toolCount, int toolDamage, int toolMaxDamage,
            String storageItemId, int storageCount,
            String editorApiUrl, boolean apiKeyConfigured, String editorModel, int llmActiveCount,
            String editorGatheringPrompt, String editorBindingPrompt, String cacheValueText,
            BindingState sourceContainer, GatheringAreaState gatheringArea, List<String> gatheringManualTargets,
            boolean gatheringLlmEnabled, int gatheringLlmCachedBlockIds, int gatheringLlmCachedTags,
            List<String> gatheringLlmAllowedBlockIds, List<String> gatheringLlmDeniedBlockIds,
            List<String> gatheringLlmAllowedTags, List<String> gatheringLlmDeniedTags,
            List<BindingState> bindings, List<ObserverNativeScreenPayloads.SlotState> slots
    ) implements CustomPacketPayload {
        public static final Type<CopperGolemState> TYPE = new Type<>(id("observer_automata_copper_golem_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, CopperGolemState> CODEC = StreamCodec.of(
                ObserverAutomataCopperGolemPayloads::writeState,
                ObserverAutomataCopperGolemPayloads::readState);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CopperGolemRelay(
            UUID targetId,
            int protocolVersion, long sequence, boolean open, String familyId, String screenClass, String title,
            boolean running, String mode, String activity, String tab, int selectedBinding, int bindingScroll,
            boolean bindingDetailVisible, boolean filterTextEntryVisible, boolean filterTextEntryAllowed,
            boolean cacheValueIsTag, boolean targetBlocksVisible,
            String fuelItemId, int fuelCount, int fuelTicks, boolean infiniteFuel,
            String toolItemId, int toolCount, int toolDamage, int toolMaxDamage,
            String storageItemId, int storageCount,
            String editorApiUrl, boolean apiKeyConfigured, String editorModel, int llmActiveCount,
            String editorGatheringPrompt, String editorBindingPrompt, String cacheValueText,
            BindingState sourceContainer, GatheringAreaState gatheringArea, List<String> gatheringManualTargets,
            boolean gatheringLlmEnabled, int gatheringLlmCachedBlockIds, int gatheringLlmCachedTags,
            List<String> gatheringLlmAllowedBlockIds, List<String> gatheringLlmDeniedBlockIds,
            List<String> gatheringLlmAllowedTags, List<String> gatheringLlmDeniedTags,
            List<BindingState> bindings, List<ObserverNativeScreenPayloads.SlotState> slots
    ) implements CustomPacketPayload {
        public static final Type<CopperGolemRelay> TYPE = new Type<>(id("observer_automata_copper_golem_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, CopperGolemRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeState(buf, value.asState());
                },
                buf -> fromState(buf.readUUID(), readState(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        public CopperGolemState asState() {
            return new CopperGolemState(protocolVersion, sequence, open, familyId, screenClass, title, running, mode,
                    activity, tab, selectedBinding, bindingScroll, bindingDetailVisible, filterTextEntryVisible,
                    filterTextEntryAllowed, cacheValueIsTag, targetBlocksVisible, fuelItemId, fuelCount, fuelTicks,
                    infiniteFuel, toolItemId, toolCount, toolDamage, toolMaxDamage, storageItemId, storageCount,
                    editorApiUrl, apiKeyConfigured, editorModel, llmActiveCount, editorGatheringPrompt,
                    editorBindingPrompt, cacheValueText, sourceContainer, gatheringArea, gatheringManualTargets,
                    gatheringLlmEnabled, gatheringLlmCachedBlockIds, gatheringLlmCachedTags,
                    gatheringLlmAllowedBlockIds, gatheringLlmDeniedBlockIds, gatheringLlmAllowedTags,
                    gatheringLlmDeniedTags, bindings, slots);
        }
    }

    public static CopperGolemRelay relay(UUID targetId, CopperGolemState s) { return fromState(targetId, s); }

    private static CopperGolemRelay fromState(UUID targetId, CopperGolemState s) {
        return new CopperGolemRelay(targetId, s.protocolVersion(), s.sequence(), s.open(), s.familyId(),
                s.screenClass(), s.title(), s.running(), s.mode(), s.activity(), s.tab(), s.selectedBinding(),
                s.bindingScroll(), s.bindingDetailVisible(), s.filterTextEntryVisible(), s.filterTextEntryAllowed(),
                s.cacheValueIsTag(), s.targetBlocksVisible(), s.fuelItemId(), s.fuelCount(), s.fuelTicks(),
                s.infiniteFuel(), s.toolItemId(), s.toolCount(), s.toolDamage(), s.toolMaxDamage(), s.storageItemId(),
                s.storageCount(), s.editorApiUrl(), s.apiKeyConfigured(), s.editorModel(), s.llmActiveCount(),
                s.editorGatheringPrompt(), s.editorBindingPrompt(), s.cacheValueText(), s.sourceContainer(),
                s.gatheringArea(), s.gatheringManualTargets(), s.gatheringLlmEnabled(), s.gatheringLlmCachedBlockIds(),
                s.gatheringLlmCachedTags(), s.gatheringLlmAllowedBlockIds(), s.gatheringLlmDeniedBlockIds(),
                s.gatheringLlmAllowedTags(), s.gatheringLlmDeniedTags(), s.bindings(), s.slots());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    private static void writeState(FriendlyByteBuf buf, CopperGolemState v) {
        buf.writeVarInt(v.protocolVersion());
        buf.writeLong(v.sequence());
        buf.writeBoolean(v.open());
        buf.writeUtf(v.familyId(), MAX_TEXT);
        buf.writeUtf(v.screenClass(), MAX_TEXT);
        buf.writeUtf(v.title(), MAX_TEXT);
        buf.writeBoolean(v.running());
        buf.writeUtf(v.mode(), 32);
        buf.writeUtf(v.activity(), 64);
        buf.writeUtf(v.tab(), 32);
        buf.writeVarInt(v.selectedBinding());
        buf.writeVarInt(v.bindingScroll());
        buf.writeBoolean(v.bindingDetailVisible());
        buf.writeBoolean(v.filterTextEntryVisible());
        buf.writeBoolean(v.filterTextEntryAllowed());
        buf.writeBoolean(v.cacheValueIsTag());
        buf.writeBoolean(v.targetBlocksVisible());
        writeItemSummary(buf, v.fuelItemId(), v.fuelCount());
        buf.writeVarInt(v.fuelTicks());
        buf.writeBoolean(v.infiniteFuel());
        writeItemSummary(buf, v.toolItemId(), v.toolCount());
        buf.writeVarInt(v.toolDamage());
        buf.writeVarInt(v.toolMaxDamage());
        writeItemSummary(buf, v.storageItemId(), v.storageCount());
        buf.writeUtf(v.editorApiUrl(), MAX_LONG_TEXT);
        buf.writeBoolean(v.apiKeyConfigured());
        buf.writeUtf(v.editorModel(), MAX_TEXT);
        buf.writeVarInt(v.llmActiveCount());
        buf.writeUtf(v.editorGatheringPrompt(), MAX_LONG_TEXT);
        buf.writeUtf(v.editorBindingPrompt(), MAX_LONG_TEXT);
        buf.writeUtf(v.cacheValueText(), MAX_TEXT);
        writeOptionalBinding(buf, v.sourceContainer());
        writeOptionalArea(buf, v.gatheringArea());
        writeStrings(buf, v.gatheringManualTargets());
        buf.writeBoolean(v.gatheringLlmEnabled());
        buf.writeVarInt(v.gatheringLlmCachedBlockIds());
        buf.writeVarInt(v.gatheringLlmCachedTags());
        writeStrings(buf, v.gatheringLlmAllowedBlockIds());
        writeStrings(buf, v.gatheringLlmDeniedBlockIds());
        writeStrings(buf, v.gatheringLlmAllowedTags());
        writeStrings(buf, v.gatheringLlmDeniedTags());
        writeBindings(buf, v.bindings());
        writeSlots(buf, v.slots());
    }

    private static CopperGolemState readState(FriendlyByteBuf buf) {
        return new CopperGolemState(
                buf.readVarInt(), buf.readLong(), buf.readBoolean(), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT),
                buf.readUtf(MAX_TEXT), buf.readBoolean(), buf.readUtf(32), buf.readUtf(64), buf.readUtf(32),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean(), buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt(),
                buf.readBoolean(), buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readUtf(MAX_LONG_TEXT), buf.readBoolean(),
                buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readUtf(MAX_LONG_TEXT), buf.readUtf(MAX_LONG_TEXT),
                buf.readUtf(MAX_TEXT), readOptionalBinding(buf), readOptionalArea(buf), readStrings(buf),
                buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), readStrings(buf), readStrings(buf),
                readStrings(buf), readStrings(buf), readBindings(buf), readSlots(buf));
    }

    private static void writeItemSummary(FriendlyByteBuf buf, String id, int count) {
        buf.writeUtf(id == null ? "" : id, MAX_TEXT);
        buf.writeVarInt(count);
    }

    private static void writeOptionalBinding(FriendlyByteBuf buf, BindingState value) {
        buf.writeBoolean(value != null);
        if (value != null) writeBinding(buf, value);
    }
    private static BindingState readOptionalBinding(FriendlyByteBuf buf) { return buf.readBoolean() ? readBinding(buf) : null; }

    private static void writeOptionalArea(FriendlyByteBuf buf, GatheringAreaState value) {
        buf.writeBoolean(value != null);
        if (value == null) return;
        buf.writeUtf(value.dimension(), MAX_TEXT);
        buf.writeBoolean(value.hasCornerA());
        buf.writeInt(value.cornerAX()); buf.writeInt(value.cornerAY()); buf.writeInt(value.cornerAZ());
        buf.writeBoolean(value.hasCornerB());
        buf.writeInt(value.cornerBX()); buf.writeInt(value.cornerBY()); buf.writeInt(value.cornerBZ());
    }
    private static GatheringAreaState readOptionalArea(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        return new GatheringAreaState(buf.readUtf(MAX_TEXT), buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt());
    }

    private static void writeBindings(FriendlyByteBuf buf, List<BindingState> values) {
        List<BindingState> list = values == null ? List.of() : values;
        int count = Math.min(list.size(), MAX_BINDINGS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) writeBinding(buf, list.get(i));
    }
    private static List<BindingState> readBindings(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_BINDINGS) throw new IllegalArgumentException("Automata binding count out of range");
        List<BindingState> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(readBinding(buf));
        return List.copyOf(values);
    }

    private static void writeBinding(FriendlyByteBuf buf, BindingState value) {
        buf.writeUtf(value.dimension(), MAX_TEXT);
        buf.writeInt(value.x()); buf.writeInt(value.y()); buf.writeInt(value.z());
        buf.writeUtf(value.blockId(), MAX_TEXT); buf.writeUtf(value.itemId(), MAX_TEXT);
        buf.writeBoolean(value.loaded()); buf.writeBoolean(value.available()); buf.writeBoolean(value.llmEnabled());
        buf.writeUtf(value.llmPrompt(), MAX_LONG_TEXT);
        buf.writeVarInt(value.llmCachedItemIds()); buf.writeVarInt(value.llmCachedTags());
        writeStrings(buf, value.llmAllowedItemIds()); writeStrings(buf, value.llmDeniedItemIds());
        writeStrings(buf, value.llmAllowedTags()); writeStrings(buf, value.llmDeniedTags());
    }
    private static BindingState readBinding(FriendlyByteBuf buf) {
        return new BindingState(buf.readUtf(MAX_TEXT), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readUtf(MAX_LONG_TEXT), buf.readVarInt(), buf.readVarInt(), readStrings(buf), readStrings(buf),
                readStrings(buf), readStrings(buf));
    }

    private static void writeStrings(FriendlyByteBuf buf, List<String> values) {
        List<String> list = values == null ? List.of() : values.stream().filter(v -> v != null && !v.isBlank())
                .limit(MAX_VALUES).toList();
        buf.writeVarInt(list.size());
        for (String value : list) buf.writeUtf(value, MAX_TEXT);
    }
    private static List<String> readStrings(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_VALUES) throw new IllegalArgumentException("Automata value count out of range");
        List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(buf.readUtf(MAX_TEXT));
        return List.copyOf(values);
    }

    private static void writeSlots(FriendlyByteBuf buf, List<ObserverNativeScreenPayloads.SlotState> values) {
        List<ObserverNativeScreenPayloads.SlotState> list = values == null ? List.of() : values;
        int count = Math.min(list.size(), ObserverNativeScreenPayloads.MAX_SLOTS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            var slot = list.get(i);
            buf.writeVarInt(slot.index()); buf.writeVarInt(slot.x()); buf.writeVarInt(slot.y());
            buf.writeUtf(slot.itemId(), MAX_TEXT); buf.writeVarInt(slot.count()); buf.writeVarInt(slot.damage());
        }
    }
    private static List<ObserverNativeScreenPayloads.SlotState> readSlots(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > ObserverNativeScreenPayloads.MAX_SLOTS) throw new IllegalArgumentException("Automata slot count out of range");
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) slots.add(new ObserverNativeScreenPayloads.SlotState(
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt()));
        return List.copyOf(slots);
    }
}
