package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Crafter semantic transport: powered state, disabled 3x3 cells, and full menu slots. */
public final class ObserverCrafterScreenPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final long CAPABILITY = 1L << 19;
    public static final String FAMILY_ID = "crafter";
    public static final String SCREEN_CLASS = "net.minecraft.client.gui.screens.inventory.CrafterScreen";
    private static final int MAX_TEXT = 256;

    private ObserverCrafterScreenPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record CrafterState(int protocolVersion, long sequence, boolean open, String familyId,
                               String screenClass, String title, boolean powered, int disabledMask,
                               int occupiedInputSlots,
                               List<ObserverNativeScreenPayloads.SlotState> slots) implements CustomPacketPayload {
        public static final Type<CrafterState> TYPE = new Type<>(id("observer_crafter_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, CrafterState> CODEC = StreamCodec.of(
                ObserverCrafterScreenPayloads::writeState, ObserverCrafterScreenPayloads::readState);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CrafterRelay(UUID targetId, int protocolVersion, long sequence, boolean open, String familyId,
                               String screenClass, String title, boolean powered, int disabledMask,
                               int occupiedInputSlots,
                               List<ObserverNativeScreenPayloads.SlotState> slots) implements CustomPacketPayload {
        public static final Type<CrafterRelay> TYPE = new Type<>(id("observer_crafter_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, CrafterRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.powered(), value.disabledMask(),
                            value.occupiedInputSlots(), value.slots());
                },
                buf -> relay(buf.readUUID(), readState(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static CrafterState closed(long sequence) {
        return new CrafterState(PROTOCOL_VERSION, sequence, false, FAMILY_ID, "", "", false, 0, 0, List.of());
    }

    public static CrafterRelay relay(UUID targetId, CrafterState state) {
        return new CrafterRelay(targetId, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.screenClass(), state.title(), state.powered(), state.disabledMask(),
                state.occupiedInputSlots(), state.slots());
    }

    private static void writeState(FriendlyByteBuf buf, CrafterState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(),
                value.title(), value.powered(), value.disabledMask(), value.occupiedInputSlots(), value.slots());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, boolean powered,
                                    int disabledMask, int occupiedInputSlots,
                                    List<ObserverNativeScreenPayloads.SlotState> slots) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeBoolean(powered);
        buf.writeVarInt(disabledMask);
        buf.writeVarInt(occupiedInputSlots);
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

    private static CrafterState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        boolean powered = buf.readBoolean();
        int disabledMask = buf.readVarInt();
        int occupiedInputSlots = buf.readVarInt();
        int slotCount = buf.readVarInt();
        if (slotCount < 0 || slotCount > ObserverNativeScreenPayloads.MAX_SLOTS) {
            throw new IllegalArgumentException("Observer crafter slot count out of range: " + slotCount);
        }
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new ObserverNativeScreenPayloads.SlotState(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt()));
        }
        return new CrafterState(protocolVersion, sequence, open, familyId, screenClass, title, powered,
                disabledMask, occupiedInputSlots, List.copyOf(slots));
    }
}
