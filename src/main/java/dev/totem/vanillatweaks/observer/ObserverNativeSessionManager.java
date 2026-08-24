package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative structured-state side channel used during the Observer protocol-native migration. */
public final class ObserverNativeSessionManager {
    private static final UUID EMPTY_TARGET = new UUID(0L, 0L);
    private static final Map<UUID, UUID> TARGET_BY_OBSERVER = new HashMap<>();
    private static final Map<UUID, Long> LAST_SEQUENCE_BY_TARGET = new HashMap<>();

    private ObserverNativeSessionManager() {
    }

    public static boolean start(ServerPlayer observer, ServerPlayer target) {
        if (!ServerPlayNetworking.canSend(observer, ObserverNativePayloads.NativeSession.TYPE)
                || !ServerPlayNetworking.canSend(observer, ObserverNativePayloads.NativeViewRelay.TYPE)
                || !ServerPlayNetworking.canSend(target, ObserverNativePayloads.NativeControl.TYPE)) {
            return false;
        }

        TARGET_BY_OBSERVER.put(observer.getUUID(), target.getUUID());
        ServerPlayNetworking.send(observer, new ObserverNativePayloads.NativeSession(
                true,
                target.getUUID(),
                target.getGameProfile().name(),
                ObserverNativePayloads.PROTOCOL_VERSION
        ));
        updateTargetControl(target.level().getServer(), target.getUUID());
        return true;
    }

    public static boolean stop(ServerPlayer observer) {
        UUID targetId = TARGET_BY_OBSERVER.remove(observer.getUUID());
        boolean wasNative = targetId != null;
        if (ServerPlayNetworking.canSend(observer, ObserverNativePayloads.NativeSession.TYPE)) {
            ServerPlayNetworking.send(observer, new ObserverNativePayloads.NativeSession(
                    false,
                    EMPTY_TARGET,
                    "",
                    ObserverNativePayloads.PROTOCOL_VERSION
            ));
        }
        if (targetId != null) {
            updateTargetControl(observer.level().getServer(), targetId);
        }
        return wasNative;
    }

    public static void removeOfflineObserver(MinecraftServer server, UUID observerId) {
        UUID targetId = TARGET_BY_OBSERVER.remove(observerId);
        if (targetId != null) {
            updateTargetControl(server, targetId);
        }
    }

    public static void refreshTargetControl(MinecraftServer server, UUID targetId) {
        updateTargetControl(server, targetId);
    }

    public static void acceptViewState(ServerPlayer target, ObserverNativePayloads.NativeViewState payload) {
        if (!valid(payload)) {
            return;
        }
        int observerCount = nativeObserverCount(target.getUUID());
        if (observerCount == 0) {
            return;
        }

        long lastSequence = LAST_SEQUENCE_BY_TARGET.getOrDefault(target.getUUID(), -1L);
        if (payload.sequence() <= lastSequence) {
            return;
        }
        LAST_SEQUENCE_BY_TARGET.put(target.getUUID(), payload.sequence());

        ObserverNativePayloads.NativeViewRelay relay = new ObserverNativePayloads.NativeViewRelay(
                target.getUUID(),
                payload.protocolVersion(),
                payload.sequence(),
                payload.yaw(),
                payload.pitch(),
                payload.health(),
                payload.maxHealth(),
                payload.food(),
                payload.saturation(),
                payload.sprinting(),
                payload.crouching(),
                payload.usingItem()
        );
        MinecraftServer server = target.level().getServer();
        for (Map.Entry<UUID, UUID> entry : TARGET_BY_OBSERVER.entrySet()) {
            if (!target.getUUID().equals(entry.getValue())) {
                continue;
            }
            ServerPlayer observer = server.getPlayerList().getPlayer(entry.getKey());
            if (observer != null
                    && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverNativePayloads.NativeViewRelay.TYPE)) {
                ServerPlayNetworking.send(observer, relay);
            }
        }
    }

    private static boolean valid(ObserverNativePayloads.NativeViewState payload) {
        return payload.protocolVersion() == ObserverNativePayloads.PROTOCOL_VERSION
                && payload.sequence() >= 0L
                && Float.isFinite(payload.yaw())
                && Float.isFinite(payload.pitch())
                && Float.isFinite(payload.health())
                && Float.isFinite(payload.maxHealth())
                && Float.isFinite(payload.saturation())
                && payload.health() >= 0.0F
                && payload.maxHealth() > 0.0F
                && payload.health() <= payload.maxHealth() + 0.001F
                && payload.food() >= 0
                && payload.food() <= 20
                && payload.saturation() >= 0.0F
                && payload.saturation() <= 20.0F;
    }

    private static int nativeObserverCount(UUID targetId) {
        int count = 0;
        for (UUID value : TARGET_BY_OBSERVER.values()) {
            if (targetId.equals(value)) {
                count++;
            }
        }
        return count;
    }

    private static void updateTargetControl(MinecraftServer server, UUID targetId) {
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null || !ServerPlayNetworking.canSend(target, ObserverNativePayloads.NativeControl.TYPE)) {
            LAST_SEQUENCE_BY_TARGET.remove(targetId);
            return;
        }
        int nativeCount = nativeObserverCount(targetId);
        boolean enabled = nativeCount > 0;
        boolean captureGameplayFrames = ObserverSessionManager.observerCount(targetId) > nativeCount;
        if (!enabled) {
            LAST_SEQUENCE_BY_TARGET.remove(targetId);
        }
        ServerPlayNetworking.send(target, new ObserverNativePayloads.NativeControl(
                enabled,
                ObserverNativePayloads.PROTOCOL_VERSION,
                enabled ? ObserverNativePayloads.TARGET_STATE_FPS : 0,
                captureGameplayFrames
        ));
    }
}
