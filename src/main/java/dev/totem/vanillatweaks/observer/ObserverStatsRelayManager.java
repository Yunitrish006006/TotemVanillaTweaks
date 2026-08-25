package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStatsScreenPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Relay and validation for vanilla Statistics semantic state. */
public final class ObserverStatsRelayManager {
    private static final Map<UUID, Long> LAST_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Field TARGET_BY_OBSERVER = staticField("TARGET_BY_OBSERVER");
    private static final Field SCREEN_CAPABILITIES_BY_OBSERVER = staticField("SCREEN_CAPABILITIES_BY_OBSERVER");

    private ObserverStatsRelayManager() {}

    public static void acceptState(ServerPlayer target, ObserverStatsScreenPayloads.StatsState payload) {
        if (!valid(payload)) return;
        UUID targetId = target.getUUID();
        if (!hasCapableObserver(targetId)) return;
        long last = LAST_SEQUENCE_BY_TARGET.getOrDefault(targetId, -1L);
        if (payload.sequence() <= last) return;
        LAST_SEQUENCE_BY_TARGET.put(targetId, payload.sequence());
        MinecraftServer server = target.level().getServer();
        var relay = ObserverStatsScreenPayloads.relay(targetId, payload);
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (!targetId.equals(entry.getValue())) continue;
            UUID observerId = entry.getKey();
            if (!ObserverNativeScreenPayloads.supports(capabilitiesByObserver().getOrDefault(observerId, 0L),
                    ObserverStatsScreenPayloads.CAPABILITY)) continue;
            ServerPlayer observer = server.getPlayerList().getPlayer(observerId);
            if (observer != null && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverStatsScreenPayloads.StatsRelay.TYPE)) {
                ServerPlayNetworking.send(observer, relay);
            }
        }
    }

    public static void clearTarget(UUID targetId) {
        LAST_SEQUENCE_BY_TARGET.remove(targetId);
    }

    private static boolean valid(ObserverStatsScreenPayloads.StatsState p) {
        if (p.protocolVersion() != ObserverStatsScreenPayloads.PROTOCOL_VERSION || p.sequence() < 0L
                || !ObserverStatsScreenPayloads.FAMILY_ID.equals(p.familyId())) return false;
        if (!p.open()) {
            return p.screenClass().isEmpty() && p.title().isEmpty() && "general".equals(p.activeTab())
                    && !p.loading() && p.scrollAmount() == 0.0D && p.itemSortColumn().isEmpty() && p.itemSortOrder() == 0
                    && p.generalRows().isEmpty() && p.itemRows().isEmpty() && p.mobRows().isEmpty();
        }
        if (!ObserverStatsScreenPayloads.SCREEN_CLASS.equals(p.screenClass()) || !bounded(p.title(), 512)
                || !(p.activeTab().equals("general") || p.activeTab().equals("items") || p.activeTab().equals("mobs"))
                || !Double.isFinite(p.scrollAmount()) || p.scrollAmount() < 0.0D || p.scrollAmount() > 100_000.0D
                || !validSortColumn(p.itemSortColumn()) || (p.itemSortOrder() < -1 || p.itemSortOrder() > 1)
                || p.generalRows().size() > ObserverStatsScreenPayloads.MAX_GENERAL_ROWS
                || p.itemRows().size() > ObserverStatsScreenPayloads.MAX_ITEM_ROWS
                || p.mobRows().size() > ObserverStatsScreenPayloads.MAX_MOB_ROWS) return false;

        if (p.activeTab().equals("general") && (!p.itemRows().isEmpty() || !p.mobRows().isEmpty())) return false;
        if (p.activeTab().equals("items") && (!p.generalRows().isEmpty() || !p.mobRows().isEmpty())) return false;
        if (p.activeTab().equals("mobs") && (!p.generalRows().isEmpty() || !p.itemRows().isEmpty())) return false;
        if (!p.activeTab().equals("items") && (!p.itemSortColumn().isEmpty() || p.itemSortOrder() != 0)) return false;

        Set<String> ids = new HashSet<>();
        for (var row : p.generalRows()) {
            if (!boundedNonBlank(row.statId(), 256) || !ids.add(row.statId())
                    || !bounded(row.label(), 512) || !bounded(row.formattedValue(), 512)
                    || row.rawValue() < 0) return false;
        }
        ids.clear();
        for (var row : p.itemRows()) {
            if (!boundedNonBlank(row.itemId(), 256) || !ids.add(row.itemId())
                    || row.mined() < 0 || row.broken() < 0 || row.crafted() < 0 || row.used() < 0
                    || row.pickedUp() < 0 || row.dropped() < 0) return false;
        }
        ids.clear();
        for (var row : p.mobRows()) {
            if (!boundedNonBlank(row.entityId(), 256) || !ids.add(row.entityId()) || !bounded(row.name(), 512)
                    || row.killed() < 0 || row.killedBy() < 0) return false;
        }
        return true;
    }

    private static boolean validSortColumn(String value) {
        return value != null && (value.isEmpty() || value.equals("mined") || value.equals("broken")
                || value.equals("crafted") || value.equals("used") || value.equals("picked_up") || value.equals("dropped"));
    }

    private static boolean bounded(String value, int max) { return value != null && value.length() <= max; }
    private static boolean boundedNonBlank(String value, int max) { return bounded(value, max) && !value.isBlank(); }

    private static boolean hasCapableObserver(UUID targetId) {
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (targetId.equals(entry.getValue()) && ObserverNativeScreenPayloads.supports(
                    capabilitiesByObserver().getOrDefault(entry.getKey(), 0L), ObserverStatsScreenPayloads.CAPABILITY)) return true;
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
