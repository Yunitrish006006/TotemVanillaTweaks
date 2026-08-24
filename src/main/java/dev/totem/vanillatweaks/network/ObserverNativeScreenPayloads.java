package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Versioned semantic-screen transport for protocol-native Observer View. */
public final class ObserverNativeScreenPayloads {
    public static final int SCREEN_PROTOCOL_VERSION = 2;
    public static final int FURNACE_PROTOCOL_VERSION = 1;
    public static final int MAX_SLOTS = 128;

    /** Generic slot-layout adapter used for AbstractContainerScreen families. */
    public static final String FAMILY_CONTAINER_SLOTS = "container_slots";
    /** Furnace, blast-furnace and smoker adapter with progress/fuel semantics. */
    public static final String FAMILY_FURNACE = "furnace";
    /** Written/writable/lectern/signing book semantic adapter. */
    public static final String FAMILY_BOOK = "book";
    /** Player 2x2 and crafting-table 3x3 semantic adapter. */
    public static final String FAMILY_CRAFTING = "crafting";

    public static final long CAPABILITY_CONTAINER_SLOTS = 1L;
    public static final long CAPABILITY_FURNACE = 1L << 1;
    public static final long CAPABILITY_BOOK = 1L << 2;
    public static final long CAPABILITY_CRAFTING = 1L << 3;
    public static final long KNOWN_CAPABILITIES = CAPABILITY_CONTAINER_SLOTS
            | CAPABILITY_FURNACE
            | CAPABILITY_BOOK
            | CAPABILITY_CRAFTING;

    private static final int MAX_TEXT = 256;

    private ObserverNativeScreenPayloads() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public static long sanitizeCapabilities(long capabilities) {
        return capabilities & KNOWN_CAPABILITIES;
    }

    public static boolean supports(long capabilities, long capability) {
        return (sanitizeCapabilities(capabilities) & capability) == capability;
    }

    public static long capabilityForFamily(String familyId) {
        return switch (familyId) {
            case FAMILY_CONTAINER_SLOTS -> CAPABILITY_CONTAINER_SLOTS;
            case FAMILY_FURNACE -> CAPABILITY_FURNACE;
            case FAMILY_BOOK -> CAPABILITY_BOOK;
            case FAMILY_CRAFTING -> CAPABILITY_CRAFTING;
            default -> 0L;
        };
    }

    public record SlotState(int index, int x, int y, String itemId, int count, int damage) {
    }

    public record ContainerState(int protocolVersion, long sequence, boolean open, String familyId,
                                 String screenClass, String title, int contentWidth, int contentHeight,
                                 int mouseX, int mouseY, List<SlotState> slots) implements CustomPacketPayload {
        public static final Type<ContainerState> TYPE = new Type<>(id("observer_native_container_state_v2"));
        public static final StreamCodec<FriendlyByteBuf, ContainerState> CODEC = StreamCodec.of(
                ObserverNativeScreenPayloads::writeContainerState,
                ObserverNativeScreenPayloads::readContainerState
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ContainerRelay(UUID targetId, int protocolVersion, long sequence, boolean open, String familyId,
                                 String screenClass, String title, int contentWidth, int contentHeight,
                                 int mouseX, int mouseY, List<SlotState> slots) implements CustomPacketPayload {
        public static final Type<ContainerRelay> TYPE = new Type<>(id("observer_native_container_relay_v2"));
        public static final StreamCodec<FriendlyByteBuf, ContainerRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeContainerFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.contentWidth(), value.contentHeight(),
                            value.mouseX(), value.mouseY(), value.slots());
                },
                buf -> {
                    UUID targetId = buf.readUUID();
                    ContainerState state = readContainerState(buf);
                    return new ContainerRelay(targetId, state.protocolVersion(), state.sequence(), state.open(),
                            state.familyId(), state.screenClass(), state.title(), state.contentWidth(),
                            state.contentHeight(), state.mouseX(), state.mouseY(), state.slots());
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record FurnaceState(int protocolVersion, long sequence, boolean open, String familyId,
                               String screenClass, String title, int contentWidth, int contentHeight,
                               int mouseX, int mouseY, List<SlotState> slots, float cookProgress,
                               float fuelProgress, boolean lit) implements CustomPacketPayload {
        public static final Type<FurnaceState> TYPE = new Type<>(id("observer_native_furnace_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, FurnaceState> CODEC = StreamCodec.of(
                ObserverNativeScreenPayloads::writeFurnaceState,
                ObserverNativeScreenPayloads::readFurnaceState
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record FurnaceRelay(UUID targetId, int protocolVersion, long sequence, boolean open, String familyId,
                               String screenClass, String title, int contentWidth, int contentHeight,
                               int mouseX, int mouseY, List<SlotState> slots, float cookProgress,
                               float fuelProgress, boolean lit) implements CustomPacketPayload {
        public static final Type<FurnaceRelay> TYPE = new Type<>(id("observer_native_furnace_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, FurnaceRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFurnaceFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.contentWidth(), value.contentHeight(),
                            value.mouseX(), value.mouseY(), value.slots(), value.cookProgress(), value.fuelProgress(),
                            value.lit());
                },
                buf -> {
                    UUID targetId = buf.readUUID();
                    FurnaceState state = readFurnaceState(buf);
                    return new FurnaceRelay(targetId, state.protocolVersion(), state.sequence(), state.open(),
                            state.familyId(), state.screenClass(), state.title(), state.contentWidth(),
                            state.contentHeight(), state.mouseX(), state.mouseY(), state.slots(), state.cookProgress(),
                            state.fuelProgress(), state.lit());
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeContainerState(FriendlyByteBuf buf, ContainerState value) {
        writeContainerFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                value.screenClass(), value.title(), value.contentWidth(), value.contentHeight(), value.mouseX(),
                value.mouseY(), value.slots());
    }

    private static void writeContainerFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                             String familyId, String screenClass, String title, int contentWidth,
                                             int contentHeight, int mouseX, int mouseY, List<SlotState> slots) {
        writeCommonScreenFields(buf, protocolVersion, sequence, open, familyId, screenClass, title, contentWidth,
                contentHeight, mouseX, mouseY, slots);
    }

    private static ContainerState readContainerState(FriendlyByteBuf buf) {
        CommonScreenFields common = readCommonScreenFields(buf);
        return new ContainerState(common.protocolVersion(), common.sequence(), common.open(), common.familyId(),
                common.screenClass(), common.title(), common.contentWidth(), common.contentHeight(), common.mouseX(),
                common.mouseY(), common.slots());
    }

    private static void writeFurnaceState(FriendlyByteBuf buf, FurnaceState value) {
        writeFurnaceFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                value.screenClass(), value.title(), value.contentWidth(), value.contentHeight(), value.mouseX(),
                value.mouseY(), value.slots(), value.cookProgress(), value.fuelProgress(), value.lit());
    }

    private static void writeFurnaceFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                           String familyId, String screenClass, String title, int contentWidth,
                                           int contentHeight, int mouseX, int mouseY, List<SlotState> slots,
                                           float cookProgress, float fuelProgress, boolean lit) {
        writeCommonScreenFields(buf, protocolVersion, sequence, open, familyId, screenClass, title, contentWidth,
                contentHeight, mouseX, mouseY, slots);
        buf.writeFloat(cookProgress);
        buf.writeFloat(fuelProgress);
        buf.writeBoolean(lit);
    }

    private static FurnaceState readFurnaceState(FriendlyByteBuf buf) {
        CommonScreenFields common = readCommonScreenFields(buf);
        return new FurnaceState(common.protocolVersion(), common.sequence(), common.open(), common.familyId(),
                common.screenClass(), common.title(), common.contentWidth(), common.contentHeight(), common.mouseX(),
                common.mouseY(), common.slots(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    private static void writeCommonScreenFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                                String familyId, String screenClass, String title, int contentWidth,
                                                int contentHeight, int mouseX, int mouseY, List<SlotState> slots) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeVarInt(contentWidth);
        buf.writeVarInt(contentHeight);
        buf.writeVarInt(mouseX);
        buf.writeVarInt(mouseY);
        writeSlots(buf, slots);
    }

    private static CommonScreenFields readCommonScreenFields(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        int contentWidth = buf.readVarInt();
        int contentHeight = buf.readVarInt();
        int mouseX = buf.readVarInt();
        int mouseY = buf.readVarInt();
        return new CommonScreenFields(protocolVersion, sequence, open, familyId, screenClass, title, contentWidth,
                contentHeight, mouseX, mouseY, readSlots(buf));
    }

    private static void writeSlots(FriendlyByteBuf buf, List<SlotState> slots) {
        int slotCount = Math.min(slots.size(), MAX_SLOTS);
        buf.writeVarInt(slotCount);
        for (int i = 0; i < slotCount; i++) {
            SlotState slot = slots.get(i);
            buf.writeVarInt(slot.index());
            buf.writeVarInt(slot.x());
            buf.writeVarInt(slot.y());
            buf.writeUtf(slot.itemId(), MAX_TEXT);
            buf.writeVarInt(slot.count());
            buf.writeVarInt(slot.damage());
        }
    }

    private static List<SlotState> readSlots(FriendlyByteBuf buf) {
        int slotCount = buf.readVarInt();
        if (slotCount < 0 || slotCount > MAX_SLOTS) {
            throw new IllegalArgumentException("Observer container slot count out of range: " + slotCount);
        }
        List<SlotState> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new SlotState(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(MAX_TEXT),
                    buf.readVarInt(), buf.readVarInt()));
        }
        return List.copyOf(slots);
    }

    private record CommonScreenFields(int protocolVersion, long sequence, boolean open, String familyId,
                                      String screenClass, String title, int contentWidth, int contentHeight,
                                      int mouseX, int mouseY, List<SlotState> slots) {
    }
}
