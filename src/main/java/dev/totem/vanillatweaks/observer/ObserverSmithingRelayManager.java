package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverSmithingScreenPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Relay and validation for Smithing Table semantics. */
public final class ObserverSmithingRelayManager {
    private static final Map<UUID, Long> LAST_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Field TARGET_BY_OBSERVER = staticField("TARGET_BY_OBSERVER");
    private static final Field SCREEN_CAPABILITIES_BY_OBSERVER = staticField("SCREEN_CAPABILITIES_BY_OBSERVER");
    private ObserverSmithingRelayManager() {}

    public static void acceptState(ServerPlayer target, ObserverSmithingScreenPayloads.SmithingState payload) {
        if (!valid(payload)) return;
        UUID targetId = target.getUUID();
        if (!hasCapableObserver(targetId)) return;
        long last = LAST_SEQUENCE_BY_TARGET.getOrDefault(targetId, -1L);
        if (payload.sequence() <= last) return;
        LAST_SEQUENCE_BY_TARGET.put(targetId, payload.sequence());
        MinecraftServer server = target.level().getServer();
        var relay = ObserverSmithingScreenPayloads.relay(targetId, payload);
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (!targetId.equals(entry.getValue())) continue;
            UUID observerId = entry.getKey();
            long caps = capabilitiesByObserver().getOrDefault(observerId, 0L);
            if (!ObserverNativeScreenPayloads.supports(caps, ObserverSmithingScreenPayloads.CAPABILITY)) continue;
            ServerPlayer observer = server.getPlayerList().getPlayer(observerId);
            if (observer != null && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverSmithingScreenPayloads.SmithingRelay.TYPE)) {
                ServerPlayNetworking.send(observer, relay);
            }
        }
    }

    public static void clearTarget(UUID targetId) { LAST_SEQUENCE_BY_TARGET.remove(targetId); }

    private static boolean hasCapableObserver(UUID targetId) {
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (!targetId.equals(entry.getValue())) continue;
            if (ObserverNativeScreenPayloads.supports(capabilitiesByObserver().getOrDefault(entry.getKey(), 0L),
                    ObserverSmithingScreenPayloads.CAPABILITY)) return true;
        }
        return false;
    }

    private static boolean valid(ObserverSmithingScreenPayloads.SmithingState p) {
        if (p.protocolVersion() != ObserverSmithingScreenPayloads.PROTOCOL_VERSION || p.sequence() < 0L
                || !ObserverSmithingScreenPayloads.FAMILY_ID.equals(p.familyId())) return false;
        if (!p.open()) return !p.recipeError() && !p.resultAvailable() && p.slots().isEmpty();
        if (!ObserverSmithingScreenPayloads.SCREEN_CLASS.equals(p.screenClass())) return false;
        return validSlots(p.slots());
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
