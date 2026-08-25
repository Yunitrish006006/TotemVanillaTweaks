package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Semantic transport for TotemNexus death-node administration. */
public final class ObserverNexusDeathNodeAdminPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final long CAPABILITY = 1L << 20;
    public static final String FAMILY_ID = "nexus_death_node_admin";
    public static final String SCREEN_CLASS = "dev.totem.nexus.client.NexusDeathNodeAdminScreen";
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_DIAGNOSTICS = 8;
    private static final int MAX_TEXT = 256;

    private ObserverNexusDeathNodeAdminPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record EntryState(UUID id, UUID ownerId, String ownerName, String name, String status,
                             String dimension, int x, int y, int z, long createdGameTime, long updatedGameTime,
                             List<String> diagnosticFlags) {}

    public record AdminState(int protocolVersion, long sequence, boolean open, String familyId,
                             String screenClass, String title, String ownerQuery, String dimensionQuery,
                             String statusFilter, String timeFilter, int scrollIndex, int page, int pageSize,
                             int totalEntries, boolean truncated, boolean administratorView, UUID selectedNodeId,
                             boolean confirmationActive, String confirmationAction,
                             List<EntryState> entries) implements CustomPacketPayload {
        public static final Type<AdminState> TYPE = new Type<>(id("observer_nexus_death_node_admin_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, AdminState> CODEC = StreamCodec.of(
                ObserverNexusDeathNodeAdminPayloads::writeState, ObserverNexusDeathNodeAdminPayloads::readState);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record AdminRelay(UUID targetId, int protocolVersion, long sequence, boolean open, String familyId,
                             String screenClass, String title, String ownerQuery, String dimensionQuery,
                             String statusFilter, String timeFilter, int scrollIndex, int page, int pageSize,
                             int totalEntries, boolean truncated, boolean administratorView, UUID selectedNodeId,
                             boolean confirmationActive, String confirmationAction,
                             List<EntryState> entries) implements CustomPacketPayload {
        public static final Type<AdminRelay> TYPE = new Type<>(id("observer_nexus_death_node_admin_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, AdminRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.ownerQuery(), value.dimensionQuery(),
                            value.statusFilter(), value.timeFilter(), value.scrollIndex(), value.page(), value.pageSize(),
                            value.totalEntries(), value.truncated(), value.administratorView(), value.selectedNodeId(),
                            value.confirmationActive(), value.confirmationAction(), value.entries());
                },
                buf -> relay(buf.readUUID(), readState(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static AdminState closed(long sequence) {
        return new AdminState(PROTOCOL_VERSION, sequence, false, FAMILY_ID, "", "", "", "", "all", "all",
                0, 0, 1, 0, false, false, null, false, "", List.of());
    }

    public static AdminRelay relay(UUID targetId, AdminState state) {
        return new AdminRelay(targetId, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.screenClass(), state.title(), state.ownerQuery(), state.dimensionQuery(), state.statusFilter(),
                state.timeFilter(), state.scrollIndex(), state.page(), state.pageSize(), state.totalEntries(),
                state.truncated(), state.administratorView(), state.selectedNodeId(), state.confirmationActive(),
                state.confirmationAction(), state.entries());
    }

    private static void writeState(FriendlyByteBuf buf, AdminState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(),
                value.title(), value.ownerQuery(), value.dimensionQuery(), value.statusFilter(), value.timeFilter(),
                value.scrollIndex(), value.page(), value.pageSize(), value.totalEntries(), value.truncated(),
                value.administratorView(), value.selectedNodeId(), value.confirmationActive(), value.confirmationAction(),
                value.entries());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, String ownerQuery,
                                    String dimensionQuery, String statusFilter, String timeFilter, int scrollIndex,
                                    int page, int pageSize, int totalEntries, boolean truncated,
                                    boolean administratorView, UUID selectedNodeId, boolean confirmationActive,
                                    String confirmationAction, List<EntryState> entries) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeUtf(ownerQuery, MAX_TEXT);
        buf.writeUtf(dimensionQuery, MAX_TEXT);
        buf.writeUtf(statusFilter, 32);
        buf.writeUtf(timeFilter, 32);
        buf.writeVarInt(scrollIndex);
        buf.writeVarInt(page);
        buf.writeVarInt(pageSize);
        buf.writeVarInt(totalEntries);
        buf.writeBoolean(truncated);
        buf.writeBoolean(administratorView);
        buf.writeBoolean(selectedNodeId != null);
        if (selectedNodeId != null) buf.writeUUID(selectedNodeId);
        buf.writeBoolean(confirmationActive);
        buf.writeUtf(confirmationAction, 32);
        int count = Math.min(entries.size(), MAX_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) writeEntry(buf, entries.get(i));
    }

    private static void writeEntry(FriendlyByteBuf buf, EntryState entry) {
        buf.writeUUID(entry.id());
        buf.writeUUID(entry.ownerId());
        buf.writeUtf(entry.ownerName(), 64);
        buf.writeUtf(entry.name(), 128);
        buf.writeUtf(entry.status(), 32);
        buf.writeUtf(entry.dimension(), 128);
        buf.writeInt(entry.x());
        buf.writeInt(entry.y());
        buf.writeInt(entry.z());
        buf.writeLong(entry.createdGameTime());
        buf.writeLong(entry.updatedGameTime());
        int count = Math.min(entry.diagnosticFlags().size(), MAX_DIAGNOSTICS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) buf.writeUtf(entry.diagnosticFlags().get(i), 64);
    }

    private static AdminState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        String ownerQuery = buf.readUtf(MAX_TEXT);
        String dimensionQuery = buf.readUtf(MAX_TEXT);
        String statusFilter = buf.readUtf(32);
        String timeFilter = buf.readUtf(32);
        int scrollIndex = buf.readVarInt();
        int page = buf.readVarInt();
        int pageSize = buf.readVarInt();
        int totalEntries = buf.readVarInt();
        boolean truncated = buf.readBoolean();
        boolean administratorView = buf.readBoolean();
        UUID selectedNodeId = buf.readBoolean() ? buf.readUUID() : null;
        boolean confirmationActive = buf.readBoolean();
        String confirmationAction = buf.readUtf(32);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Observer death-node admin entry count out of range: " + count);
        List<EntryState> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(readEntry(buf));
        return new AdminState(protocolVersion, sequence, open, familyId, screenClass, title, ownerQuery,
                dimensionQuery, statusFilter, timeFilter, scrollIndex, page, pageSize, totalEntries, truncated,
                administratorView, selectedNodeId, confirmationActive, confirmationAction, List.copyOf(entries));
    }

    private static EntryState readEntry(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        UUID ownerId = buf.readUUID();
        String ownerName = buf.readUtf(64);
        String name = buf.readUtf(128);
        String status = buf.readUtf(32);
        String dimension = buf.readUtf(128);
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        long created = buf.readLong();
        long updated = buf.readLong();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_DIAGNOSTICS) throw new IllegalArgumentException("Observer death-node diagnostics out of range: " + count);
        List<String> diagnostics = new ArrayList<>(count);
        for (int i = 0; i < count; i++) diagnostics.add(buf.readUtf(64));
        return new EntryState(id, ownerId, ownerName, name, status, dimension, x, y, z, created, updated,
                List.copyOf(diagnostics));
    }
}
