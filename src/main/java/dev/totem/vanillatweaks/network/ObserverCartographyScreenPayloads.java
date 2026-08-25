package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Cartography Table semantic transport: requested map operation plus menu slots. */
public final class ObserverCartographyScreenPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final long CAPABILITY = 1L << 16;
    public static final String FAMILY_ID = "cartography";
    public static final String SCREEN_CLASS = "net.minecraft.client.gui.screens.inventory.CartographyTableScreen";
    private static final int MAX_TEXT = 256;

    private ObserverCartographyScreenPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record CartographyState(int protocolVersion, long sequence, boolean open, String familyId,
                                   String screenClass, String title, String operation,
                                   boolean mapPresent, boolean additionalPresent, boolean resultAvailable,
                                   List<ObserverNativeScreenPayloads.SlotState> slots) implements CustomPacketPayload {
        public static final Type<CartographyState> TYPE = new Type<>(id("observer_cartography_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, CartographyState> CODEC = StreamCodec.of(
                ObserverCartographyScreenPayloads::writeState, ObserverCartographyScreenPayloads::readState);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CartographyRelay(UUID targetId, int protocolVersion, long sequence, boolean open, String familyId,
                                   String screenClass, String title, String operation,
                                   boolean mapPresent, boolean additionalPresent, boolean resultAvailable,
                                   List<ObserverNativeScreenPayloads.SlotState> slots) implements CustomPacketPayload {
        public static final Type<CartographyRelay> TYPE = new Type<>(id("observer_cartography_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, CartographyRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.operation(), value.mapPresent(),
                            value.additionalPresent(), value.resultAvailable(), value.slots());
                },
                buf -> relay(buf.readUUID(), readState(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static CartographyState closed(long sequence) {
        return new CartographyState(PROTOCOL_VERSION, sequence, false, FAMILY_ID, "", "", "none",
                false, false, false, List.of());
    }

    public static CartographyRelay relay(UUID targetId, CartographyState state) {
        return new CartographyRelay(targetId, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.screenClass(), state.title(), state.operation(), state.mapPresent(), state.additionalPresent(),
                state.resultAvailable(), state.slots());
    }

    private static void writeState(FriendlyByteBuf buf, CartographyState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(),
                value.title(), value.operation(), value.mapPresent(), value.additionalPresent(),
                value.resultAvailable(), value.slots());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, String operation,
                                    boolean mapPresent, boolean additionalPresent, boolean resultAvailable,
                                    List<ObserverNativeScreenPayloads.SlotState> slots) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeUtf(operation, MAX_TEXT);
        buf.writeBoolean(mapPresent);
        buf.writeBoolean(additionalPresent);
        buf.writeBoolean(resultAvailable);
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

    private static CartographyState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        String operation = buf.readUtf(MAX_TEXT);
        boolean mapPresent = buf.readBoolean();
        boolean additionalPresent = buf.readBoolean();
        boolean resultAvailable = buf.readBoolean();
        int slotCount = buf.readVarInt();
        if (slotCount < 0 || slotCount > ObserverNativeScreenPayloads.MAX_SLOTS) {
            throw new IllegalArgumentException("Observer cartography slot count out of range: " + slotCount);
        }
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new ObserverNativeScreenPayloads.SlotState(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt()));
        }
        return new CartographyState(protocolVersion, sequence, open, familyId, screenClass, title, operation,
                mapPresent, additionalPresent, resultAvailable, List.copyOf(slots));
    }
}
