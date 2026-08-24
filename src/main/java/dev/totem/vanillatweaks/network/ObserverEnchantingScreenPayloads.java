package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Structured enchanting-table transport with all three vanilla offer semantics. */
public final class ObserverEnchantingScreenPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final int OPTION_COUNT = 3;
    private static final int MAX_TEXT = 256;

    private ObserverEnchantingScreenPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record OptionState(int index, int cost, int enchantClue, int levelClue, boolean affordable) {}

    public record EnchantingState(
            int protocolVersion,
            long sequence,
            boolean open,
            String familyId,
            String screenClass,
            String title,
            int playerLevel,
            int lapisCount,
            List<OptionState> options,
            List<ObserverNativeScreenPayloads.SlotState> slots
    ) implements CustomPacketPayload {
        public static final Type<EnchantingState> TYPE = new Type<>(id("observer_native_enchanting_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, EnchantingState> CODEC = StreamCodec.of(
                ObserverEnchantingScreenPayloads::writeState,
                ObserverEnchantingScreenPayloads::readState
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record EnchantingRelay(
            UUID targetId,
            int protocolVersion,
            long sequence,
            boolean open,
            String familyId,
            String screenClass,
            String title,
            int playerLevel,
            int lapisCount,
            List<OptionState> options,
            List<ObserverNativeScreenPayloads.SlotState> slots
    ) implements CustomPacketPayload {
        public static final Type<EnchantingRelay> TYPE = new Type<>(id("observer_native_enchanting_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, EnchantingRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.playerLevel(), value.lapisCount(),
                            value.options(), value.slots());
                },
                buf -> {
                    UUID targetId = buf.readUUID();
                    EnchantingState state = readState(buf);
                    return new EnchantingRelay(targetId, state.protocolVersion(), state.sequence(), state.open(),
                            state.familyId(), state.screenClass(), state.title(), state.playerLevel(), state.lapisCount(),
                            state.options(), state.slots());
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void writeState(FriendlyByteBuf buf, EnchantingState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(),
                value.title(), value.playerLevel(), value.lapisCount(), value.options(), value.slots());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, int playerLevel, int lapisCount,
                                    List<OptionState> options, List<ObserverNativeScreenPayloads.SlotState> slots) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeVarInt(playerLevel);
        buf.writeVarInt(lapisCount);
        int optionCount = Math.min(options.size(), OPTION_COUNT);
        buf.writeVarInt(optionCount);
        for (int i = 0; i < optionCount; i++) {
            OptionState option = options.get(i);
            buf.writeVarInt(option.index());
            buf.writeVarInt(option.cost());
            buf.writeVarInt(option.enchantClue());
            buf.writeVarInt(option.levelClue());
            buf.writeBoolean(option.affordable());
        }
        int slotCount = Math.min(slots.size(), ObserverNativeScreenPayloads.MAX_SLOTS);
        buf.writeVarInt(slotCount);
        for (int i = 0; i < slotCount; i++) {
            ObserverNativeScreenPayloads.SlotState slot = slots.get(i);
            buf.writeVarInt(slot.index());
            buf.writeVarInt(slot.x());
            buf.writeVarInt(slot.y());
            buf.writeUtf(slot.itemId(), MAX_TEXT);
            buf.writeVarInt(slot.count());
            buf.writeVarInt(slot.damage());
        }
    }

    private static EnchantingState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        int playerLevel = buf.readVarInt();
        int lapisCount = buf.readVarInt();
        int optionCount = buf.readVarInt();
        if (optionCount < 0 || optionCount > OPTION_COUNT) {
            throw new IllegalArgumentException("Observer enchanting option count out of range: " + optionCount);
        }
        List<OptionState> options = new ArrayList<>(optionCount);
        for (int i = 0; i < optionCount; i++) {
            options.add(new OptionState(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        }
        int slotCount = buf.readVarInt();
        if (slotCount < 0 || slotCount > ObserverNativeScreenPayloads.MAX_SLOTS) {
            throw new IllegalArgumentException("Observer enchanting slot count out of range: " + slotCount);
        }
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new ObserverNativeScreenPayloads.SlotState(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt()));
        }
        return new EnchantingState(protocolVersion, sequence, open, familyId, screenClass, title, playerLevel,
                lapisCount, List.copyOf(options), List.copyOf(slots));
    }
}
