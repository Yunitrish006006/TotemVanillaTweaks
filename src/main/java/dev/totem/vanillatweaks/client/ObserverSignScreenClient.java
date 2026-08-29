package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.mixin.client.AbstractSignEditScreenAccessor;
import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import dev.totem.vanillatweaks.network.ObserverSignScreenPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.HangingSignEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
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
    private static boolean suppressObserverScreenStop;
    private static long extractedFrames;
    private static long suppressedRemovalPackets;

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
        if (!ObserverNativeClient.observerSessionActive()) { clearRemote(); closeObserverScreen(); }
        else if (remoteOpen) ensureObserverScreen();
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
        List<String> lines = new ArrayList<>(ObserverSignScreenPayloads.LINE_COUNT);
        // Sign editor messages are unsent local drafts. Preserve only the
        // production screen shape/style; private draft text never enters the
        // Observer transport.
        for (int i = 0; i < ObserverSignScreenPayloads.LINE_COUNT; i++) lines.add("");
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
        if (!payload.open()) { clearRemote(); closeObserverScreen(); return; }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = payload.title();
        remoteVariant = payload.variant();
        remoteFrontText = payload.frontText();
        remoteCurrentLine = payload.currentLine();
        remoteColor = payload.color();
        remoteGlowing = payload.glowing();
        remoteLines = List.copyOf(payload.lines());
        ensureObserverScreen();
    }

    private static void ensureObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        boolean hanging = "hanging_sign".equals(remoteVariant);
        boolean correct = hanging ? minecraft.gui.screen() instanceof ObserverHangingSignScreen
                : minecraft.gui.screen() instanceof ObserverSignScreen;
        if (!correct) {
            suppressObserverScreenStop = true;
            try { minecraft.setScreenAndShow(createSignScreen(hanging)); }
            finally { suppressObserverScreenStop = false; }
        }
        if (minecraft.gui.screen() instanceof AbstractSignEditScreen screen) applySignState(screen);
    }

    private static void closeObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ObserverSignScreen
                || minecraft.gui.screen() instanceof ObserverHangingSignScreen)) return;
        suppressObserverScreenStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressObserverScreenStop = false; }
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

    private static Screen createSignScreen(boolean hanging) {
        SignBlockEntity sign = hanging
                ? new HangingSignBlockEntity(BlockPos.ZERO, Blocks.OAK_HANGING_SIGN.defaultBlockState())
                : new SignBlockEntity(BlockPos.ZERO, Blocks.OAK_SIGN.defaultBlockState());
        // Keep the synthetic block entity detached. Calling setText() here would invoke
        // markUpdated() and touch a client level; the semantic text is applied directly
        // to the production screen state immediately after it is constructed instead.
        return hanging ? new ObserverHangingSignScreen(sign, remoteFrontText)
                : new ObserverSignScreen(sign, remoteFrontText);
    }

    private static SignText signText() {
        SignText text = new SignText().setColor(DyeColor.byName(remoteColor, DyeColor.BLACK))
                .setHasGlowingText(remoteGlowing);
        for (int i = 0; i < ObserverSignScreenPayloads.LINE_COUNT; i++) {
            text = text.setMessage(i, Component.literal(i < remoteLines.size() ? remoteLines.get(i) : ""));
        }
        return text;
    }

    private static void applySignState(AbstractSignEditScreen screen) {
        AbstractSignEditScreenAccessor accessor = (AbstractSignEditScreenAccessor) screen;
        String[] messages = accessor.totem$getMessages();
        for (int i = 0; i < messages.length; i++) messages[i] = i < remoteLines.size() ? remoteLines.get(i) : "";
        accessor.totem$setLine(Math.clamp(remoteCurrentLine, 0, ObserverSignScreenPayloads.LINE_COUNT - 1));
        accessor.totem$setText(signText());
    }


    private static final class ObserverSignScreen extends SignEditScreen implements ObserverReadOnlyScreen {
        private ObserverSignScreen(SignBlockEntity sign, boolean front) { super(sign, front, false); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void tick() { }
        @Override public void onClose() { if (!suppressObserverScreenStop) ObserverVanillaScreenSupport.stopObserving(); }
        @Override public void removed() {
            if (minecraft != null) minecraft.textInputManager().stopTextInput();
            suppressedRemovalPackets++;
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick){
            super.extractRenderState(graphics,x,y,tick); extractedFrames++;
        }
    }

    private static final class ObserverHangingSignScreen extends HangingSignEditScreen implements ObserverReadOnlyScreen {
        private ObserverHangingSignScreen(SignBlockEntity sign, boolean front) { super(sign, front, false); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void tick() { }
        @Override public void onClose() { if (!suppressObserverScreenStop) ObserverVanillaScreenSupport.stopObserving(); }
        @Override public void removed() {
            if (minecraft != null) minecraft.textInputManager().stopTextInput();
            suppressedRemovalPackets++;
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick){
            super.extractRenderState(graphics,x,y,tick); extractedFrames++;
        }
    }
}
