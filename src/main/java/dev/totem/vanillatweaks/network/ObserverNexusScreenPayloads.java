package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Optional TotemNexus semantic transport for normal-player map, friends and registration screens. */
public final class ObserverNexusScreenPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final String VARIANT_MAP = "map";
    public static final String VARIANT_FRIENDS = "friends";
    public static final String VARIANT_REGISTRATION = "registration";
    public static final int MAX_MAP_ENTRIES = 128;
    public static final int MAX_FRIEND_ENTRIES = 128;
    private static final int MAX_TEXT = 256;

    private ObserverNexusScreenPayloads() {}

    public record MapEntryState(
            UUID id, String name, String type, String dimension, String visibility,
            boolean friendShared, boolean favorite, boolean manageable, boolean owned, boolean canTeleport,
            String blockedReason, int tier, double resonance, int distanceBlocks, int foodCost,
            int amethystCost, int prepareTicks, int maxHorizontalDeviation, int damageChancePercent,
            int structureWearChancePercent) {}

    public record FriendEntryState(UUID id, String name, boolean online, String status) {}

    public record NexusState(
            int protocolVersion, long sequence, boolean open, String familyId, String variant,
            String screenClass, String title,
            UUID sourceId, String sourceType, String sourceName, String sourceDimension,
            int sourceX, int sourceY, int sourceZ,
            String activeDimension, UUID selectedId, int listScrollIndex, double zoom,
            String searchQuery, String typeFilter, String friendFilter, String sortMode, boolean showMaterials,
            List<MapEntryState> mapEntries,
            int friendsScrollIndex, List<FriendEntryState> friendEntries,
            String registrationDimension, int registrationX, int registrationY, int registrationZ,
            int registrationTier, int resonancePercent, int completenessPercent, int wearPercent,
            int confirmSeconds
    ) implements CustomPacketPayload {
        public static final Type<NexusState> TYPE = new Type<>(id("observer_nexus_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, NexusState> CODEC = StreamCodec.of(
                ObserverNexusScreenPayloads::writeState,
                ObserverNexusScreenPayloads::readState);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record NexusRelay(
            UUID targetId,
            int protocolVersion, long sequence, boolean open, String familyId, String variant,
            String screenClass, String title,
            UUID sourceId, String sourceType, String sourceName, String sourceDimension,
            int sourceX, int sourceY, int sourceZ,
            String activeDimension, UUID selectedId, int listScrollIndex, double zoom,
            String searchQuery, String typeFilter, String friendFilter, String sortMode, boolean showMaterials,
            List<MapEntryState> mapEntries,
            int friendsScrollIndex, List<FriendEntryState> friendEntries,
            String registrationDimension, int registrationX, int registrationY, int registrationZ,
            int registrationTier, int resonancePercent, int completenessPercent, int wearPercent,
            int confirmSeconds
    ) implements CustomPacketPayload {
        public static final Type<NexusRelay> TYPE = new Type<>(id("observer_nexus_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, NexusRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeState(buf, value.asState());
                },
                buf -> relay(buf.readUUID(), readState(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public NexusState asState() {
            return new NexusState(protocolVersion, sequence, open, familyId, variant, screenClass, title,
                    sourceId, sourceType, sourceName, sourceDimension, sourceX, sourceY, sourceZ,
                    activeDimension, selectedId, listScrollIndex, zoom, searchQuery, typeFilter, friendFilter,
                    sortMode, showMaterials, mapEntries, friendsScrollIndex, friendEntries,
                    registrationDimension, registrationX, registrationY, registrationZ, registrationTier,
                    resonancePercent, completenessPercent, wearPercent, confirmSeconds);
        }
    }

    public static NexusRelay relay(UUID targetId, NexusState state) {
        return new NexusRelay(targetId, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.variant(), state.screenClass(), state.title(), state.sourceId(), state.sourceType(),
                state.sourceName(), state.sourceDimension(), state.sourceX(), state.sourceY(), state.sourceZ(),
                state.activeDimension(), state.selectedId(), state.listScrollIndex(), state.zoom(), state.searchQuery(),
                state.typeFilter(), state.friendFilter(), state.sortMode(), state.showMaterials(), state.mapEntries(),
                state.friendsScrollIndex(), state.friendEntries(), state.registrationDimension(), state.registrationX(),
                state.registrationY(), state.registrationZ(), state.registrationTier(), state.resonancePercent(),
                state.completenessPercent(), state.wearPercent(), state.confirmSeconds());
    }

    public static NexusState closed(long sequence) {
        return new NexusState(PROTOCOL_VERSION, sequence, false, ObserverNativeScreenPayloads.FAMILY_NEXUS, "", "", "",
                null, "", "", "", 0, 0, 0, "", null, 0, 1.0D, "", "", "", "", false,
                List.of(), 0, List.of(), "", 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    private static void writeState(FriendlyByteBuf buf, NexusState v) {
        buf.writeVarInt(v.protocolVersion());
        buf.writeLong(v.sequence());
        buf.writeBoolean(v.open());
        buf.writeUtf(v.familyId(), MAX_TEXT);
        buf.writeUtf(v.variant(), 32);
        buf.writeUtf(v.screenClass(), MAX_TEXT);
        buf.writeUtf(v.title(), MAX_TEXT);
        writeNullableUuid(buf, v.sourceId());
        buf.writeUtf(v.sourceType(), MAX_TEXT);
        buf.writeUtf(v.sourceName(), MAX_TEXT);
        buf.writeUtf(v.sourceDimension(), MAX_TEXT);
        buf.writeInt(v.sourceX()); buf.writeInt(v.sourceY()); buf.writeInt(v.sourceZ());
        buf.writeUtf(v.activeDimension(), MAX_TEXT);
        writeNullableUuid(buf, v.selectedId());
        buf.writeVarInt(v.listScrollIndex());
        buf.writeDouble(v.zoom());
        buf.writeUtf(v.searchQuery(), MAX_TEXT);
        buf.writeUtf(v.typeFilter(), 32);
        buf.writeUtf(v.friendFilter(), 32);
        buf.writeUtf(v.sortMode(), 32);
        buf.writeBoolean(v.showMaterials());
        writeMapEntries(buf, v.mapEntries());
        buf.writeVarInt(v.friendsScrollIndex());
        writeFriendEntries(buf, v.friendEntries());
        buf.writeUtf(v.registrationDimension(), MAX_TEXT);
        buf.writeInt(v.registrationX()); buf.writeInt(v.registrationY()); buf.writeInt(v.registrationZ());
        buf.writeVarInt(v.registrationTier());
        buf.writeVarInt(v.resonancePercent());
        buf.writeVarInt(v.completenessPercent());
        buf.writeVarInt(v.wearPercent());
        buf.writeVarInt(v.confirmSeconds());
    }

    private static NexusState readState(FriendlyByteBuf buf) {
        return new NexusState(
                buf.readVarInt(), buf.readLong(), buf.readBoolean(), buf.readUtf(MAX_TEXT), buf.readUtf(32),
                buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), readNullableUuid(buf), buf.readUtf(MAX_TEXT),
                buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readUtf(MAX_TEXT), readNullableUuid(buf), buf.readVarInt(), buf.readDouble(), buf.readUtf(MAX_TEXT),
                buf.readUtf(32), buf.readUtf(32), buf.readUtf(32), buf.readBoolean(), readMapEntries(buf),
                buf.readVarInt(), readFriendEntries(buf), buf.readUtf(MAX_TEXT), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    private static void writeMapEntries(FriendlyByteBuf buf, List<MapEntryState> values) {
        List<MapEntryState> list = values == null ? List.of() : values;
        int count = Math.min(list.size(), MAX_MAP_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            MapEntryState e = list.get(i);
            buf.writeUUID(e.id());
            buf.writeUtf(e.name(), MAX_TEXT);
            buf.writeUtf(e.type(), 64);
            buf.writeUtf(e.dimension(), MAX_TEXT);
            buf.writeUtf(e.visibility(), 32);
            buf.writeBoolean(e.friendShared());
            buf.writeBoolean(e.favorite());
            buf.writeBoolean(e.manageable());
            buf.writeBoolean(e.owned());
            buf.writeBoolean(e.canTeleport());
            buf.writeUtf(e.blockedReason(), MAX_TEXT);
            buf.writeVarInt(e.tier());
            buf.writeDouble(e.resonance());
            buf.writeVarInt(e.distanceBlocks());
            buf.writeVarInt(e.foodCost());
            buf.writeVarInt(e.amethystCost());
            buf.writeVarInt(e.prepareTicks());
            buf.writeVarInt(e.maxHorizontalDeviation());
            buf.writeVarInt(e.damageChancePercent());
            buf.writeVarInt(e.structureWearChancePercent());
        }
    }

    private static List<MapEntryState> readMapEntries(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_MAP_ENTRIES) throw new IllegalArgumentException("Nexus map entry count out of range");
        List<MapEntryState> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new MapEntryState(buf.readUUID(), buf.readUtf(MAX_TEXT), buf.readUtf(64), buf.readUtf(MAX_TEXT),
                    buf.readUtf(32), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readBoolean(), buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readDouble(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt()));
        }
        return List.copyOf(list);
    }

    private static void writeFriendEntries(FriendlyByteBuf buf, List<FriendEntryState> values) {
        List<FriendEntryState> list = values == null ? List.of() : values;
        int count = Math.min(list.size(), MAX_FRIEND_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            FriendEntryState e = list.get(i);
            buf.writeUUID(e.id());
            buf.writeUtf(e.name(), 64);
            buf.writeBoolean(e.online());
            buf.writeUtf(e.status(), 32);
        }
    }

    private static List<FriendEntryState> readFriendEntries(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_FRIEND_ENTRIES) throw new IllegalArgumentException("Nexus friend entry count out of range");
        List<FriendEntryState> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new FriendEntryState(buf.readUUID(), buf.readUtf(64), buf.readBoolean(), buf.readUtf(32)));
        }
        return List.copyOf(list);
    }

    private static void writeNullableUuid(FriendlyByteBuf buf, UUID value) {
        buf.writeBoolean(value != null);
        if (value != null) buf.writeUUID(value);
    }

    private static UUID readNullableUuid(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUUID() : null;
    }
}
