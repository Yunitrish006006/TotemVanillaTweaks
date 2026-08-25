package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Brewing-stand semantic transport: slots plus brewing/fuel progress. */
public final class ObserverBrewingScreenPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final long CAPABILITY = 1L << 11;
    public static final String FAMILY_ID = "brewing";
    public static final String SCREEN_CLASS = "net.minecraft.client.gui.screens.inventory.BrewingStandScreen";
    public static final int MAX_BREW_TICKS = 400;
    public static final int MAX_FUEL = 20;
    private static final int MAX_TEXT = 256;

    private ObserverBrewingScreenPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record BrewingState(int protocolVersion, long sequence, boolean open, String familyId,
                               String screenClass, String title, int brewingTicks, int fuel,
                               List<ObserverNativeScreenPayloads.SlotState> slots) implements CustomPacketPayload {
        public static final Type<BrewingState> TYPE = new Type<>(id("observer_brewing_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, BrewingState> CODEC = StreamCodec.of(
                ObserverBrewingScreenPayloads::writeState, ObserverBrewingScreenPayloads::readState);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BrewingRelay(UUID targetId, int protocolVersion, long sequence, boolean open, String familyId,
                               String screenClass, String title, int brewingTicks, int fuel,
                               List<ObserverNativeScreenPayloads.SlotState> slots) implements CustomPacketPayload {
        public static final Type<BrewingRelay> TYPE = new Type<>(id("observer_brewing_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, BrewingRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.brewingTicks(), value.fuel(), value.slots());
                },
                buf -> {
                    UUID targetId = buf.readUUID();
                    return relay(targetId, readState(buf));
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static BrewingState closed(long sequence) {
        return new BrewingState(PROTOCOL_VERSION, sequence, false, FAMILY_ID, "", "", 0, 0, List.of());
    }

    public static BrewingRelay relay(UUID targetId, BrewingState state) {
        return new BrewingRelay(targetId, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.screenClass(), state.title(), state.brewingTicks(), state.fuel(), state.slots());
    }

    private static void writeState(FriendlyByteBuf buf, BrewingState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(),
                value.title(), value.brewingTicks(), value.fuel(), value.slots());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, int brewingTicks, int fuel,
                                    List<ObserverNativeScreenPayloads.SlotState> slots) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeVarInt(brewingTicks);
        buf.writeVarInt(fuel);
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

    private static BrewingState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        int brewingTicks = buf.readVarInt();
        int fuel = buf.readVarInt();
        int slotCount = buf.readVarInt();
        if (slotCount < 0 || slotCount > ObserverNativeScreenPayloads.MAX_SLOTS) {
            throw new IllegalArgumentException("Observer brewing slot count out of range: " + slotCount);
        }
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new ObserverNativeScreenPayloads.SlotState(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt()));
        }
        return new BrewingState(protocolVersion, sequence, open, familyId, screenClass, title,
                brewingTicks, fuel, List.copyOf(slots));
    }
}
