package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverAdvancementsScreenPayloads;
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

/** Relay and validation for vanilla Advancements semantic state. */
public final class ObserverAdvancementsRelayManager {
    private static final Map<UUID, Long> LAST_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Field TARGET_BY_OBSERVER = staticField("TARGET_BY_OBSERVER");
    private static final Field SCREEN_CAPABILITIES_BY_OBSERVER = staticField("SCREEN_CAPABILITIES_BY_OBSERVER");

    private ObserverAdvancementsRelayManager() {}

    public static void acceptState(ServerPlayer target, ObserverAdvancementsScreenPayloads.AdvancementsState payload) {
        if (!valid(payload)) return;
        UUID targetId = target.getUUID();
        if (!hasCapableObserver(targetId)) return;
        long last = LAST_SEQUENCE_BY_TARGET.getOrDefault(targetId, -1L);
        if (payload.sequence() <= last) return;
        LAST_SEQUENCE_BY_TARGET.put(targetId, payload.sequence());
        MinecraftServer server = target.level().getServer();
        var relay = ObserverAdvancementsScreenPayloads.relay(targetId, payload);
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (!targetId.equals(entry.getValue())) continue;
            UUID observerId = entry.getKey();
            if (!ObserverNativeScreenPayloads.supports(capabilitiesByObserver().getOrDefault(observerId, 0L),
                    ObserverAdvancementsScreenPayloads.CAPABILITY)) continue;
            ServerPlayer observer = server.getPlayerList().getPlayer(observerId);
            if (observer != null && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverAdvancementsScreenPayloads.AdvancementsRelay.TYPE)) {
                ServerPlayNetworking.send(observer, relay);
            }
        }
    }

    public static void clearTarget(UUID targetId) { LAST_SEQUENCE_BY_TARGET.remove(targetId); }

    private static boolean valid(ObserverAdvancementsScreenPayloads.AdvancementsState p) {
        if (p.protocolVersion() != ObserverAdvancementsScreenPayloads.PROTOCOL_VERSION || p.sequence() < 0L
                || !ObserverAdvancementsScreenPayloads.FAMILY_ID.equals(p.familyId())) return false;
        if (!p.open()) {
            return p.screenClass().isEmpty() && p.title().isEmpty() && p.selectedRootId().isEmpty()
                    && p.scrollX() == 0.0D && p.scrollY() == 0.0D && p.tabs().isEmpty() && p.nodes().isEmpty();
        }
        if (!ObserverAdvancementsScreenPayloads.SCREEN_CLASS.equals(p.screenClass())
                || !bounded(p.title(), 512) || !bounded(p.selectedRootId(), 256)
                || !Double.isFinite(p.scrollX()) || !Double.isFinite(p.scrollY())
                || Math.abs(p.scrollX()) > 100_000.0D || Math.abs(p.scrollY()) > 100_000.0D
                || p.tabs().size() > ObserverAdvancementsScreenPayloads.MAX_TABS
                || p.nodes().size() > ObserverAdvancementsScreenPayloads.MAX_NODES) return false;

        Set<String> roots = new HashSet<>();
        for (var tab : p.tabs()) {
            if (!boundedNonBlank(tab.rootId(), 256) || !roots.add(tab.rootId())
                    || !bounded(tab.title(), 512) || !bounded(tab.iconItemId(), 256)) return false;
        }
        if (!p.selectedRootId().isEmpty() && !roots.contains(p.selectedRootId())) return false;

        Set<String> ids = new HashSet<>();
        for (var node : p.nodes()) {
            if (!boundedNonBlank(node.id(), 256) || !ids.add(node.id())
                    || !boundedNonBlank(node.rootId(), 256) || !roots.contains(node.rootId())
                    || !bounded(node.parentId(), 256) || !bounded(node.title(), 512)
                    || !bounded(node.description(), 512) || !bounded(node.iconItemId(), 256)
                    || !(node.type().equals("task") || node.type().equals("goal") || node.type().equals("challenge"))
                    || !Float.isFinite(node.x()) || !Float.isFinite(node.y()) || !Float.isFinite(node.progress())
                    || Math.abs(node.x()) > 10_000.0F || Math.abs(node.y()) > 10_000.0F
                    || node.progress() < 0.0F || node.progress() > 1.0F
                    || (node.done() && node.progress() < 0.999F)) return false;
        }
        for (var node : p.nodes()) {
            if (!node.parentId().isEmpty() && !ids.contains(node.parentId())) return false;
        }
        return true;
    }

    private static boolean bounded(String value, int max) { return value != null && value.length() <= max; }
    private static boolean boundedNonBlank(String value, int max) { return bounded(value, max) && !value.isBlank(); }

    private static boolean hasCapableObserver(UUID targetId) {
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (targetId.equals(entry.getValue()) && ObserverNativeScreenPayloads.supports(
                    capabilitiesByObserver().getOrDefault(entry.getKey(), 0L), ObserverAdvancementsScreenPayloads.CAPABILITY)) return true;
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
