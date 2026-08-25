package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Optional TotemVillagers Woodcutter semantic transport; no hard TotemVillagers dependency. */
public final class ObserverVillagersWoodcutterPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final long CAPABILITY = 1L << 10;
    public static final String FAMILY_ID = "villagers_woodcutter";
    public static final String SCREEN_CLASS = "dev.totem.villagers.client.WoodcutterScreen";
    private static final int MAX_TEXT = 256;

    private ObserverVillagersWoodcutterPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record WoodcutterState(
            int protocolVersion,
            long sequence,
            boolean open,
            String familyId,
            String screenClass,
            String title,
            int selectedRecipeIndex,
            int recipeCount,
            int requiredInputCount,
            boolean hasInputItem,
            List<ObserverNativeScreenPayloads.SlotState> slots
    ) implements CustomPacketPayload {
        public static final Type<WoodcutterState> TYPE = new Type<>(id("observer_villagers_woodcutter_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, WoodcutterState> CODEC = StreamCodec.of(
                ObserverVillagersWoodcutterPayloads::writeState,
                ObserverVillagersWoodcutterPayloads::readState
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record WoodcutterRelay(
            UUID targetId,
            int protocolVersion,
            long sequence,
            boolean open,
            String familyId,
            String screenClass,
            String title,
            int selectedRecipeIndex,
            int recipeCount,
            int requiredInputCount,
            boolean hasInputItem,
            List<ObserverNativeScreenPayloads.SlotState> slots
    ) implements CustomPacketPayload {
        public static final Type<WoodcutterRelay> TYPE = new Type<>(id("observer_villagers_woodcutter_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, WoodcutterRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.selectedRecipeIndex(), value.recipeCount(),
                            value.requiredInputCount(), value.hasInputItem(), value.slots());
                },
                buf -> {
                    UUID targetId = buf.readUUID();
                    WoodcutterState state = readState(buf);
                    return relay(targetId, state);
                }
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static WoodcutterState closed(long sequence) {
        return new WoodcutterState(PROTOCOL_VERSION, sequence, false, FAMILY_ID, "", "",
                -1, 0, 0, false, List.of());
    }

    public static WoodcutterRelay relay(UUID targetId, WoodcutterState state) {
        return new WoodcutterRelay(targetId, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.screenClass(), state.title(), state.selectedRecipeIndex(), state.recipeCount(),
                state.requiredInputCount(), state.hasInputItem(), state.slots());
    }

    private static void writeState(FriendlyByteBuf buf, WoodcutterState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(),
                value.title(), value.selectedRecipeIndex(), value.recipeCount(), value.requiredInputCount(),
                value.hasInputItem(), value.slots());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, int selectedRecipeIndex,
                                    int recipeCount, int requiredInputCount, boolean hasInputItem,
                                    List<ObserverNativeScreenPayloads.SlotState> slots) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeVarInt(selectedRecipeIndex);
        buf.writeVarInt(recipeCount);
        buf.writeVarInt(requiredInputCount);
        buf.writeBoolean(hasInputItem);
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

    private static WoodcutterState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        int selectedRecipeIndex = buf.readVarInt();
        int recipeCount = buf.readVarInt();
        int requiredInputCount = buf.readVarInt();
        boolean hasInputItem = buf.readBoolean();
        int slotCount = buf.readVarInt();
        if (slotCount < 0 || slotCount > ObserverNativeScreenPayloads.MAX_SLOTS) {
            throw new IllegalArgumentException("Observer Woodcutter slot count out of range: " + slotCount);
        }
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new ObserverNativeScreenPayloads.SlotState(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt()));
        }
        return new WoodcutterState(protocolVersion, sequence, open, familyId, screenClass, title,
                selectedRecipeIndex, recipeCount, requiredInputCount, hasInputItem, List.copyOf(slots));
    }
}
