package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/** Client-side structured-state transport for protocol-native Observer View. */
public final class ObserverNativeClient {
    private static boolean targetStateEnabled;
    private static int targetProtocolVersion;
    private static int targetStateFps = ObserverNativePayloads.TARGET_STATE_FPS;
    private static boolean captureGameplayFrames = true;
    private static long lastTargetStateNanos;
    private static long nextTargetStateSequence;

    private static boolean observerSessionActive;
    private static int observerProtocolVersion;
    private static UUID observerTargetId;
    private static String observerTargetName = "";
    private static long lastNativeStateSequence = -1L;
    private static float remoteYaw;
    private static float remotePitch;
    private static float remoteHealth;
    private static float remoteMaxHealth;
    private static int remoteFood;
    private static float remoteSaturation;
    private static float remoteExperienceProgress;
    private static int remoteExperienceLevel;
    private static int remoteSelectedHotbarSlot;
    private static boolean remoteSprinting;
    private static boolean remoteCrouching;
    private static boolean remoteUsingItem;

    private ObserverNativeClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverNativePayloads.NativeControl.TYPE,
                (payload, context) -> context.client().execute(() -> applyControl(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverNativePayloads.NativeSession.TYPE,
                (payload, context) -> context.client().execute(() -> applySession(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverNativePayloads.NativeViewRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload))
        );
        ClientTickEvents.END_CLIENT_TICK.register(ObserverNativeClient::tick);
    }

    static boolean suppressGameplayFramebuffer() {
        return targetStateEnabled && !captureGameplayFrames;
    }

    static boolean observerSessionActive() {
        return observerSessionActive;
    }

    static long lastNativeStateSequence() {
        return lastNativeStateSequence;
    }

    static float remoteHealth() {
        return remoteHealth;
    }

    static float remoteMaxHealth() {
        return remoteMaxHealth;
    }

    static int remoteFood() {
        return remoteFood;
    }

    static float remoteSaturation() {
        return remoteSaturation;
    }

    static float remoteExperienceProgress() {
        return remoteExperienceProgress;
    }

    static int remoteExperienceLevel() {
        return remoteExperienceLevel;
    }

    static int remoteSelectedHotbarSlot() {
        return remoteSelectedHotbarSlot;
    }

    static String observerTargetName() {
        return observerTargetName;
    }

    private static void applyControl(ObserverNativePayloads.NativeControl payload) {
        targetStateEnabled = payload.enabled()
                && payload.protocolVersion() == ObserverNativePayloads.PROTOCOL_VERSION;
        targetProtocolVersion = payload.protocolVersion();
        targetStateFps = clamp(payload.stateFps(), 1, 20);
        captureGameplayFrames = payload.captureGameplayFrames();
        lastTargetStateNanos = 0L;
        if (!targetStateEnabled) {
            targetProtocolVersion = 0;
            captureGameplayFrames = true;
        }
    }

    private static void applySession(ObserverNativePayloads.NativeSession payload) {
        observerSessionActive = payload.active()
                && payload.protocolVersion() == ObserverNativePayloads.PROTOCOL_VERSION;
        observerProtocolVersion = payload.protocolVersion();
        observerTargetId = observerSessionActive ? payload.targetId() : null;
        observerTargetName = observerSessionActive ? payload.targetName() : "";
        lastNativeStateSequence = -1L;
        ObserverUiClient.applyNativeSession(
                observerSessionActive,
                payload.targetId(),
                observerSessionActive ? payload.targetName() : ""
        );
        if (!observerSessionActive) {
            observerProtocolVersion = 0;
            remoteYaw = 0.0F;
            remotePitch = 0.0F;
            remoteHealth = 0.0F;
            remoteMaxHealth = 0.0F;
            remoteFood = 0;
            remoteSaturation = 0.0F;
            remoteExperienceProgress = 0.0F;
            remoteExperienceLevel = 0;
            remoteSelectedHotbarSlot = 0;
            remoteSprinting = false;
            remoteCrouching = false;
            remoteUsingItem = false;
        }
    }

    private static void tick(Minecraft minecraft) {
        if (!targetStateEnabled || minecraft.player == null || minecraft.level == null) {
            return;
        }
        long now = System.nanoTime();
        long interval = 1_000_000_000L / Math.max(1, targetStateFps);
        if (now - lastTargetStateNanos < interval) {
            return;
        }
        lastTargetStateNanos = now;

        ClientPlayNetworking.send(new ObserverNativePayloads.NativeViewState(
                targetProtocolVersion,
                ++nextTargetStateSequence,
                minecraft.player.getYRot(),
                minecraft.player.getXRot(),
                minecraft.player.getHealth(),
                minecraft.player.getMaxHealth(),
                minecraft.player.getFoodData().getFoodLevel(),
                minecraft.player.getFoodData().getSaturationLevel(),
                minecraft.player.experienceProgress,
                minecraft.player.experienceLevel,
                minecraft.player.getInventory().getSelectedSlot(),
                minecraft.player.isSprinting(),
                minecraft.player.isCrouching(),
                minecraft.player.isUsingItem()
        ));
    }

    private static void acceptRelay(ObserverNativePayloads.NativeViewRelay payload) {
        if (!observerSessionActive
                || observerTargetId == null
                || !observerTargetId.equals(payload.targetId())
                || payload.protocolVersion() != observerProtocolVersion
                || payload.sequence() <= lastNativeStateSequence) {
            return;
        }
        lastNativeStateSequence = payload.sequence();
        remoteYaw = payload.yaw();
        remotePitch = payload.pitch();
        remoteHealth = payload.health();
        remoteMaxHealth = payload.maxHealth();
        remoteFood = payload.food();
        remoteSaturation = payload.saturation();
        remoteExperienceProgress = payload.experienceProgress();
        remoteExperienceLevel = payload.experienceLevel();
        remoteSelectedHotbarSlot = payload.selectedHotbarSlot();
        remoteSprinting = payload.sprinting();
        remoteCrouching = payload.crouching();
        remoteUsingItem = payload.usingItem();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
