package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
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
    private static final Map<UUID, Long> LAST_SCREEN_SEQUENCE_BY_TARGET = new HashMap<>();

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
                payload.experienceProgress(),
                payload.experienceLevel(),
                payload.selectedHotbarSlot(),
                payload.sprinting(),
                payload.crouching(),
                payload.usingItem()
        );
        relayToNativeObservers(target, ObserverNativePayloads.NativeViewRelay.TYPE, relay);
    }

    public static void acceptContainerState(
            ServerPlayer target,
            ObserverNativeScreenPayloads.ContainerState payload
    ) {
        if (!validContainer(payload) || nativeObserverCount(target.getUUID()) == 0) {
            return;
        }
        long lastSequence = LAST_SCREEN_SEQUENCE_BY_TARGET.getOrDefault(target.getUUID(), -1L);
        if (payload.sequence() <= lastSequence) {
            return;
        }
        LAST_SCREEN_SEQUENCE_BY_TARGET.put(target.getUUID(), payload.sequence());

        ObserverNativeScreenPayloads.ContainerRelay relay = new ObserverNativeScreenPayloads.ContainerRelay(
                target.getUUID(),
                payload.protocolVersion(),
                payload.sequence(),
                payload.open(),
                payload.screenClass(),
                payload.title(),
                payload.contentWidth(),
                payload.contentHeight(),
                payload.mouseX(),
                payload.mouseY(),
                payload.slots()
        );

        MinecraftServer server = target.level().getServer();
        for (Map.Entry<UUID, UUID> entry : TARGET_BY_OBSERVER.entrySet()) {
            if (!target.getUUID().equals(entry.getValue())) {
                continue;
            }
            ServerPlayer observer = server.getPlayerList().getPlayer(entry.getKey());
            if (observer != null
                    && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverNativeScreenPayloads.ContainerRelay.TYPE)) {
                ServerPlayNetworking.send(observer, relay);
            }
        }
    }

    private static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void relayToNativeObservers(
            ServerPlayer target,
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<T> type,
            T relay
    ) {
        MinecraftServer server = target.level().getServer();
        for (Map.Entry<UUID, UUID> entry : TARGET_BY_OBSERVER.entrySet()) {
            if (!target.getUUID().equals(entry.getValue())) {
                continue;
            }
            ServerPlayer observer = server.getPlayerList().getPlayer(entry.getKey());
            if (observer != null && observer.isSpectator() && ServerPlayNetworking.canSend(observer, type)) {
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
                && Float.isFinite(payload.experienceProgress())
                && payload.health() >= 0.0F
                && payload.maxHealth() > 0.0F
                && payload.health() <= payload.maxHealth() + 0.001F
                && payload.food() >= 0
                && payload.food() <= 20
                && payload.saturation() >= 0.0F
                && payload.saturation() <= 20.0F
                && payload.experienceProgress() >= 0.0F
                && payload.experienceProgress() <= 1.0F + 0.001F
                && payload.experienceLevel() >= 0
                && payload.selectedHotbarSlot() >= 0
                && payload.selectedHotbarSlot() < 9;
    }

    private static boolean validContainer(ObserverNativeScreenPayloads.ContainerState payload) {
        if (payload.protocolVersion() != ObserverNativeScreenPayloads.SCREEN_PROTOCOL_VERSION
                || payload.sequence() < 0L
                || payload.slots().size() > ObserverNativeScreenPayloads.MAX_SLOTS) {
            return false;
        }
        if (!payload.open()) {
            return payload.slots().isEmpty();
        }
        if (payload.contentWidth() < 64 || payload.contentWidth() > 512
                || payload.contentHeight() < 64 || payload.contentHeight() > 512
                || payload.mouseX() < -2048 || payload.mouseX() > 2048
                || payload.mouseY() < -2048 || payload.mouseY() > 2048) {
            return false;
        }
        for (ObserverNativeScreenPayloads.SlotState slot : payload.slots()) {
            if (slot.index() < 0
                    || slot.x() < -64 || slot.x() > 512
                    || slot.y() < -64 || slot.y() > 512
                    || slot.count() < 0 || slot.count() > 127
                    || slot.damage() < 0) {
                return false;
            }
        }
        return true;
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
            LAST_SCREEN_SEQUENCE_BY_TARGET.remove(targetId);
            return;
        }
        int nativeCount = nativeObserverCount(targetId);
        boolean enabled = nativeCount > 0;
        boolean captureGameplayFrames = ObserverSessionManager.observerCount(targetId) > nativeCount;
        if (!enabled) {
            LAST_SEQUENCE_BY_TARGET.remove(targetId);
            LAST_SCREEN_SEQUENCE_BY_TARGET.remove(targetId);
        }
        ServerPlayNetworking.send(target, new ObserverNativePayloads.NativeControl(
                enabled,
                ObserverNativePayloads.PROTOCOL_VERSION,
                enabled ? ObserverNativePayloads.TARGET_STATE_FPS : 0,
                captureGameplayFrames
        ));
    }
}
