package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverBeaconScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Relay and validation for Beacon semantic state. */
public final class ObserverBeaconRelayManager {
    private static final int VANILLA_SLOT_COUNT = 37;
    private static final Set<String> EFFECTS = Set.of(
            "minecraft:speed", "minecraft:haste", "minecraft:resistance",
            "minecraft:jump_boost", "minecraft:strength", "minecraft:regeneration");
    private static final Map<UUID, Long> LAST_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Field TARGET_BY_OBSERVER = staticField("TARGET_BY_OBSERVER");
    private static final Field SCREEN_CAPABILITIES_BY_OBSERVER = staticField("SCREEN_CAPABILITIES_BY_OBSERVER");

    private ObserverBeaconRelayManager() {}

    public static void acceptState(ServerPlayer target, ObserverBeaconScreenPayloads.BeaconState payload) {
        if (!valid(payload)) return;
        UUID targetId = target.getUUID();
        if (!hasCapableObserver(targetId)) return;
        long last = LAST_SEQUENCE_BY_TARGET.getOrDefault(targetId, -1L);
        if (payload.sequence() <= last) return;
        LAST_SEQUENCE_BY_TARGET.put(targetId, payload.sequence());
        MinecraftServer server = target.level().getServer();
        var relay = ObserverBeaconScreenPayloads.relay(targetId, payload);
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (!targetId.equals(entry.getValue())) continue;
            UUID observerId = entry.getKey();
            if (!ObserverNativeScreenPayloads.supports(capabilitiesByObserver().getOrDefault(observerId, 0L),
                    ObserverBeaconScreenPayloads.CAPABILITY)) continue;
            ServerPlayer observer = server.getPlayerList().getPlayer(observerId);
            if (observer != null && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverBeaconScreenPayloads.BeaconRelay.TYPE)) {
                ServerPlayNetworking.send(observer, relay);
            }
        }
    }

    public static void clearTarget(UUID targetId) { LAST_SEQUENCE_BY_TARGET.remove(targetId); }

    private static boolean hasCapableObserver(UUID targetId) {
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (targetId.equals(entry.getValue()) && ObserverNativeScreenPayloads.supports(
                    capabilitiesByObserver().getOrDefault(entry.getKey(), 0L), ObserverBeaconScreenPayloads.CAPABILITY)) return true;
        }
        return false;
    }

    private static boolean valid(ObserverBeaconScreenPayloads.BeaconState p) {
        if (p.protocolVersion() != ObserverBeaconScreenPayloads.PROTOCOL_VERSION || p.sequence() < 0L
                || !ObserverBeaconScreenPayloads.FAMILY_ID.equals(p.familyId())) return false;
        if (!p.open()) return p.levels() == 0 && p.primaryEffectId().isEmpty() && p.secondaryEffectId().isEmpty()
                && !p.paymentPresent() && !p.canConfirm() && p.slots().isEmpty();
        if (!ObserverBeaconScreenPayloads.SCREEN_CLASS.equals(p.screenClass())
                || p.levels() < 0 || p.levels() > 4 || p.slots().size() != VANILLA_SLOT_COUNT
                || !validSlots(p.slots()) || !validEffect(p.primaryEffectId()) || !validEffect(p.secondaryEffectId())) return false;
        boolean payment = slotPresent(p.slots(), 0);
        if (payment != p.paymentPresent()) return false;
        boolean expectedConfirm = p.levels() > 0 && payment && !p.primaryEffectId().isBlank();
        return p.canConfirm() == expectedConfirm;
    }

    private static boolean validEffect(String id) {
        return id != null && (id.isBlank() || EFFECTS.contains(id));
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
