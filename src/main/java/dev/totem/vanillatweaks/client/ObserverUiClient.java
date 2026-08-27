package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Lightweight lifecycle and screen-metadata bridge for protocol-native Observer View. */
public final class ObserverUiClient {
    private static boolean sessionActive;
    private static UUID targetId;
    private static String targetName = "";
    private static String lastScreenKey;

    private ObserverUiClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverPayloads.ScreenRelay.TYPE,
                (payload, context) -> context.client().execute(() -> applyScreenRelay(payload))
        );
        ClientTickEvents.END_CLIENT_TICK.register(ObserverUiClient::tickScreenMetadata);
    }

    static void applyNativeSession(boolean active, UUID nativeTargetId, String nativeTargetName) {
        sessionActive = active;
        targetId = active ? nativeTargetId : null;
        targetName = active ? nativeTargetName : "";
        if (!active) {
            ObserverPauseScreenClient.applyScreenMetadata(false, "", "");
            ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        }
    }

    static boolean sessionActive() {
        return sessionActive;
    }

    static UUID targetId() {
        return targetId;
    }

    static String targetName() {
        return targetName;
    }

    private static void applyScreenRelay(ObserverPayloads.ScreenRelay payload) {
        if (!sessionActive || targetId == null || !targetId.equals(payload.targetId())) {
            return;
        }
        if (ObserverPauseScreenClient.applyScreenMetadata(
                payload.open(), payload.screenClass(), payload.title())) {
            return;
        }
        if (payload.open() && ObserverStructuredScreenPolicy.suppressGenericMetadata(
                payload.screenClass(), ObserverNativeClient.observerScreenCapabilities())) {
            return;
        }
        ObserverNativeScreenClient.applyGenericScreenState(payload.open(), payload.screenClass(), payload.title());
    }

    private static void tickScreenMetadata(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) {
            lastScreenKey = null;
            return;
        }

        ObserverPayloads.ScreenState state = captureScreenMetadata(minecraft.gui.screen());
        String key = state.open() + "\u0000" + state.screenClass() + "\u0000" + state.title();
        if (key.equals(lastScreenKey)) {
            return;
        }
        lastScreenKey = key;
        ClientPlayNetworking.send(state);
    }

    /**
     * Captures only public screen identity metadata. Screen-private fields such
     * as a ChatScreen draft or unsent command are deliberately never read.
     */
    static ObserverPayloads.ScreenState captureScreenMetadata(Screen screen) {
        if (screen == null || ObserverMirrorScreen.isMirror(screen)) {
            return new ObserverPayloads.ScreenState(false, "", "");
        }
        Component title = screen.getTitle();
        return new ObserverPayloads.ScreenState(
                true,
                screen.getClass().getName(),
                title == null ? "" : title.getString()
        );
    }
}
