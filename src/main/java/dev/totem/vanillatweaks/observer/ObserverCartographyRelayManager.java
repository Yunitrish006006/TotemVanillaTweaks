package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverCartographyScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Relay and validation for Cartography Table semantic state. */
public final class ObserverCartographyRelayManager {
    private static final int VANILLA_SLOT_COUNT = 39;
    private static final Map<UUID, Long> LAST_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Field TARGET_BY_OBSERVER = staticField("TARGET_BY_OBSERVER");
    private static final Field SCREEN_CAPABILITIES_BY_OBSERVER = staticField("SCREEN_CAPABILITIES_BY_OBSERVER");

    private ObserverCartographyRelayManager() {}

    public static void acceptState(ServerPlayer target, ObserverCartographyScreenPayloads.CartographyState payload) {
        if (!valid(payload)) return;
        UUID targetId = target.getUUID();
        if (!hasCapableObserver(targetId)) return;
        long last = LAST_SEQUENCE_BY_TARGET.getOrDefault(targetId, -1L);
        if (payload.sequence() <= last) return;
        LAST_SEQUENCE_BY_TARGET.put(targetId, payload.sequence());
        MinecraftServer server = target.level().getServer();
        var relay = ObserverCartographyScreenPayloads.relay(targetId, payload);
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (!targetId.equals(entry.getValue())) continue;
            UUID observerId = entry.getKey();
            if (!ObserverNativeScreenPayloads.supports(capabilitiesByObserver().getOrDefault(observerId, 0L),
                    ObserverCartographyScreenPayloads.CAPABILITY)) continue;
            ServerPlayer observer = server.getPlayerList().getPlayer(observerId);
            if (observer != null && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverCartographyScreenPayloads.CartographyRelay.TYPE)) {
                ServerPlayNetworking.send(observer, relay);
            }
        }
    }

    public static void clearTarget(UUID targetId) { LAST_SEQUENCE_BY_TARGET.remove(targetId); }

    private static boolean hasCapableObserver(UUID targetId) {
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (targetId.equals(entry.getValue()) && ObserverNativeScreenPayloads.supports(
                    capabilitiesByObserver().getOrDefault(entry.getKey(), 0L), ObserverCartographyScreenPayloads.CAPABILITY)) return true;
        }
        return false;
    }

    private static boolean valid(ObserverCartographyScreenPayloads.CartographyState p) {
        if (p.protocolVersion() != ObserverCartographyScreenPayloads.PROTOCOL_VERSION || p.sequence() < 0L
                || !ObserverCartographyScreenPayloads.FAMILY_ID.equals(p.familyId())) return false;
        if (!p.open()) return "none".equals(p.operation()) && !p.mapPresent() && !p.additionalPresent()
                && !p.resultAvailable() && p.slots().isEmpty();
        if (!ObserverCartographyScreenPayloads.SCREEN_CLASS.equals(p.screenClass())
                || p.slots().size() != VANILLA_SLOT_COUNT || !validSlots(p.slots())) return false;
        String mapItem = itemId(p.slots(), 0);
        String extraItem = itemId(p.slots(), 1);
        boolean mapPresent = "minecraft:filled_map".equals(mapItem);
        boolean additionalPresent = !extraItem.isBlank();
        if (p.mapPresent() != mapPresent || p.additionalPresent() != additionalPresent) return false;
        if (!expectedOperation(mapPresent, extraItem).equals(p.operation())) return false;
        return p.resultAvailable() == slotPresent(p.slots(), 2);
    }

    private static String expectedOperation(boolean mapPresent, String extraItem) {
        if (!mapPresent) return "none";
        return switch (extraItem) {
            case "minecraft:paper" -> "scale";
            case "minecraft:map" -> "clone";
            case "minecraft:glass_pane" -> "lock";
            default -> "none";
        };
    }

    private static String itemId(List<ObserverNativeScreenPayloads.SlotState> slots, int index) {
        for (var slot : slots) if (slot.index() == index) return slot.count() > 0 ? slot.itemId() : "";
        return "";
    }

    private static boolean slotPresent(List<ObserverNativeScreenPayloads.SlotState> slots, int index) {
        for (var slot : slots) if (slot.index() == index) return slot.count() > 0 && !slot.itemId().isBlank();
        return false;
    }

    private static boolean validSlots(List<ObserverNativeScreenPayloads.SlotState> slots) {
        if (slots.size() > ObserverNativeScreenPayloads.MAX_SLOTS) return false;
        for (var slot : slots) {
            if (slot.index() < 0 || slot.x() < -64 || slot.x() > 512 || slot.y() < -64 || slot.y() > 512
                    || slot.count() < 0 || slot.count() > 127 || slot.damage() < 0) return false;
        }
        return true;
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
