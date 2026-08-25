package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNexusScreenPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Optional-module relay for TotemNexus normal-player screen semantics. */
public final class ObserverNexusRelayManager {
    private static final Map<UUID, Long> LAST_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Field TARGET_BY_OBSERVER = staticField("TARGET_BY_OBSERVER");
    private static final Field SCREEN_CAPABILITIES_BY_OBSERVER = staticField("SCREEN_CAPABILITIES_BY_OBSERVER");

    private ObserverNexusRelayManager() {}

    public static void acceptState(ServerPlayer target, ObserverNexusScreenPayloads.NexusState payload) {
        if (!valid(payload)) return;
        UUID targetId = target.getUUID();
        if (!hasCapableObserver(targetId)) return;
        long last = LAST_SEQUENCE_BY_TARGET.getOrDefault(targetId, -1L);
        if (payload.sequence() <= last) return;
        LAST_SEQUENCE_BY_TARGET.put(targetId, payload.sequence());

        MinecraftServer server = target.level().getServer();
        var relay = ObserverNexusScreenPayloads.relay(targetId, payload);
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (!targetId.equals(entry.getValue())) continue;
            UUID observerId = entry.getKey();
            long capabilities = capabilitiesByObserver().getOrDefault(observerId, 0L);
            if (!ObserverNativeScreenPayloads.supports(capabilities, ObserverNativeScreenPayloads.CAPABILITY_NEXUS)) continue;
            ServerPlayer observer = server.getPlayerList().getPlayer(observerId);
            if (observer != null && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverNexusScreenPayloads.NexusRelay.TYPE)) {
                ServerPlayNetworking.send(observer, relay);
            }
        }
    }

    public static void clearTarget(UUID targetId) {
        LAST_SEQUENCE_BY_TARGET.remove(targetId);
    }

    private static boolean valid(ObserverNexusScreenPayloads.NexusState p) {
        if (p.protocolVersion() != ObserverNexusScreenPayloads.PROTOCOL_VERSION
                || p.sequence() < 0L
                || !ObserverNativeScreenPayloads.FAMILY_NEXUS.equals(p.familyId())) return false;
        if (!p.open()) return p.mapEntries().isEmpty() && p.friendEntries().isEmpty();
        return switch (p.variant()) {
            case ObserverNexusScreenPayloads.VARIANT_MAP -> validMap(p);
            case ObserverNexusScreenPayloads.VARIANT_FRIENDS -> validFriends(p);
            case ObserverNexusScreenPayloads.VARIANT_REGISTRATION -> validRegistration(p);
            default -> false;
        };
    }

    private static boolean validMap(ObserverNexusScreenPayloads.NexusState p) {
        if (p.sourceId() == null || p.mapEntries().size() > ObserverNexusScreenPayloads.MAX_MAP_ENTRIES
                || p.listScrollIndex() < 0 || !Double.isFinite(p.zoom()) || p.zoom() < 0.1D || p.zoom() > 10.0D) return false;
        for (var e : p.mapEntries()) {
            if (e.id() == null || e.tier() < 0 || !Double.isFinite(e.resonance()) || e.resonance() < 0.0D
                    || e.distanceBlocks() < 0 || e.foodCost() < 0 || e.amethystCost() < 0 || e.prepareTicks() < 0
                    || e.maxHorizontalDeviation() < 0 || e.damageChancePercent() < 0 || e.damageChancePercent() > 100
                    || e.structureWearChancePercent() < 0 || e.structureWearChancePercent() > 100) return false;
        }
        return true;
    }

    private static boolean validFriends(ObserverNexusScreenPayloads.NexusState p) {
        if (p.friendEntries().size() > ObserverNexusScreenPayloads.MAX_FRIEND_ENTRIES || p.friendsScrollIndex() < 0) return false;
        for (var e : p.friendEntries()) {
            if (e.id() == null || !("friend".equals(e.status()) || "incoming".equals(e.status()) || "outgoing".equals(e.status()))) return false;
        }
        return true;
    }

    private static boolean validRegistration(ObserverNexusScreenPayloads.NexusState p) {
        return p.registrationTier() >= 0
                && percent(p.resonancePercent()) && percent(p.completenessPercent()) && percent(p.wearPercent())
                && p.confirmSeconds() >= 0 && p.confirmSeconds() <= 3600;
    }

    private static boolean percent(int value) { return value >= 0 && value <= 100; }

    private static boolean hasCapableObserver(UUID targetId) {
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (!targetId.equals(entry.getValue())) continue;
            if (ObserverNativeScreenPayloads.supports(capabilitiesByObserver().getOrDefault(entry.getKey(), 0L),
                    ObserverNativeScreenPayloads.CAPABILITY_NEXUS)) return true;
        }
        return false;
    }

    private static Field staticField(String name) {
        try {
            Field field = ObserverNativeSessionManager.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, UUID> targetByObserver() {
        try { return (Map<UUID, UUID>) TARGET_BY_OBSERVER.get(null); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Long> capabilitiesByObserver() {
        try { return (Map<UUID, Long>) SCREEN_CAPABILITIES_BY_OBSERVER.get(null); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }
}
