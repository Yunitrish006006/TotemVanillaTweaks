package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Optional TotemRemnant Backpack semantic transport; no hard TotemRemnant dependency. */
public final class ObserverRemnantBackpackPayloads {
    public static final int PROTOCOL_VERSION = 1;
    private static final int MAX_TEXT = 256;

    private ObserverRemnantBackpackPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record BackpackState(
            int protocolVersion,
            long sequence,
            boolean open,
            String familyId,
            String screenClass,
            String title,
            int rowCount,
            int visibleRows,
            int firstVisibleRow,
            int upgradeSlotCount,
            boolean craftingEnabled,
            boolean enderAccessVisible,
            List<ObserverNativeScreenPayloads.SlotState> slots
    ) implements CustomPacketPayload {
        public static final Type<BackpackState> TYPE = new Type<>(id("observer_remnant_backpack_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, BackpackState> CODEC = StreamCodec.of(
                ObserverRemnantBackpackPayloads::writeState,
                ObserverRemnantBackpackPayloads::readState
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BackpackRelay(
            UUID targetId,
            int protocolVersion,
            long sequence,
            boolean open,
            String familyId,
            String screenClass,
            String title,
            int rowCount,
            int visibleRows,
            int firstVisibleRow,
            int upgradeSlotCount,
            boolean craftingEnabled,
            boolean enderAccessVisible,
            List<ObserverNativeScreenPayloads.SlotState> slots
    ) implements CustomPacketPayload {
        public static final Type<BackpackRelay> TYPE = new Type<>(id("observer_remnant_backpack_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, BackpackRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.rowCount(), value.visibleRows(), value.firstVisibleRow(),
                            value.upgradeSlotCount(), value.craftingEnabled(), value.enderAccessVisible(), value.slots());
                },
                buf -> {
                    UUID targetId = buf.readUUID();
                    BackpackState state = readState(buf);
                    return new BackpackRelay(targetId, state.protocolVersion(), state.sequence(), state.open(),
                            state.familyId(), state.screenClass(), state.title(), state.rowCount(), state.visibleRows(),
                            state.firstVisibleRow(), state.upgradeSlotCount(), state.craftingEnabled(),
                            state.enderAccessVisible(), state.slots());
                }
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void writeState(FriendlyByteBuf buf, BackpackState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(),
                value.title(), value.rowCount(), value.visibleRows(), value.firstVisibleRow(), value.upgradeSlotCount(),
                value.craftingEnabled(), value.enderAccessVisible(), value.slots());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, int rowCount, int visibleRows,
                                    int firstVisibleRow, int upgradeSlotCount, boolean craftingEnabled,
                                    boolean enderAccessVisible, List<ObserverNativeScreenPayloads.SlotState> slots) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeVarInt(rowCount);
        buf.writeVarInt(visibleRows);
        buf.writeVarInt(firstVisibleRow);
        buf.writeVarInt(upgradeSlotCount);
        buf.writeBoolean(craftingEnabled);
        buf.writeBoolean(enderAccessVisible);
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

    private static BackpackState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        int rowCount = buf.readVarInt();
        int visibleRows = buf.readVarInt();
        int firstVisibleRow = buf.readVarInt();
        int upgradeSlotCount = buf.readVarInt();
        boolean craftingEnabled = buf.readBoolean();
        boolean enderAccessVisible = buf.readBoolean();
        int slotCount = buf.readVarInt();
        if (slotCount < 0 || slotCount > ObserverNativeScreenPayloads.MAX_SLOTS) {
            throw new IllegalArgumentException("Observer Remnant backpack slot count out of range: " + slotCount);
        }
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new ObserverNativeScreenPayloads.SlotState(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt()));
        }
        return new BackpackState(protocolVersion, sequence, open, familyId, screenClass, title, rowCount, visibleRows,
                firstVisibleRow, upgradeSlotCount, craftingEnabled, enderAccessVisible, List.copyOf(slots));
    }
}
