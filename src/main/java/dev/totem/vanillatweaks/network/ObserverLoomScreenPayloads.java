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
    public static final int PROTOCOL_VERSION = 1;
    public static final long CAPABILITY = 1L << 15;
    public static final String FAMILY_ID = "loom";
    public static final String SCREEN_CLASS = "net.minecraft.client.gui.screens.inventory.LoomScreen";
    public static final int MAX_PATTERNS = 512;
    private static final int MAX_TEXT = 256;

    private ObserverLoomScreenPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record LoomState(int protocolVersion, long sequence, boolean open, String familyId,
                            String screenClass, String title, int selectedPatternIndex, int startRow,
                            boolean displayPatterns, boolean hasMaxPatterns, boolean resultAvailable,
                            List<String> patternIds, List<ObserverNativeScreenPayloads.SlotState> slots)
            implements CustomPacketPayload {
        public static final Type<LoomState> TYPE = new Type<>(id("observer_loom_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, LoomState> CODEC = StreamCodec.of(
                ObserverLoomScreenPayloads::writeState, ObserverLoomScreenPayloads::readState);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record LoomRelay(UUID targetId, int protocolVersion, long sequence, boolean open, String familyId,
                            String screenClass, String title, int selectedPatternIndex, int startRow,
                            boolean displayPatterns, boolean hasMaxPatterns, boolean resultAvailable,
                            List<String> patternIds, List<ObserverNativeScreenPayloads.SlotState> slots)
            implements CustomPacketPayload {
        public static final Type<LoomRelay> TYPE = new Type<>(id("observer_loom_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, LoomRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.selectedPatternIndex(), value.startRow(),
                            value.displayPatterns(), value.hasMaxPatterns(), value.resultAvailable(),
                            value.patternIds(), value.slots());
                },
                buf -> relay(buf.readUUID(), readState(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static LoomState closed(long sequence) {
        return new LoomState(PROTOCOL_VERSION, sequence, false, FAMILY_ID, "", "", -1, 0,
                false, false, false, List.of(), List.of());
    }

    public static LoomRelay relay(UUID targetId, LoomState state) {
        return new LoomRelay(targetId, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.screenClass(), state.title(), state.selectedPatternIndex(), state.startRow(),
                state.displayPatterns(), state.hasMaxPatterns(), state.resultAvailable(),
                state.patternIds(), state.slots());
    }

    private static void writeState(FriendlyByteBuf buf, LoomState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(),
                value.title(), value.selectedPatternIndex(), value.startRow(), value.displayPatterns(),
                value.hasMaxPatterns(), value.resultAvailable(), value.patternIds(), value.slots());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, int selectedPatternIndex,
                                    int startRow, boolean displayPatterns, boolean hasMaxPatterns,
                                    boolean resultAvailable, List<String> patternIds,
                                    List<ObserverNativeScreenPayloads.SlotState> slots) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeVarInt(selectedPatternIndex);
        buf.writeVarInt(startRow);
        buf.writeBoolean(displayPatterns);
        buf.writeBoolean(hasMaxPatterns);
        buf.writeBoolean(resultAvailable);
        int patternCount = Math.min(patternIds.size(), MAX_PATTERNS);
        buf.writeVarInt(patternCount);
        for (int i = 0; i < patternCount; i++) buf.writeUtf(patternIds.get(i), MAX_TEXT);
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
        boolean displayPatterns = buf.readBoolean();
        boolean hasMaxPatterns = buf.readBoolean();
        boolean resultAvailable = buf.readBoolean();
        int patternCount = buf.readVarInt();
        if (patternCount < 0 || patternCount > MAX_PATTERNS) {
            throw new IllegalArgumentException("Observer loom pattern count out of range: " + patternCount);
        }
        List<String> patternIds = new ArrayList<>(patternCount);
        for (int i = 0; i < patternCount; i++) patternIds.add(buf.readUtf(MAX_TEXT));
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
                startRow, displayPatterns, hasMaxPatterns, resultAvailable,
                List.copyOf(patternIds), List.copyOf(slots));
    }
}
