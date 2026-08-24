package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Structured anvil transport with rename and level-cost semantics. */
public final class ObserverAnvilScreenPayloads {
    public static final int PROTOCOL_VERSION = 1;
    private static final int MAX_TEXT = 256;

    private ObserverAnvilScreenPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record AnvilState(
            int protocolVersion,
            long sequence,
            boolean open,
            String familyId,
            String screenClass,
            String title,
            String itemName,
            int levelCost,
            boolean tooExpensive,
            boolean resultAvailable,
            List<ObserverNativeScreenPayloads.SlotState> slots
    ) implements CustomPacketPayload {
        public static final Type<AnvilState> TYPE = new Type<>(id("observer_native_anvil_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, AnvilState> CODEC = StreamCodec.of(
                ObserverAnvilScreenPayloads::writeState,
                ObserverAnvilScreenPayloads::readState
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record AnvilRelay(
            UUID targetId,
            int protocolVersion,
            long sequence,
            boolean open,
            String familyId,
            String screenClass,
            String title,
            String itemName,
            int levelCost,
            boolean tooExpensive,
            boolean resultAvailable,
            List<ObserverNativeScreenPayloads.SlotState> slots
    ) implements CustomPacketPayload {
        public static final Type<AnvilRelay> TYPE = new Type<>(id("observer_native_anvil_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, AnvilRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.itemName(), value.levelCost(),
                            value.tooExpensive(), value.resultAvailable(), value.slots());
                },
                buf -> {
                    UUID targetId = buf.readUUID();
                    AnvilState state = readState(buf);
                    return new AnvilRelay(targetId, state.protocolVersion(), state.sequence(), state.open(),
                            state.familyId(), state.screenClass(), state.title(), state.itemName(), state.levelCost(),
                            state.tooExpensive(), state.resultAvailable(), state.slots());
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void writeState(FriendlyByteBuf buf, AnvilState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                value.screenClass(), value.title(), value.itemName(), value.levelCost(), value.tooExpensive(),
                value.resultAvailable(), value.slots());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, String itemName,
                                    int levelCost, boolean tooExpensive, boolean resultAvailable,
                                    List<ObserverNativeScreenPayloads.SlotState> slots) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeUtf(itemName, MAX_TEXT);
        buf.writeVarInt(levelCost);
        buf.writeBoolean(tooExpensive);
        buf.writeBoolean(resultAvailable);
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

    private static AnvilState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        String itemName = buf.readUtf(MAX_TEXT);
        int levelCost = buf.readVarInt();
        boolean tooExpensive = buf.readBoolean();
        boolean resultAvailable = buf.readBoolean();
        int slotCount = buf.readVarInt();
        if (slotCount < 0 || slotCount > ObserverNativeScreenPayloads.MAX_SLOTS) {
            throw new IllegalArgumentException("Observer anvil slot count out of range: " + slotCount);
        }
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new ObserverNativeScreenPayloads.SlotState(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt()));
        }
        return new AnvilState(protocolVersion, sequence, open, familyId, screenClass, title, itemName, levelCost,
                tooExpensive, resultAvailable, List.copyOf(slots));
    }
}
