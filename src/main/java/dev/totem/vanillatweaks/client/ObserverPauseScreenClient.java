package dev.totem.vanillatweaks.client;

import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;

/** Local semantic reconstruction for the vanilla pause/game menu using existing screen metadata. */
public final class ObserverPauseScreenClient {
    static final String PAUSE_SCREEN_CLASS = PauseScreen.class.getName();

    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static boolean suppressObserverScreenStop;
    private static long extractedFrames;

    private ObserverPauseScreenClient() {}

    /**
     * Consumes PauseScreen metadata and returns true when the generic placeholder must not process it.
     * If another screen replaces an active pause screen, its Observer reconstruction closes and routing continues.
     */
    static boolean applyScreenMetadata(boolean open, String screenClass, String title) {
        boolean pause = open && PAUSE_SCREEN_CLASS.equals(screenClass);
        if (pause) {
            remoteOpen = true;
            remoteTitle = title == null ? "" : title;
            ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
            ensureObserverScreen();
            return true;
        }
        if (remoteOpen) {
            clearRemote();
            closeObserverScreen();
            if (!open) {
                ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
                return true;
            }
        }
        return false;
    }

    static boolean hasRemoteScreen() {
        return remoteOpen && ObserverNativeClient.observerSessionActive();
    }

    static boolean isNativeObserverScreen(Screen screen) {
        return screen instanceof ObserverPauseScreen;
    }

    private static void ensureObserverScreen() {
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ObserverPauseScreen)) {
            suppressObserverScreenStop = true;
            try {
                minecraft.setScreenAndShow(new ObserverPauseScreen());
            } finally {
                suppressObserverScreenStop = false;
            }
        }
    }

    private static void closeObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ObserverPauseScreen)) return;
        suppressObserverScreenStop = true;
        try {
            minecraft.setScreenAndShow(null);
        } finally {
            suppressObserverScreenStop = false;
        }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
    }

    private static final class ObserverPauseScreen extends PauseScreen implements ObserverReadOnlyScreen {
        private ObserverPauseScreen() { super(true); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() {
            if (!suppressObserverScreenStop) ObserverVanillaScreenSupport.stopObserving();
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            super.extractRenderState(g, mouseX, mouseY, partialTick);
            extractedFrames++;
        }
    }
}
