package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Framebuffer-free mount inventory semantics for the genuine HorseInventoryScreen. */
public final class ObserverHorseScreenPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final long CAPABILITY = 1L << 25;
    public static final String FAMILY_ID = "horse_inventory";
    public static final String SCREEN_CLASS = "net.minecraft.client.gui.screens.inventory.HorseInventoryScreen";
    public static final int MAX_COLUMNS = 5;
    private static final int MAX_STACK_BYTES = 16 * 1024;
    private static final int MAX_TOTAL_STACK_BYTES = 256 * 1024;

    private ObserverHorseScreenPayloads() { }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record HorseSlotState(int index, int x, int y, ItemStack stack) {
        public HorseSlotState { stack = stack.copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
    }

    public record HorseState(int protocolVersion, long sequence, boolean open, String familyId,
                             String screenClass, String title, int entityId, UUID entityUuid,
                             String entityType, int columns,
                             List<HorseSlotState> slots) implements CustomPacketPayload {
        public static final Type<HorseState> TYPE = new Type<>(id("observer_horse_state_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HorseState> CODEC = StreamCodec.of(
                ObserverHorseScreenPayloads::write, ObserverHorseScreenPayloads::read);
        public HorseState { slots = List.copyOf(slots); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record HorseRelay(UUID targetId, int protocolVersion, long sequence, boolean open, String familyId,
                             String screenClass, String title, int entityId, UUID entityUuid,
                             String entityType, int columns,
                             List<HorseSlotState> slots) implements CustomPacketPayload {
        public static final Type<HorseRelay> TYPE = new Type<>(id("observer_horse_relay_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HorseRelay> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeUUID(value.targetId()); write(buf, value.state()); },
                buf -> { UUID target = buf.readUUID(); HorseState state = read(buf); return relay(target, state); });
        public HorseRelay { slots = List.copyOf(slots); }
        HorseState state() { return new HorseState(protocolVersion, sequence, open, familyId, screenClass, title,
                entityId, entityUuid, entityType, columns, slots); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static HorseState closed(long sequence) {
        return new HorseState(PROTOCOL_VERSION, sequence, false, FAMILY_ID, "", "", -1,
                new UUID(0L, 0L), "", 0, List.of());
    }

    public static HorseRelay relay(UUID target, HorseState state) {
        return new HorseRelay(target, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.screenClass(), state.title(), state.entityId(), state.entityUuid(), state.entityType(),
                state.columns(), state.slots());
    }

    private static void write(RegistryFriendlyByteBuf buf, HorseState value) {
        buf.writeVarInt(value.protocolVersion()); buf.writeVarLong(value.sequence()); buf.writeBoolean(value.open());
        buf.writeUtf(value.familyId(), 64); buf.writeUtf(value.screenClass(), 256); buf.writeUtf(value.title(), 256);
        buf.writeVarInt(value.entityId()); buf.writeUUID(value.entityUuid()); buf.writeUtf(value.entityType(), 64);
        buf.writeVarInt(value.columns());
        if (value.slots().size() > ObserverNativeScreenPayloads.MAX_SLOTS) throw new IllegalArgumentException("Too many horse slots");
        buf.writeVarInt(value.slots().size());
        int totalStackBytes = 0;
        for (var slot : value.slots()) {
            buf.writeVarInt(slot.index()); buf.writeVarInt(slot.x()); buf.writeVarInt(slot.y());
            RegistryFriendlyByteBuf encoded = new RegistryFriendlyByteBuf(Unpooled.buffer(), buf.registryAccess());
            try {
                ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(encoded, slot.stack());
                int size = encoded.readableBytes();
                if (size > MAX_STACK_BYTES || (totalStackBytes += size) > MAX_TOTAL_STACK_BYTES)
                    throw new IllegalArgumentException("Horse ItemStack component payload is too large");
                byte[] bytes = new byte[size];
                encoded.getBytes(encoded.readerIndex(), bytes);
                buf.writeByteArray(bytes);
            } finally { encoded.release(); }
        }
    }

    private static HorseState read(RegistryFriendlyByteBuf buf) {
        int protocol = buf.readVarInt(); long sequence = buf.readVarLong(); boolean open = buf.readBoolean();
        String family = buf.readUtf(64), screen = buf.readUtf(256), title = buf.readUtf(256);
        int entityId = buf.readVarInt(); UUID entityUuid = buf.readUUID(); String entityType = buf.readUtf(64);
        int columns = buf.readVarInt(); int count = buf.readVarInt();
        if (count < 0 || count > ObserverNativeScreenPayloads.MAX_SLOTS) throw new IllegalArgumentException("Invalid horse slot count");
        List<HorseSlotState> slots = new ArrayList<>(count);
        int totalStackBytes = 0;
        for (int i = 0; i < count; i++) {
            int index = buf.readVarInt(), x = buf.readVarInt(), y = buf.readVarInt();
            byte[] bytes = buf.readByteArray(MAX_STACK_BYTES);
            if ((totalStackBytes += bytes.length) > MAX_TOTAL_STACK_BYTES)
                throw new IllegalArgumentException("Horse ItemStack payload total is too large");
            RegistryFriendlyByteBuf encoded = new RegistryFriendlyByteBuf(
                    Unpooled.wrappedBuffer(bytes), buf.registryAccess());
            try {
                ItemStack stack = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(encoded);
                if (encoded.readableBytes() != 0)
                    throw new IllegalArgumentException("Trailing Horse ItemStack bytes");
                slots.add(new HorseSlotState(index, x, y, stack));
            } finally { encoded.release(); }
        }
        return new HorseState(protocol, sequence, open, family, screen, title, entityId, entityUuid,
                entityType, columns, slots);
    }
}
