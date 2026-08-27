package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Loom semantic transport: selectable pattern catalogue, viewport/selection, and menu slots. */
public final class ObserverLoomScreenPayloads {
    public static final int PROTOCOL_VERSION = 2;
    public static final long CAPABILITY = 1L << 15;
    public static final String FAMILY_ID = "loom";
    public static final String SCREEN_CLASS = "net.minecraft.client.gui.screens.inventory.LoomScreen";
    public static final int MAX_PATTERNS = 512;
    public static final int MAX_BANNER_LAYERS = 6;
    private static final int MAX_TEXT = 256;

    private ObserverLoomScreenPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record PatternState(String registryId, String assetId) {}

    public record BannerLayerState(String assetId, int dyeColorId) {}

    public record LoomState(int protocolVersion, long sequence, boolean open, String familyId,
                            String screenClass, String title, int selectedPatternIndex, int startRow,
                            float scrollOffset, boolean displayPatterns, boolean hasMaxPatterns,
                            boolean resultAvailable, int resultBaseColorId, List<PatternState> patterns,
                            List<BannerLayerState> resultLayers,
                            List<ObserverNativeScreenPayloads.SlotState> slots)
            implements CustomPacketPayload {
        public static final Type<LoomState> TYPE = new Type<>(id("observer_loom_state_v2"));
        public static final StreamCodec<FriendlyByteBuf, LoomState> CODEC = StreamCodec.of(
                ObserverLoomScreenPayloads::writeState, ObserverLoomScreenPayloads::readState);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record LoomRelay(UUID targetId, int protocolVersion, long sequence, boolean open, String familyId,
                            String screenClass, String title, int selectedPatternIndex, int startRow,
                            float scrollOffset, boolean displayPatterns, boolean hasMaxPatterns,
                            boolean resultAvailable, int resultBaseColorId, List<PatternState> patterns,
                            List<BannerLayerState> resultLayers,
                            List<ObserverNativeScreenPayloads.SlotState> slots)
            implements CustomPacketPayload {
        public static final Type<LoomRelay> TYPE = new Type<>(id("observer_loom_relay_v2"));
        public static final StreamCodec<FriendlyByteBuf, LoomRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.selectedPatternIndex(), value.startRow(),
                            value.scrollOffset(), value.displayPatterns(), value.hasMaxPatterns(),
                            value.resultAvailable(), value.resultBaseColorId(), value.patterns(),
                            value.resultLayers(), value.slots());
                },
                buf -> relay(buf.readUUID(), readState(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static LoomState closed(long sequence) {
        return new LoomState(PROTOCOL_VERSION, sequence, false, FAMILY_ID, "", "", -1, 0,
                0.0F, false, false, false, -1, List.of(), List.of(), List.of());
    }

    public static LoomRelay relay(UUID targetId, LoomState state) {
        return new LoomRelay(targetId, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.screenClass(), state.title(), state.selectedPatternIndex(), state.startRow(),
                state.scrollOffset(), state.displayPatterns(), state.hasMaxPatterns(), state.resultAvailable(),
                state.resultBaseColorId(), state.patterns(), state.resultLayers(), state.slots());
    }

    private static void writeState(FriendlyByteBuf buf, LoomState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(),
                value.title(), value.selectedPatternIndex(), value.startRow(), value.scrollOffset(),
                value.displayPatterns(), value.hasMaxPatterns(), value.resultAvailable(), value.resultBaseColorId(),
                value.patterns(), value.resultLayers(), value.slots());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, int selectedPatternIndex,
                                    int startRow, float scrollOffset, boolean displayPatterns,
                                    boolean hasMaxPatterns, boolean resultAvailable, int resultBaseColorId,
                                    List<PatternState> patterns, List<BannerLayerState> resultLayers,
                                    List<ObserverNativeScreenPayloads.SlotState> slots) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeVarInt(selectedPatternIndex);
        buf.writeVarInt(startRow);
        buf.writeFloat(scrollOffset);
        buf.writeBoolean(displayPatterns);
        buf.writeBoolean(hasMaxPatterns);
        buf.writeBoolean(resultAvailable);
        buf.writeVarInt(resultBaseColorId);
        int patternCount = Math.min(patterns.size(), MAX_PATTERNS);
        buf.writeVarInt(patternCount);
        for (int i = 0; i < patternCount; i++) {
            PatternState pattern = patterns.get(i);
            buf.writeUtf(pattern.registryId(), MAX_TEXT);
            buf.writeUtf(pattern.assetId(), MAX_TEXT);
        }
        int layerCount = Math.min(resultLayers.size(), MAX_BANNER_LAYERS);
        buf.writeVarInt(layerCount);
        for (int i = 0; i < layerCount; i++) {
            BannerLayerState layer = resultLayers.get(i);
            buf.writeUtf(layer.assetId(), MAX_TEXT);
            buf.writeVarInt(layer.dyeColorId());
        }
        int slotCount = Math.min(slots.size(), ObserverNativeScreenPayloads.MAX_SLOTS);
        buf.writeVarInt(slotCount);
        for (int i = 0; i < slotCount; i++) {
            var slot = slots.get(i);
            buf.writeVarInt(slot.index());
            buf.writeVarInt(slot.x());
            buf.writeVarInt(slot.y());
            buf.writeUtf(slot.itemId(), MAX_TEXT);
            buf.writeVarInt(slot.count());
            buf.writeVarInt(slot.damage());
        }
    }

    private static LoomState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        int selectedPatternIndex = buf.readVarInt();
        int startRow = buf.readVarInt();
        float scrollOffset = buf.readFloat();
        boolean displayPatterns = buf.readBoolean();
        boolean hasMaxPatterns = buf.readBoolean();
        boolean resultAvailable = buf.readBoolean();
        int resultBaseColorId = buf.readVarInt();
        int patternCount = buf.readVarInt();
        if (patternCount < 0 || patternCount > MAX_PATTERNS) {
            throw new IllegalArgumentException("Observer loom pattern count out of range: " + patternCount);
        }
        List<PatternState> patterns = new ArrayList<>(patternCount);
        for (int i = 0; i < patternCount; i++) {
            patterns.add(new PatternState(buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT)));
        }
        int layerCount = buf.readVarInt();
        if (layerCount < 0 || layerCount > MAX_BANNER_LAYERS) {
            throw new IllegalArgumentException("Observer loom result layer count out of range: " + layerCount);
        }
        List<BannerLayerState> resultLayers = new ArrayList<>(layerCount);
        for (int i = 0; i < layerCount; i++) {
            resultLayers.add(new BannerLayerState(buf.readUtf(MAX_TEXT), buf.readVarInt()));
        }
        int slotCount = buf.readVarInt();
        if (slotCount < 0 || slotCount > ObserverNativeScreenPayloads.MAX_SLOTS) {
            throw new IllegalArgumentException("Observer loom slot count out of range: " + slotCount);
        }
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new ObserverNativeScreenPayloads.SlotState(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt()));
        }
        return new LoomState(protocolVersion, sequence, open, familyId, screenClass, title, selectedPatternIndex,
                startRow, scrollOffset, displayPatterns, hasMaxPatterns, resultAvailable, resultBaseColorId,
                List.copyOf(patterns), List.copyOf(resultLayers), List.copyOf(slots));
    }
}
