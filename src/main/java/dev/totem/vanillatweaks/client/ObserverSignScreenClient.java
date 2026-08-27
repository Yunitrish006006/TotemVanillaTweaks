package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.mixin.client.AbstractSignEditScreenAccessor;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import dev.totem.vanillatweaks.network.ObserverSignScreenPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.HangingSignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignText;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Semantic adapter and local reconstruction for vanilla Sign edit screens. */
public final class ObserverSignScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static String remoteVariant = "";
    private static boolean remoteFrontText;
    private static int remoteCurrentLine;
    private static String remoteColor = "";
    private static boolean remoteGlowing;
    private static List<String> remoteLines = List.of();
    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverSignScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ObserverSignScreenPayloads.SignRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverSignScreenClient::tick);
    }

    public static boolean isSignScreen(Screen screen) {
        return screen instanceof AbstractSignEditScreen;
    }

    private static void tick(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) closeTarget(false);
        else tickTarget(minecraft);
        if (!ObserverNativeClient.observerSessionActive()) { clearRemote(); closeMirror(); }
        else if (remoteOpen) ensureMirror();
    }

    private static void tickTarget(Minecraft minecraft) {
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverSignScreenPayloads.CAPABILITY);
        Screen current = minecraft.gui.screen();
        if (!supported || !(current instanceof AbstractSignEditScreen screen)) {
            closeTarget(supported);
            return;
        }
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        ObserverSignScreenPayloads.SignState state = captureTargetState(screen, ++nextTargetSequence);
        targetOpen = true;
        lastSnapshotNanos = now;
        ClientPlayNetworking.send(state);
    }

    private static ObserverSignScreenPayloads.SignState captureTargetState(
            AbstractSignEditScreen screen, long sequence) {
        AbstractSignEditScreenAccessor accessor = (AbstractSignEditScreenAccessor) screen;
        String[] source = accessor.totem$getMessages();
        List<String> lines = new ArrayList<>(ObserverSignScreenPayloads.LINE_COUNT);
        for (int i = 0; i < ObserverSignScreenPayloads.LINE_COUNT; i++)
            lines.add(source != null && i < source.length && source[i] != null ? source[i] : "");
        SignText text = accessor.totem$getText();
        String color = text == null || text.getColor() == null ? "" : text.getColor().getName();
        boolean glowing = text != null && text.hasGlowingText();
        String variant = screen instanceof HangingSignEditScreen ? "hanging_sign" : "sign";
        return new ObserverSignScreenPayloads.SignState(
                ObserverSignScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverSignScreenPayloads.FAMILY_ID, screen.getClass().getName(),
                screen.getTitle() == null ? "" : screen.getTitle().getString(), variant,
                accessor.totem$isFrontText(), accessor.totem$getLine(), color, glowing, List.copyOf(lines));
    }

    private static void closeTarget(boolean canSend) {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) ClientPlayNetworking.send(ObserverSignScreenPayloads.closed(++nextTargetSequence));
    }

    private static void acceptRelay(ObserverSignScreenPayloads.SignRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverSignScreenPayloads.CAPABILITY)
                || targetId == null || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverSignScreenPayloads.PROTOCOL_VERSION
                || !ObserverSignScreenPayloads.FAMILY_ID.equals(payload.familyId())
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverSignScreenPayloads.FAMILY_ID,
                        payload.targetId(), payload.sequence())) return;
        if (!payload.open()) { clearRemote(); closeMirror(); return; }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = payload.title();
        remoteVariant = payload.variant();
        remoteFrontText = payload.frontText();
        remoteCurrentLine = payload.currentLine();
        remoteColor = payload.color();
        remoteGlowing = payload.glowing();
        remoteLines = List.copyOf(payload.lines());
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeSignMirrorScreen)) {
            suppressMirrorStop = true;
            try { minecraft.setScreenAndShow(new NativeSignMirrorScreen()); }
            finally { suppressMirrorStop = false; }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeSignMirrorScreen)) return;
        suppressMirrorStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressMirrorStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
        remoteVariant = "";
        remoteFrontText = true;
        remoteCurrentLine = 0;
        remoteColor = "";
        remoteGlowing = false;
        remoteLines = List.of();
    }

    private static final class NativeSignMirrorScreen extends ObserverMirrorScreen {
        private NativeSignMirrorScreen() { super(Component.literal("Observer Sign")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) ClientPlayNetworking.send(new ObserverPayloads.Stop());
            super.onClose();
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0x90000000);
            int boardWidth = "hanging_sign".equals(remoteVariant) ? 190 : 160;
            int boardHeight = "hanging_sign".equals(remoteVariant) ? 94 : 110;
            int left = (width - boardWidth) / 2;
            int top = (height - boardHeight) / 2;
            graphics.fill(left - 4, top - 28, left + boardWidth + 4, top + boardHeight + 8, 0xCC202020);
            graphics.text(font, remoteTitle.isBlank() ? "Edit Sign" : remoteTitle, left, top - 22, 0xFFFFFFFF, false);
            graphics.text(font, (remoteFrontText ? "Front" : "Back") + " · "
                    + (remoteColor.isBlank() ? "default" : remoteColor) + (remoteGlowing ? " · glowing" : ""),
                    left, top - 10, 0xFFCCCCCC, false);
            graphics.fill(left, top, left + boardWidth, top + boardHeight, "hanging_sign".equals(remoteVariant) ? 0xFF9A6B35 : 0xFFA97843);
            for (int i = 0; i < ObserverSignScreenPayloads.LINE_COUNT; i++) {
                int y = top + 18 + i * 18;
                if (i == remoteCurrentLine) graphics.fill(left + 8, y - 3, left + boardWidth - 8, y + 11, 0x55FFFFFF);
                String line = i < remoteLines.size() ? remoteLines.get(i) : "";
                int x = left + Math.max(10, (boardWidth - font.width(line)) / 2);
                graphics.text(font, line, x, y, remoteGlowing ? 0xFFFFFFFF : 0xFF202020, false);
            }
            extractedFrames++;
        }
    }
}
