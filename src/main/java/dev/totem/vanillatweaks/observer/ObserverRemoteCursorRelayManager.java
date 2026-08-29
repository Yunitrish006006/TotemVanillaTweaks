package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverRemoteCursorPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Validates cursor sequence/rate/session/screen identity before relaying. */
public final class ObserverRemoteCursorRelayManager {
    static final long MIN_INTERVAL_NANOS = 1_000_000_000L / ObserverRemoteCursorPayloads.MAX_UPDATES_PER_SECOND;
    private static final Map<UUID, Map<String, Long>> LAST_SEQUENCE = new HashMap<>();
    private static final Map<UUID, Long> LAST_ACCEPTED_NANOS = new HashMap<>();

    private ObserverRemoteCursorRelayManager() { }

    public static void accept(ServerPlayer target, ObserverRemoteCursorPayloads.State state) {
        UUID targetId = target.getUUID();
        if (state.protocolVersion() != ObserverRemoteCursorPayloads.PROTOCOL_VERSION
                || state.screenProtocol() < 1
                || !ObserverRemoteCursorPayloads.valid(state.sequence(), state.x(), state.y(),
                state.contentWidth(), state.contentHeight())) return;
        boolean ownedFamily = ObserverOwnedScreenRelayManager.isOwnedFamily(state.familyId());
        if (ownedFamily && !ObserverOwnedScreenRelayManager.matchesOpen(
                targetId, state.familyId(), state.variant(), state.screenProtocol())) return;
        long familyCapability = ObserverOwnedScreenRelayManager.capability(state.familyId());
        if (familyCapability == 0 || state.sequence() <= LAST_SEQUENCE
                .getOrDefault(targetId, Map.of()).getOrDefault(state.familyId(), -1L)) return;
        long now = System.nanoTime(), previousTime = LAST_ACCEPTED_NANOS.getOrDefault(targetId, Long.MIN_VALUE / 2);
        if (now - previousTime < MIN_INTERVAL_NANOS) return;
        var familyObservers = ObserverNativeSessionManager.observerIdsForTarget(targetId, familyCapability);
        var cursorObservers = Set.copyOf(ObserverNativeSessionManager.observerIdsForTarget(
                targetId, ObserverRemoteCursorPayloads.CAPABILITY));
        if (familyObservers.stream().noneMatch(cursorObservers::contains)) return;
        if (!acceptSequence(targetId, state.familyId(), state.sequence())) return;
        LAST_ACCEPTED_NANOS.put(targetId, now);
        var relay = new ObserverRemoteCursorPayloads.Relay(targetId, state.protocolVersion(), state.sequence(),
                state.familyId(), state.variant(), state.screenProtocol(), state.x(), state.y(), state.contentWidth(), state.contentHeight(), state.carried().copy());
        for (UUID observerId : familyObservers) {
            if (!cursorObservers.contains(observerId)) continue;
            ServerPlayer observer = target.level().getServer().getPlayerList().getPlayer(observerId);
            if (observer != null && observer.isSpectator() && ServerPlayNetworking.canSend(observer, ObserverRemoteCursorPayloads.Relay.TYPE))
                ServerPlayNetworking.send(observer, relay);
        }
    }

    public static void clearTarget(UUID targetId) { LAST_SEQUENCE.remove(targetId); LAST_ACCEPTED_NANOS.remove(targetId); }

    static boolean acceptSequence(UUID targetId, String family, long sequence) {
        Map<String, Long> streams = LAST_SEQUENCE.computeIfAbsent(targetId, ignored -> new HashMap<>());
        if (sequence <= streams.getOrDefault(family, -1L)) return false;
        streams.put(family, sequence);
        return true;
    }
}
