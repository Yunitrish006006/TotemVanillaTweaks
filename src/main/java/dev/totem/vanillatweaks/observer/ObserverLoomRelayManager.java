package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverLoomScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Relay and validation for Loom semantic state. */
public final class ObserverLoomRelayManager {
    private static final int VANILLA_SLOT_COUNT = 40;
    private static final Map<UUID, Long> LAST_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Field TARGET_BY_OBSERVER = staticField("TARGET_BY_OBSERVER");
    private static final Field SCREEN_CAPABILITIES_BY_OBSERVER = staticField("SCREEN_CAPABILITIES_BY_OBSERVER");

    private ObserverLoomRelayManager() {}

    public static void acceptState(ServerPlayer target, ObserverLoomScreenPayloads.LoomState payload) {
        if (!valid(payload)) return;
        UUID targetId = target.getUUID();
        if (!hasCapableObserver(targetId)) return;
        long last = LAST_SEQUENCE_BY_TARGET.getOrDefault(targetId, -1L);
        if (payload.sequence() <= last) return;
        LAST_SEQUENCE_BY_TARGET.put(targetId, payload.sequence());
        MinecraftServer server = target.level().getServer();
        var relay = ObserverLoomScreenPayloads.relay(targetId, payload);
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (!targetId.equals(entry.getValue())) continue;
            UUID observerId = entry.getKey();
            if (!ObserverNativeScreenPayloads.supports(capabilitiesByObserver().getOrDefault(observerId, 0L),
                    ObserverLoomScreenPayloads.CAPABILITY)) continue;
            ServerPlayer observer = server.getPlayerList().getPlayer(observerId);
            if (observer != null && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverLoomScreenPayloads.LoomRelay.TYPE)) {
                ServerPlayNetworking.send(observer, relay);
            }
        }
    }

    public static void clearTarget(UUID targetId) { LAST_SEQUENCE_BY_TARGET.remove(targetId); }

    private static boolean hasCapableObserver(UUID targetId) {
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (targetId.equals(entry.getValue()) && ObserverNativeScreenPayloads.supports(
                    capabilitiesByObserver().getOrDefault(entry.getKey(), 0L), ObserverLoomScreenPayloads.CAPABILITY)) return true;
        }
        return false;
    }

    private static boolean valid(ObserverLoomScreenPayloads.LoomState p) {
        if (p.protocolVersion() != ObserverLoomScreenPayloads.PROTOCOL_VERSION || p.sequence() < 0L
                || !ObserverLoomScreenPayloads.FAMILY_ID.equals(p.familyId())) return false;
        if (!p.open()) return p.selectedPatternIndex() == -1 && p.startRow() == 0 && !p.displayPatterns()
                && p.scrollOffset() == 0.0F && !p.hasMaxPatterns() && !p.resultAvailable()
                && p.resultBaseColorId() == -1 && p.patterns().isEmpty() && p.resultLayers().isEmpty()
                && p.slots().isEmpty();
        if (!ObserverLoomScreenPayloads.SCREEN_CLASS.equals(p.screenClass())
                || p.patterns().size() > ObserverLoomScreenPayloads.MAX_PATTERNS
                || p.resultLayers().size() > ObserverLoomScreenPayloads.MAX_BANNER_LAYERS
                || !Float.isFinite(p.scrollOffset()) || p.scrollOffset() < 0.0F || p.scrollOffset() > 1.001F
                || p.slots().size() != VANILLA_SLOT_COUNT || !validSlots(p.slots())) return false;
        for (var pattern : p.patterns()) {
            if (pattern == null || pattern.registryId() == null || pattern.registryId().length() > 256
                    || !validIdentifier(pattern.assetId())) return false;
        }
        for (var layer : p.resultLayers()) {
            if (layer == null || !validIdentifier(layer.assetId())
                    || layer.dyeColorId() < 0 || layer.dyeColorId() > 15) return false;
        }
        if (p.selectedPatternIndex() < -1 || p.selectedPatternIndex() >= p.patterns().size()) return false;
        int totalRows = Math.ceilDiv(p.patterns().size(), 4);
        int maxStartRow = Math.max(0, totalRows - 4);
        if (p.startRow() < 0 || p.startRow() > maxStartRow) return false;
        if (maxStartRow == 0 && (p.startRow() != 0 || p.scrollOffset() != 0.0F)) return false;
        if (p.resultAvailable()) {
            if (p.resultBaseColorId() < 0 || p.resultBaseColorId() > 15 || p.hasMaxPatterns()) return false;
        } else if (p.resultBaseColorId() != -1 || !p.resultLayers().isEmpty()) return false;
        return p.resultAvailable() == slotPresentAtMenuOrdinal(p.slots(), 3);
    }

    private static boolean validIdentifier(String value) {
        if (value == null || value.isBlank() || value.length() > 256) return false;
        try {
            net.minecraft.resources.Identifier.parse(value);
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean slotPresentAtMenuOrdinal(
            List<ObserverNativeScreenPayloads.SlotState> slots, int ordinal) {
        if (ordinal < 0 || ordinal >= slots.size()) return false;
        var slot = slots.get(ordinal);
        return slot.count() > 0 && !slot.itemId().isBlank();
    }

    private static boolean validSlots(List<ObserverNativeScreenPayloads.SlotState> slots) {
        if (slots.size() > ObserverNativeScreenPayloads.MAX_SLOTS) return false;
        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            if (slot.index() != i || slot.x() < -64 || slot.x() > 512 || slot.y() < -64 || slot.y() > 512
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
