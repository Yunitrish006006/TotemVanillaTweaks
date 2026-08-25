package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Beacon semantic transport: tier, pending effects, payment and menu slots. */
public final class ObserverBeaconScreenPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final long CAPABILITY = 1L << 17;
    public static final String FAMILY_ID = "beacon";
    public static final String SCREEN_CLASS = "net.minecraft.client.gui.screens.inventory.BeaconScreen";
    private static final int MAX_TEXT = 256;

    private ObserverBeaconScreenPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record BeaconState(int protocolVersion, long sequence, boolean open, String familyId,
                              String screenClass, String title, int levels, String primaryEffectId,
                              String secondaryEffectId, boolean paymentPresent, boolean canConfirm,
                              List<ObserverNativeScreenPayloads.SlotState> slots) implements CustomPacketPayload {
        public static final Type<BeaconState> TYPE = new Type<>(id("observer_beacon_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, BeaconState> CODEC = StreamCodec.of(
                ObserverBeaconScreenPayloads::writeState, ObserverBeaconScreenPayloads::readState);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BeaconRelay(UUID targetId, int protocolVersion, long sequence, boolean open, String familyId,
                              String screenClass, String title, int levels, String primaryEffectId,
                              String secondaryEffectId, boolean paymentPresent, boolean canConfirm,
                              List<ObserverNativeScreenPayloads.SlotState> slots) implements CustomPacketPayload {
        public static final Type<BeaconRelay> TYPE = new Type<>(id("observer_beacon_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, BeaconRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.levels(), value.primaryEffectId(),
                            value.secondaryEffectId(), value.paymentPresent(), value.canConfirm(), value.slots());
                },
                buf -> relay(buf.readUUID(), readState(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static BeaconState closed(long sequence) {
        return new BeaconState(PROTOCOL_VERSION, sequence, false, FAMILY_ID, "", "", 0, "", "",
                false, false, List.of());
    }

    public static BeaconRelay relay(UUID targetId, BeaconState state) {
        return new BeaconRelay(targetId, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.screenClass(), state.title(), state.levels(), state.primaryEffectId(), state.secondaryEffectId(),
                state.paymentPresent(), state.canConfirm(), state.slots());
    }

    private static void writeState(FriendlyByteBuf buf, BeaconState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(),
                value.title(), value.levels(), value.primaryEffectId(), value.secondaryEffectId(),
                value.paymentPresent(), value.canConfirm(), value.slots());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, int levels,
                                    String primaryEffectId, String secondaryEffectId, boolean paymentPresent,
                                    boolean canConfirm, List<ObserverNativeScreenPayloads.SlotState> slots) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeVarInt(levels);
        buf.writeUtf(primaryEffectId, MAX_TEXT);
        buf.writeUtf(secondaryEffectId, MAX_TEXT);
        buf.writeBoolean(paymentPresent);
        buf.writeBoolean(canConfirm);
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

    private static BeaconState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        int levels = buf.readVarInt();
        String primaryEffectId = buf.readUtf(MAX_TEXT);
        String secondaryEffectId = buf.readUtf(MAX_TEXT);
        boolean paymentPresent = buf.readBoolean();
        boolean canConfirm = buf.readBoolean();
        int slotCount = buf.readVarInt();
        if (slotCount < 0 || slotCount > ObserverNativeScreenPayloads.MAX_SLOTS) {
            throw new IllegalArgumentException("Observer beacon slot count out of range: " + slotCount);
        }
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new ObserverNativeScreenPayloads.SlotState(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt()));
        }
        return new BeaconState(protocolVersion, sequence, open, familyId, screenClass, title, levels,
                primaryEffectId, secondaryEffectId, paymentPresent, canConfirm, List.copyOf(slots));
    }
}
