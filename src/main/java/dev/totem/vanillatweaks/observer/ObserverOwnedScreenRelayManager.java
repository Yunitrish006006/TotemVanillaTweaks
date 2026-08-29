package dev.totem.vanillatweaks.observer;

import dev.totem.core.api.v1.client.observer.ObserverScreenSnapshot;
import dev.totem.vanillatweaks.network.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Session- and capability-bound relay for owning-module semantic envelopes. */
public final class ObserverOwnedScreenRelayManager {
    private static final Map<UUID, Map<String, Long>> LAST = new HashMap<>();
    private static final Map<UUID, ScreenIdentity> OPEN = new HashMap<>();
    private static final Map<String, Set<String>> VARIANTS = Map.of(
            "remnant_backpack", Set.of(""),
            "automata_copper_golem", Set.of(""),
            "nexus", Set.of("map", "map_legacy", "friends", "friends_legacy", "registration", "registration_legacy"),
            "nexus_death_node_admin", Set.of(""),
            "locksmith_management", Set.of(""),
            "villagers_woodcutter", Set.of(""));

    private ObserverOwnedScreenRelayManager() { }

    public static void accept(ServerPlayer target, ObserverOwnedScreenPayloads.State payload) {
        ObserverScreenSnapshot snapshot = payload.snapshot();
        long capability = capability(snapshot.familyId());
        if (!validState(payload)) return;
        UUID targetId = target.getUUID();
        long previous = LAST.computeIfAbsent(targetId, ignored -> new HashMap<>())
                .getOrDefault(snapshot.familyId(), -1L);
        if (snapshot.sequence() <= previous) return;
        var observerIds = ObserverNativeSessionManager.observerIdsForTarget(targetId, capability);
        if (observerIds.isEmpty()) return;
        LAST.get(targetId).put(snapshot.familyId(), snapshot.sequence());
        if (payload.open()) OPEN.put(targetId, new ScreenIdentity(snapshot.familyId(), snapshot.variant(), snapshot.protocolVersion()));
        else OPEN.remove(targetId);
        var relay = new ObserverOwnedScreenPayloads.Relay(targetId, payload.open(), snapshot);
        for (UUID observerId : observerIds) {
            ServerPlayer observer = target.level().getServer().getPlayerList().getPlayer(observerId);
            if (observer != null && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverOwnedScreenPayloads.Relay.TYPE))
                ServerPlayNetworking.send(observer, relay);
        }
    }

    public static void clearTarget(UUID targetId) { LAST.remove(targetId); OPEN.remove(targetId); }

    public static boolean matchesOpen(UUID targetId, String family, String variant, int protocol) {
        return new ScreenIdentity(family, variant, protocol).equals(OPEN.get(targetId));
    }

    public static boolean isOwnedFamily(String family) { return VARIANTS.containsKey(family); }

    static boolean validState(ObserverOwnedScreenPayloads.State payload) {
        ObserverScreenSnapshot snapshot = payload.snapshot();
        if (capability(snapshot.familyId()) == 0
                || !ObserverOwnedScreenProtocols.accepts(snapshot.familyId(), snapshot.protocolVersion())
                || !VARIANTS.getOrDefault(snapshot.familyId(), Set.of()).contains(snapshot.variant())) return false;
        return payload.open() || (snapshot.slots().isEmpty() && snapshot.data().length == 0
                && snapshot.metadata().isEmpty() && snapshot.ownerPayload().length == 0);
    }

    public static long capability(String family) {
        long builtIn = ObserverNativeScreenPayloads.capabilityForFamily(family);
        if (builtIn != 0) return builtIn;
        return switch (family) {
            case "villagers_woodcutter" -> ObserverVillagersWoodcutterPayloads.CAPABILITY;
            case "nexus_death_node_admin" -> ObserverNexusDeathNodeAdminPayloads.CAPABILITY;
            case "locksmith_management" -> ObserverLocksmithManagementPayloads.CAPABILITY;
            default -> 0L;
        };
    }

    private record ScreenIdentity(String family, String variant, int protocol) { }
}
