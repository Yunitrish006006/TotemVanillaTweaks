package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Semantic transport for the optional TotemLocksmith management screen. */
public final class ObserverLocksmithManagementPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final long CAPABILITY = 1L << 21;
    public static final String FAMILY_ID = "locksmith_management";
    public static final String SCREEN_CLASS = "dev.totem.locksmith.client.LocksmithManagementScreen";
    public static final int MAX_ROWS = 32;
    private static final int MAX_TEXT = 128;

    private ObserverLocksmithManagementPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record MemberState(UUID playerId, String name, int roleOrdinal) {}
    public record KeyState(UUID keyId, String label) {}
    public record CandidateState(UUID playerId, String name) {}

    public record ManagementState(
            int protocolVersion, long sequence, boolean open, String familyId, String screenClass, String title,
            UUID lockId, long revision, String ownerName, boolean ownerActor, boolean managerActor,
            boolean physicalKeysRequired, int accessModeOrdinal, int automationModeOrdinal,
            int logicalContainerCount, int connectorCount, String tab,
            int memberScroll, int candidateScroll, int keyScroll,
            List<MemberState> members, List<KeyState> keys, List<CandidateState> candidates
    ) implements CustomPacketPayload {
        public static final Type<ManagementState> TYPE = new Type<>(id("observer_locksmith_management_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, ManagementState> CODEC = StreamCodec.of(
                ObserverLocksmithManagementPayloads::writeState, ObserverLocksmithManagementPayloads::readState);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ManagementRelay(
            UUID targetId, int protocolVersion, long sequence, boolean open, String familyId, String screenClass,
            String title, UUID lockId, long revision, String ownerName, boolean ownerActor, boolean managerActor,
            boolean physicalKeysRequired, int accessModeOrdinal, int automationModeOrdinal,
            int logicalContainerCount, int connectorCount, String tab,
            int memberScroll, int candidateScroll, int keyScroll,
            List<MemberState> members, List<KeyState> keys, List<CandidateState> candidates
    ) implements CustomPacketPayload {
        public static final Type<ManagementRelay> TYPE = new Type<>(id("observer_locksmith_management_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, ManagementRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(),
                            value.title(), value.lockId(), value.revision(), value.ownerName(), value.ownerActor(), value.managerActor(),
                            value.physicalKeysRequired(), value.accessModeOrdinal(), value.automationModeOrdinal(),
                            value.logicalContainerCount(), value.connectorCount(), value.tab(), value.memberScroll(),
                            value.candidateScroll(), value.keyScroll(), value.members(), value.keys(), value.candidates());
                },
                buf -> relay(buf.readUUID(), readState(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static ManagementState closed(long sequence) {
        return new ManagementState(PROTOCOL_VERSION, sequence, false, FAMILY_ID, "", "", null, 0L, "",
                false, false, false, 0, 0, 0, 0, "access", 0, 0, 0,
                List.of(), List.of(), List.of());
    }

    public static ManagementRelay relay(UUID targetId, ManagementState state) {
        return new ManagementRelay(targetId, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.screenClass(), state.title(), state.lockId(), state.revision(), state.ownerName(), state.ownerActor(),
                state.managerActor(), state.physicalKeysRequired(), state.accessModeOrdinal(), state.automationModeOrdinal(),
                state.logicalContainerCount(), state.connectorCount(), state.tab(), state.memberScroll(), state.candidateScroll(),
                state.keyScroll(), state.members(), state.keys(), state.candidates());
    }

    private static void writeState(FriendlyByteBuf buf, ManagementState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(), value.title(),
                value.lockId(), value.revision(), value.ownerName(), value.ownerActor(), value.managerActor(),
                value.physicalKeysRequired(), value.accessModeOrdinal(), value.automationModeOrdinal(),
                value.logicalContainerCount(), value.connectorCount(), value.tab(), value.memberScroll(), value.candidateScroll(),
                value.keyScroll(), value.members(), value.keys(), value.candidates());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, UUID lockId, long revision,
                                    String ownerName, boolean ownerActor, boolean managerActor, boolean physicalKeysRequired,
                                    int accessModeOrdinal, int automationModeOrdinal, int logicalContainerCount,
                                    int connectorCount, String tab, int memberScroll, int candidateScroll, int keyScroll,
                                    List<MemberState> members, List<KeyState> keys, List<CandidateState> candidates) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(screenClass, 256);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeBoolean(lockId != null);
        if (lockId != null) buf.writeUUID(lockId);
        buf.writeLong(revision);
        buf.writeUtf(ownerName, 64);
        buf.writeBoolean(ownerActor);
        buf.writeBoolean(managerActor);
        buf.writeBoolean(physicalKeysRequired);
        buf.writeVarInt(accessModeOrdinal);
        buf.writeVarInt(automationModeOrdinal);
        buf.writeVarInt(logicalContainerCount);
        buf.writeVarInt(connectorCount);
        buf.writeUtf(tab, 16);
        buf.writeVarInt(memberScroll);
        buf.writeVarInt(candidateScroll);
        buf.writeVarInt(keyScroll);
        writeMembers(buf, members);
        writeKeys(buf, keys);
        writeCandidates(buf, candidates);
    }

    private static void writeMembers(FriendlyByteBuf buf, List<MemberState> values) {
        int count = Math.min(values.size(), MAX_ROWS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            MemberState value = values.get(i);
            buf.writeUUID(value.playerId());
            buf.writeUtf(value.name(), 64);
            buf.writeVarInt(value.roleOrdinal());
        }
    }

    private static void writeKeys(FriendlyByteBuf buf, List<KeyState> values) {
        int count = Math.min(values.size(), MAX_ROWS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            KeyState value = values.get(i);
            buf.writeUUID(value.keyId());
            buf.writeUtf(value.label(), 64);
        }
    }

    private static void writeCandidates(FriendlyByteBuf buf, List<CandidateState> values) {
        int count = Math.min(values.size(), MAX_ROWS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            CandidateState value = values.get(i);
            buf.writeUUID(value.playerId());
            buf.writeUtf(value.name(), 64);
        }
    }

    private static ManagementState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(256);
        String title = buf.readUtf(MAX_TEXT);
        UUID lockId = buf.readBoolean() ? buf.readUUID() : null;
        long revision = buf.readLong();
        String ownerName = buf.readUtf(64);
        boolean ownerActor = buf.readBoolean();
        boolean managerActor = buf.readBoolean();
        boolean physicalKeysRequired = buf.readBoolean();
        int accessModeOrdinal = buf.readVarInt();
        int automationModeOrdinal = buf.readVarInt();
        int logicalContainerCount = buf.readVarInt();
        int connectorCount = buf.readVarInt();
        String tab = buf.readUtf(16);
        int memberScroll = buf.readVarInt();
        int candidateScroll = buf.readVarInt();
        int keyScroll = buf.readVarInt();
        return new ManagementState(protocolVersion, sequence, open, familyId, screenClass, title, lockId, revision,
                ownerName, ownerActor, managerActor, physicalKeysRequired, accessModeOrdinal, automationModeOrdinal,
                logicalContainerCount, connectorCount, tab, memberScroll, candidateScroll, keyScroll,
                readMembers(buf), readKeys(buf), readCandidates(buf));
    }

    private static int readCount(FriendlyByteBuf buf, String kind) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ROWS) throw new IllegalArgumentException("Observer Locksmith " + kind + " count out of range: " + count);
        return count;
    }

    private static List<MemberState> readMembers(FriendlyByteBuf buf) {
        int count = readCount(buf, "member");
        List<MemberState> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(new MemberState(buf.readUUID(), buf.readUtf(64), buf.readVarInt()));
        return List.copyOf(result);
    }

    private static List<KeyState> readKeys(FriendlyByteBuf buf) {
        int count = readCount(buf, "key");
        List<KeyState> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(new KeyState(buf.readUUID(), buf.readUtf(64)));
        return List.copyOf(result);
    }

    private static List<CandidateState> readCandidates(FriendlyByteBuf buf) {
        int count = readCount(buf, "candidate");
        List<CandidateState> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(new CandidateState(buf.readUUID(), buf.readUtf(64)));
        return List.copyOf(result);
    }
}
