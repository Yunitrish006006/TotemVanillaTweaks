package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Smithing-table semantic transport: four smithing slots, result/error state, and player inventory. */
public final class ObserverSmithingScreenPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final long CAPABILITY = 1L << 12;
    public static final String FAMILY_ID = "smithing";
    public static final String SCREEN_CLASS = "net.minecraft.client.gui.screens.inventory.SmithingScreen";
    private static final int MAX_TEXT = 256;

    private ObserverSmithingScreenPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record SmithingState(int protocolVersion, long sequence, boolean open, String familyId,
                                String screenClass, String title, boolean recipeError, boolean resultAvailable,
                                List<ObserverNativeScreenPayloads.SlotState> slots) implements CustomPacketPayload {
        public static final Type<SmithingState> TYPE = new Type<>(id("observer_smithing_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, SmithingState> CODEC = StreamCodec.of(
                ObserverSmithingScreenPayloads::writeState, ObserverSmithingScreenPayloads::readState);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SmithingRelay(UUID targetId, int protocolVersion, long sequence, boolean open, String familyId,
                                String screenClass, String title, boolean recipeError, boolean resultAvailable,
                                List<ObserverNativeScreenPayloads.SlotState> slots) implements CustomPacketPayload {
        public static final Type<SmithingRelay> TYPE = new Type<>(id("observer_smithing_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, SmithingRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.recipeError(), value.resultAvailable(), value.slots());
                },
                buf -> {
                    UUID targetId = buf.readUUID();
                    return relay(targetId, readState(buf));
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static SmithingState closed(long sequence) {
        return new SmithingState(PROTOCOL_VERSION, sequence, false, FAMILY_ID, "", "", false, false, List.of());
    }

    public static SmithingRelay relay(UUID targetId, SmithingState state) {
        return new SmithingRelay(targetId, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.screenClass(), state.title(), state.recipeError(), state.resultAvailable(), state.slots());
    }

    private static void writeState(FriendlyByteBuf buf, SmithingState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(),
                value.title(), value.recipeError(), value.resultAvailable(), value.slots());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, boolean recipeError,
                                    boolean resultAvailable, List<ObserverNativeScreenPayloads.SlotState> slots) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeBoolean(recipeError);
        buf.writeBoolean(resultAvailable);
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

    private static SmithingState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        boolean recipeError = buf.readBoolean();
        boolean resultAvailable = buf.readBoolean();
        int slotCount = buf.readVarInt();
        if (slotCount < 0 || slotCount > ObserverNativeScreenPayloads.MAX_SLOTS) {
            throw new IllegalArgumentException("Observer smithing slot count out of range: " + slotCount);
        }
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new ObserverNativeScreenPayloads.SlotState(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt()));
        }
        return new SmithingState(protocolVersion, sequence, open, familyId, screenClass, title,
                recipeError, resultAvailable, List.copyOf(slots));
    }
}
