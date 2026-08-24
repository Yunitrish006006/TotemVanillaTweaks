package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

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
        if (payload.open() && (ObserverNativeBookScreenClient.hasStructuredRemoteScreen()
                || ObserverNativeCraftingScreenClient.hasStructuredRemoteScreen())) {
            return;
        }
        ObserverNativeScreenClient.applyGenericScreenState(payload.open(), payload.screenClass(), payload.title());
    }

    private static void tickScreenMetadata(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) {
            lastScreenKey = null;
            return;
        }

        Screen screen = minecraft.gui.screen();
        boolean screenOpen = screen != null
                && !ObserverNativeScreenClient.isNativeMirrorScreen(screen)
                && !ObserverNativeBookScreenClient.isNativeMirrorScreen(screen)
                && !ObserverNativeCraftingScreenClient.isNativeMirrorScreen(screen);
        String screenClass = screenOpen ? screen.getClass().getName() : "";
        String title = screenOpen && screen.getTitle() != null ? screen.getTitle().getString() : "";
        String key = screenOpen + "\u0000" + screenClass + "\u0000" + title;
        if (key.equals(lastScreenKey)) {
            return;
        }
        lastScreenKey = key;
        ClientPlayNetworking.send(new ObserverPayloads.ScreenState(screenOpen, screenClass, title));
    }
}
