package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNexusDeathNodeAdminPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Relay and validation for TotemNexus death-node administration state. */
public final class ObserverNexusDeathNodeAdminRelayManager {
    private static final Map<UUID, Long> LAST_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Field TARGET_BY_OBSERVER = staticField("TARGET_BY_OBSERVER");
    private static final Field SCREEN_CAPABILITIES_BY_OBSERVER = staticField("SCREEN_CAPABILITIES_BY_OBSERVER");

    private ObserverNexusDeathNodeAdminRelayManager() {}

    public static void acceptState(ServerPlayer target, ObserverNexusDeathNodeAdminPayloads.AdminState payload) {
        if (!valid(payload)) return;
        UUID targetId = target.getUUID();
        if (!hasCapableObserver(targetId)) return;
        long last = LAST_SEQUENCE_BY_TARGET.getOrDefault(targetId, -1L);
        if (payload.sequence() <= last) return;
        LAST_SEQUENCE_BY_TARGET.put(targetId, payload.sequence());
        MinecraftServer server = target.level().getServer();
        var relay = ObserverNexusDeathNodeAdminPayloads.relay(targetId, payload);
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (!targetId.equals(entry.getValue())) continue;
            UUID observerId = entry.getKey();
            if (!ObserverNativeScreenPayloads.supports(capabilitiesByObserver().getOrDefault(observerId, 0L),
                    ObserverNexusDeathNodeAdminPayloads.CAPABILITY)) continue;
            ServerPlayer observer = server.getPlayerList().getPlayer(observerId);
            if (observer != null && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverNexusDeathNodeAdminPayloads.AdminRelay.TYPE)) {
                ServerPlayNetworking.send(observer, relay);
            }
        }
    }

    public static void clearTarget(UUID targetId) { LAST_SEQUENCE_BY_TARGET.remove(targetId); }

    private static boolean hasCapableObserver(UUID targetId) {
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (targetId.equals(entry.getValue()) && ObserverNativeScreenPayloads.supports(
                    capabilitiesByObserver().getOrDefault(entry.getKey(), 0L), ObserverNexusDeathNodeAdminPayloads.CAPABILITY)) return true;
        }
        return false;
    }

    private static boolean valid(ObserverNexusDeathNodeAdminPayloads.AdminState p) {
        if (p.protocolVersion() != ObserverNexusDeathNodeAdminPayloads.PROTOCOL_VERSION || p.sequence() < 0L
                || !ObserverNexusDeathNodeAdminPayloads.FAMILY_ID.equals(p.familyId())) return false;
        if (!p.open()) {
            return p.screenClass().isEmpty() && p.title().isEmpty() && p.ownerQuery().isEmpty()
                    && p.dimensionQuery().isEmpty() && p.scrollIndex() == 0 && p.page() == 0
                    && p.totalEntries() == 0 && !p.truncated() && !p.administratorView()
                    && p.selectedNodeId() == null && !p.confirmationActive() && p.confirmationAction().isEmpty()
                    && p.entries().isEmpty();
        }
        if (!ObserverNexusDeathNodeAdminPayloads.SCREEN_CLASS.equals(p.screenClass())
                || !bounded(p.title(), 256) || !bounded(p.ownerQuery(), 64) || !bounded(p.dimensionQuery(), 128)
                || !bounded(p.statusFilter(), 32) || !bounded(p.timeFilter(), 32)
                || !bounded(p.confirmationAction(), 32)
                || p.scrollIndex() < 0 || p.page() < 0 || p.pageSize() < 1
                || p.pageSize() > 2048 || p.totalEntries() < 0
                || p.entries().size() > ObserverNexusDeathNodeAdminPayloads.MAX_ENTRIES) return false;
        Set<UUID> ids = new HashSet<>();
        for (var entry : p.entries()) {
            if (entry.id() == null || entry.ownerId() == null || !ids.add(entry.id())
                    || !bounded(entry.ownerName(), 64) || !bounded(entry.name(), 128)
                    || !bounded(entry.status(), 32) || !bounded(entry.dimension(), 128)
                    || entry.createdGameTime() < 0L || entry.updatedGameTime() < 0L
                    || entry.diagnosticFlags().size() > ObserverNexusDeathNodeAdminPayloads.MAX_DIAGNOSTICS) return false;
            for (String diagnostic : entry.diagnosticFlags()) if (!bounded(diagnostic, 64)) return false;
        }
        if (p.selectedNodeId() != null && !ids.contains(p.selectedNodeId())) return false;
        if (p.confirmationActive() && p.confirmationAction().isBlank()) return false;
        return true;
    }

    private static boolean bounded(String value, int max) {
        return value != null && value.length() <= max;
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
