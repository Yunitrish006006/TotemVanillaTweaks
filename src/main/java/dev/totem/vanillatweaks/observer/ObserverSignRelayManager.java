package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverSignScreenPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Relay and validation for Sign editor semantic state. */
public final class ObserverSignRelayManager {
    private static final Set<String> COLORS = Set.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    private static final Map<UUID, Long> LAST_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Field TARGET_BY_OBSERVER = staticField("TARGET_BY_OBSERVER");
    private static final Field SCREEN_CAPABILITIES_BY_OBSERVER = staticField("SCREEN_CAPABILITIES_BY_OBSERVER");

    private ObserverSignRelayManager() {}

    public static void acceptState(ServerPlayer target, ObserverSignScreenPayloads.SignState payload) {
        if (!valid(payload)) return;
        UUID targetId = target.getUUID();
        if (!hasCapableObserver(targetId)) return;
        long last = LAST_SEQUENCE_BY_TARGET.getOrDefault(targetId, -1L);
        if (payload.sequence() <= last) return;
        LAST_SEQUENCE_BY_TARGET.put(targetId, payload.sequence());
        MinecraftServer server = target.level().getServer();
        var relay = ObserverSignScreenPayloads.relay(targetId, payload);
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (!targetId.equals(entry.getValue())) continue;
            UUID observerId = entry.getKey();
            if (!ObserverNativeScreenPayloads.supports(capabilitiesByObserver().getOrDefault(observerId, 0L),
                    ObserverSignScreenPayloads.CAPABILITY)) continue;
            ServerPlayer observer = server.getPlayerList().getPlayer(observerId);
            if (observer != null && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverSignScreenPayloads.SignRelay.TYPE)) {
                ServerPlayNetworking.send(observer, relay);
            }
        }
    }

    public static void clearTarget(UUID targetId) { LAST_SEQUENCE_BY_TARGET.remove(targetId); }

    private static boolean hasCapableObserver(UUID targetId) {
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (targetId.equals(entry.getValue()) && ObserverNativeScreenPayloads.supports(
                    capabilitiesByObserver().getOrDefault(entry.getKey(), 0L), ObserverSignScreenPayloads.CAPABILITY)) return true;
        }
        return false;
    }

    private static boolean valid(ObserverSignScreenPayloads.SignState p) {
        if (p.protocolVersion() != ObserverSignScreenPayloads.PROTOCOL_VERSION || p.sequence() < 0L
                || !ObserverSignScreenPayloads.FAMILY_ID.equals(p.familyId())) return false;
        if (!p.open()) return p.screenClass().isEmpty() && p.variant().isEmpty() && p.lines().isEmpty()
                && p.currentLine() == 0 && p.color().isEmpty() && !p.glowing();
        boolean normal = ObserverSignScreenPayloads.SIGN_SCREEN_CLASS.equals(p.screenClass()) && "sign".equals(p.variant());
        boolean hanging = ObserverSignScreenPayloads.HANGING_SIGN_SCREEN_CLASS.equals(p.screenClass())
                && "hanging_sign".equals(p.variant());
        if ((!normal && !hanging) || p.currentLine() < 0 || p.currentLine() >= ObserverSignScreenPayloads.LINE_COUNT
                || p.lines().size() != ObserverSignScreenPayloads.LINE_COUNT || !COLORS.contains(p.color())) return false;
        for (String line : p.lines()) if (line == null || line.length() > 384) return false;
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
