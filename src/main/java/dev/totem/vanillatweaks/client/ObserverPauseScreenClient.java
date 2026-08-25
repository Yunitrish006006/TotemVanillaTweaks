package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Local semantic reconstruction for the vanilla pause/game menu using existing screen metadata. */
public final class ObserverPauseScreenClient {
    static final String PAUSE_SCREEN_CLASS = "net.minecraft.client.gui.screens.PauseScreen";

    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverPauseScreenClient() {}

    /**
     * Consumes PauseScreen metadata and returns true when the generic placeholder must not process it.
     * If another screen replaces an active pause screen, the pause mirror is closed and routing continues.
     */
    static boolean applyScreenMetadata(boolean open, String screenClass, String title) {
        boolean pause = open && PAUSE_SCREEN_CLASS.equals(screenClass);
        if (pause) {
            remoteOpen = true;
            remoteTitle = title == null ? "" : title;
            ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
            ensureMirror();
            return true;
        }
        if (remoteOpen) {
            clearRemote();
            closeMirror();
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

    static boolean isNativeMirrorScreen(Screen screen) {
        return screen instanceof NativePauseMirrorScreen;
    }

    private static void ensureMirror() {
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativePauseMirrorScreen)) {
            suppressMirrorStop = true;
            try {
                minecraft.setScreenAndShow(new NativePauseMirrorScreen());
            } finally {
                suppressMirrorStop = false;
            }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativePauseMirrorScreen)) return;
        suppressMirrorStop = true;
        try {
            minecraft.setScreenAndShow(null);
        } finally {
            suppressMirrorStop = false;
        }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
    }

    private static final class NativePauseMirrorScreen extends Screen {
        private NativePauseMirrorScreen() {
            super(Component.literal("Observer Game Menu"));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) {
                ClientPlayNetworking.send(new ObserverPayloads.Stop());
            }
            super.onClose();
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, width, height, 0xA0000000);
            String heading = remoteTitle.isBlank() ? "Game Menu" : remoteTitle;
            int centerX = width / 2;
            int top = Math.max(28, height / 2 - 132);
            g.text(font, heading, centerX - font.width(heading) / 2, top, 0xFFFFFFFF, true);
            g.text(font, "Semantic pause menu", centerX - 52, top + 16, 0xFFB8C0C8, false);

            int buttonWidth = Math.min(204, Math.max(160, width - 80));
            int left = centerX - buttonWidth / 2;
            int y = top + 38;
            y = button(g, left, y, buttonWidth, "Back to Game", true);
            y += 6;
            int half = (buttonWidth - 6) / 2;
            button(g, left, y, half, "Advancements", false);
            button(g, left + half + 6, y, half, "Statistics", false);
            y += 30;
            button(g, left, y, half, "Options...", false);
            button(g, left + half + 6, y, half, "Feedback / Bugs", false);
            y += 36;
            button(g, left, y, buttonWidth, "Session exit controls", false);

            g.text(font, "No framebuffer transmitted — reconstructed from PauseScreen metadata.",
                    centerX - Math.min(170, font.width("No framebuffer transmitted — reconstructed from PauseScreen metadata.") / 2),
                    Math.min(height - 20, y + 38), 0xFF80CBC4, false);
            extractedFrames++;
        }

        private int button(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean emphasized) {
            g.fill(x, y, x + w, y + 24, emphasized ? 0xFF6A7788 : 0xFF4A4F57);
            g.outline(x, y, w, 24, 0xFFAAB2BC);
            g.text(font, label, x + (w - font.width(label)) / 2, y + 8, 0xFFFFFFFF, false);
            return y + 30;
        }
    }
}
