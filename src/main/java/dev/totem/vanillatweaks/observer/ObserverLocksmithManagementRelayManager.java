package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverLocksmithManagementPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Relay and validation for TotemLocksmith management state. */
public final class ObserverLocksmithManagementRelayManager {
    private static final Map<UUID, Long> LAST_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Field TARGET_BY_OBSERVER = staticField("TARGET_BY_OBSERVER");
    private static final Field SCREEN_CAPABILITIES_BY_OBSERVER = staticField("SCREEN_CAPABILITIES_BY_OBSERVER");

    private ObserverLocksmithManagementRelayManager() {}

    public static void acceptState(ServerPlayer target, ObserverLocksmithManagementPayloads.ManagementState payload) {
        if (!valid(payload)) return;
        UUID targetId = target.getUUID();
        if (!hasCapableObserver(targetId)) return;
        long last = LAST_SEQUENCE_BY_TARGET.getOrDefault(targetId, -1L);
        if (payload.sequence() <= last) return;
        LAST_SEQUENCE_BY_TARGET.put(targetId, payload.sequence());
        MinecraftServer server = target.level().getServer();
        var relay = ObserverLocksmithManagementPayloads.relay(targetId, payload);
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (!targetId.equals(entry.getValue())) continue;
            UUID observerId = entry.getKey();
            if (!ObserverNativeScreenPayloads.supports(capabilitiesByObserver().getOrDefault(observerId, 0L),
                    ObserverLocksmithManagementPayloads.CAPABILITY)) continue;
            ServerPlayer observer = server.getPlayerList().getPlayer(observerId);
            if (observer != null && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverLocksmithManagementPayloads.ManagementRelay.TYPE)) {
                ServerPlayNetworking.send(observer, relay);
            }
        }
    }

    public static void clearTarget(UUID targetId) { LAST_SEQUENCE_BY_TARGET.remove(targetId); }

    private static boolean valid(ObserverLocksmithManagementPayloads.ManagementState p) {
        if (p.protocolVersion() != ObserverLocksmithManagementPayloads.PROTOCOL_VERSION || p.sequence() < 0L
                || !ObserverLocksmithManagementPayloads.FAMILY_ID.equals(p.familyId())) return false;
        if (!p.open()) {
            return p.screenClass().isEmpty() && p.title().isEmpty() && p.lockId() == null && p.revision() == 0L
                    && p.ownerName().isEmpty() && !p.ownerActor() && !p.managerActor() && !p.physicalKeysRequired()
                    && p.logicalContainerCount() == 0 && p.connectorCount() == 0
                    && p.memberScroll() == 0 && p.candidateScroll() == 0 && p.keyScroll() == 0
                    && p.members().isEmpty() && p.keys().isEmpty() && p.candidates().isEmpty();
        }
        if (!ObserverLocksmithManagementPayloads.SCREEN_CLASS.equals(p.screenClass()) || p.lockId() == null
                || p.revision() < 0L || !bounded(p.title(), 128) || !bounded(p.ownerName(), 64)
                || p.accessModeOrdinal() < 0 || p.accessModeOrdinal() >= 4
                || p.automationModeOrdinal() < 0 || p.automationModeOrdinal() >= 3
                || p.logicalContainerCount() < 0 || p.connectorCount() < 0
                || !(p.tab().equals("access") || p.tab().equals("members") || p.tab().equals("keys"))
                || p.memberScroll() < 0 || p.candidateScroll() < 0 || p.keyScroll() < 0
                || p.members().size() > ObserverLocksmithManagementPayloads.MAX_ROWS
                || p.keys().size() > ObserverLocksmithManagementPayloads.MAX_ROWS
                || p.candidates().size() > ObserverLocksmithManagementPayloads.MAX_ROWS) return false;

        Set<UUID> memberIds = new HashSet<>();
        for (var member : p.members()) {
            if (member.playerId() == null || !memberIds.add(member.playerId()) || !bounded(member.name(), 64)
                    || member.roleOrdinal() < 0 || member.roleOrdinal() >= 3) return false;
        }
        Set<UUID> keyIds = new HashSet<>();
        for (var key : p.keys()) {
            if (key.keyId() == null || !keyIds.add(key.keyId()) || !bounded(key.label(), 64)) return false;
        }
        Set<UUID> candidateIds = new HashSet<>();
        for (var candidate : p.candidates()) {
            if (candidate.playerId() == null || !candidateIds.add(candidate.playerId()) || !bounded(candidate.name(), 64)) return false;
            if (memberIds.contains(candidate.playerId())) return false;
        }
        if (p.memberScroll() > Math.max(0, p.members().size() - 1)
                || p.candidateScroll() > Math.max(0, p.candidates().size() - 1)
                || p.keyScroll() > Math.max(0, p.keys().size() - 1)) return false;
        return true;
    }

    private static boolean bounded(String value, int max) { return value != null && value.length() <= max; }

    private static boolean hasCapableObserver(UUID targetId) {
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (targetId.equals(entry.getValue()) && ObserverNativeScreenPayloads.supports(
                    capabilitiesByObserver().getOrDefault(entry.getKey(), 0L), ObserverLocksmithManagementPayloads.CAPABILITY)) return true;
        }
        return false;
    }

    private static Field staticField(String name) {
        try { Field field = ObserverNativeSessionManager.class.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }
    @SuppressWarnings("unchecked") private static Map<UUID, UUID> targetByObserver() {
        try { return (Map<UUID, UUID>) TARGET_BY_OBSERVER.get(null); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }
    @SuppressWarnings("unchecked") private static Map<UUID, Long> capabilitiesByObserver() {
        try { return (Map<UUID, Long>) SCREEN_CAPABILITIES_BY_OBSERVER.get(null); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }
}
