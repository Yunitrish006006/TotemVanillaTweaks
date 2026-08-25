package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Grindstone semantic transport: two inputs, result/error state, and menu slots. */
public final class ObserverGrindstoneScreenPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final long CAPABILITY = 1L << 14;
    public static final String FAMILY_ID = "grindstone";
    public static final String SCREEN_CLASS = "net.minecraft.client.gui.screens.inventory.GrindstoneScreen";
    private static final int MAX_TEXT = 256;

    private ObserverGrindstoneScreenPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record GrindstoneState(int protocolVersion, long sequence, boolean open, String familyId,
                                 String screenClass, String title, boolean primaryInputPresent,
                                 boolean secondaryInputPresent, boolean resultAvailable, boolean invalidCombination,
                                 List<ObserverNativeScreenPayloads.SlotState> slots) implements CustomPacketPayload {
        public static final Type<GrindstoneState> TYPE = new Type<>(id("observer_grindstone_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, GrindstoneState> CODEC = StreamCodec.of(
                ObserverGrindstoneScreenPayloads::writeState, ObserverGrindstoneScreenPayloads::readState);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record GrindstoneRelay(UUID targetId, int protocolVersion, long sequence, boolean open, String familyId,
                                 String screenClass, String title, boolean primaryInputPresent,
                                 boolean secondaryInputPresent, boolean resultAvailable, boolean invalidCombination,
                                 List<ObserverNativeScreenPayloads.SlotState> slots) implements CustomPacketPayload {
        public static final Type<GrindstoneRelay> TYPE = new Type<>(id("observer_grindstone_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, GrindstoneRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.primaryInputPresent(), value.secondaryInputPresent(),
                            value.resultAvailable(), value.invalidCombination(), value.slots());
                },
                buf -> relay(buf.readUUID(), readState(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static GrindstoneState closed(long sequence) {
        return new GrindstoneState(PROTOCOL_VERSION, sequence, false, FAMILY_ID, "", "",
                false, false, false, false, List.of());
    }

    public static GrindstoneRelay relay(UUID targetId, GrindstoneState state) {
        return new GrindstoneRelay(targetId, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.screenClass(), state.title(), state.primaryInputPresent(), state.secondaryInputPresent(),
                state.resultAvailable(), state.invalidCombination(), state.slots());
    }

    private static void writeState(FriendlyByteBuf buf, GrindstoneState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(),
                value.title(), value.primaryInputPresent(), value.secondaryInputPresent(), value.resultAvailable(),
                value.invalidCombination(), value.slots());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, boolean primaryInputPresent,
                                    boolean secondaryInputPresent, boolean resultAvailable, boolean invalidCombination,
                                    List<ObserverNativeScreenPayloads.SlotState> slots) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeBoolean(primaryInputPresent);
        buf.writeBoolean(secondaryInputPresent);
        buf.writeBoolean(resultAvailable);
        buf.writeBoolean(invalidCombination);
        int count = Math.min(slots.size(), ObserverNativeScreenPayloads.MAX_SLOTS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            var slot = slots.get(i);
            buf.writeVarInt(slot.index());
            buf.writeVarInt(slot.x());
            buf.writeVarInt(slot.y());
            buf.writeUtf(slot.itemId(), MAX_TEXT);
            buf.writeVarInt(slot.count());
            buf.writeVarInt(slot.damage());
        }
    }

    private static GrindstoneState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        boolean primaryInputPresent = buf.readBoolean();
        boolean secondaryInputPresent = buf.readBoolean();
        boolean resultAvailable = buf.readBoolean();
        boolean invalidCombination = buf.readBoolean();
        int slotCount = buf.readVarInt();
        if (slotCount < 0 || slotCount > ObserverNativeScreenPayloads.MAX_SLOTS) {
            throw new IllegalArgumentException("Observer grindstone slot count out of range: " + slotCount);
        }
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new ObserverNativeScreenPayloads.SlotState(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt()));
        }
        return new GrindstoneState(protocolVersion, sequence, open, familyId, screenClass, title,
                primaryInputPresent, secondaryInputPresent, resultAvailable, invalidCombination, List.copyOf(slots));
    }
}
