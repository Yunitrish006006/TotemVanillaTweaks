package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.mixin.client.LoomScreenAccessor;
import dev.totem.vanillatweaks.network.ObserverLoomScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Semantic adapter and local reconstruction for the vanilla Loom screen. */
public final class ObserverLoomScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static long lastRemoteSequence = -1L;
    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static int remoteSelectedPatternIndex = -1;
    private static int remoteStartRow;
    private static boolean remoteDisplayPatterns;
    private static boolean remoteHasMaxPatterns;
    private static boolean remoteResultAvailable;
    private static List<String> remotePatternIds = List.of();
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();
    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverLoomScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ObserverLoomScreenPayloads.LoomRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverLoomScreenClient::tick);
    }

    public static boolean isLoomScreen(Screen screen) {
        return screen instanceof LoomScreen;
    }

    private static void tick(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) closeTarget(false);
        else tickTarget(minecraft);
        if (!ObserverNativeClient.observerSessionActive()) { clearRemote(); closeMirror(); }
        else if (remoteOpen) ensureMirror();
    }

    private static void tickTarget(Minecraft minecraft) {
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverLoomScreenPayloads.CAPABILITY);
        Screen current = minecraft.gui.screen();
        if (!supported || !(current instanceof LoomScreen screen)) {
            closeTarget(supported);
            return;
        }
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        targetOpen = true;
        lastSnapshotNanos = now;

        LoomMenu menu = screen.getMenu();
        LoomScreenAccessor accessor = (LoomScreenAccessor) screen;
        List<String> patterns = menu.getSelectablePatterns().stream()
                .limit(ObserverLoomScreenPayloads.MAX_PATTERNS)
                .map(holder -> holder.unwrapKey().map(key -> key.identifier().toString()).orElse("inline"))
                .toList();
        boolean resultAvailable = menu.slots.size() > 3 && !menu.slots.get(3).getItem().isEmpty();
        ClientPlayNetworking.send(new ObserverLoomScreenPayloads.LoomState(
                ObserverLoomScreenPayloads.PROTOCOL_VERSION, ++nextTargetSequence, true,
                ObserverLoomScreenPayloads.FAMILY_ID, screen.getClass().getName(),
                screen.getTitle() == null ? "" : screen.getTitle().getString(),
                menu.getSelectedBannerPatternIndex(), accessor.totem$getStartRow(),
                accessor.totem$getDisplayPatterns(), accessor.totem$getHasMaxPatterns(), resultAvailable,
                patterns, captureSlots(menu)));
    }

    private static void closeTarget(boolean canSend) {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) ClientPlayNetworking.send(ObserverLoomScreenPayloads.closed(++nextTargetSequence));
    }

    private static List<ObserverNativeScreenPayloads.SlotState> captureSlots(LoomMenu menu) {
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>();
        int limit = Math.min(menu.slots.size(), ObserverNativeScreenPayloads.MAX_SLOTS);
        for (int i = 0; i < limit; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            slots.add(new ObserverNativeScreenPayloads.SlotState(slot.index, slot.x, slot.y,
                    stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    stack.isEmpty() ? 0 : stack.getCount(), stack.isEmpty() ? 0 : stack.getDamageValue()));
        }
        return List.copyOf(slots);
    }

    private static void acceptRelay(ObserverLoomScreenPayloads.LoomRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverLoomScreenPayloads.CAPABILITY)
                || targetId == null || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverLoomScreenPayloads.PROTOCOL_VERSION
                || !ObserverLoomScreenPayloads.FAMILY_ID.equals(payload.familyId())
                || payload.sequence() <= lastRemoteSequence) return;
        lastRemoteSequence = payload.sequence();
        if (!payload.open()) { clearRemote(); closeMirror(); return; }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = payload.title();
        remoteSelectedPatternIndex = payload.selectedPatternIndex();
        remoteStartRow = payload.startRow();
        remoteDisplayPatterns = payload.displayPatterns();
        remoteHasMaxPatterns = payload.hasMaxPatterns();
        remoteResultAvailable = payload.resultAvailable();
        remotePatternIds = List.copyOf(payload.patternIds());
        remoteSlots = List.copyOf(payload.slots());
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeLoomMirrorScreen)) {
            suppressMirrorStop = true;
            try { minecraft.setScreenAndShow(new NativeLoomMirrorScreen()); }
            finally { suppressMirrorStop = false; }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeLoomMirrorScreen)) return;
        suppressMirrorStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressMirrorStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
        remoteSelectedPatternIndex = -1;
        remoteStartRow = 0;
        remoteDisplayPatterns = false;
        remoteHasMaxPatterns = false;
        remoteResultAvailable = false;
        remotePatternIds = List.of();
        remoteSlots = List.of();
    }

    private static ItemStack itemStack(ObserverNativeScreenPayloads.SlotState slot) {
        if (slot.itemId().isBlank() || slot.count() <= 0) return ItemStack.EMPTY;
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(slot.itemId()));
            if (item == null) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(item, Math.max(1, slot.count()));
            if (slot.damage() > 0 && stack.isDamageableItem()) stack.setDamageValue(slot.damage());
            return stack;
        } catch (RuntimeException error) { return ItemStack.EMPTY; }
    }

    private static final class NativeLoomMirrorScreen extends Screen {
        private NativeLoomMirrorScreen() { super(Component.literal("Observer Loom")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) ClientPlayNetworking.send(new ObserverPayloads.Stop());
            super.onClose();
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0x90000000);
            int pw = 176, ph = 166, left = (width - pw) / 2, top = (height - ph) / 2;
            graphics.fill(left, top, left + pw, top + ph, 0xFFE3E3E3);
            graphics.fill(left + 3, top + 3, left + pw - 3, top + ph - 3, 0xFFC6C6C6);
            graphics.text(font, remoteTitle.isBlank() ? "Loom" : remoteTitle, left + 8, top + 6, 0xFF404040, false);
            if (remoteDisplayPatterns) {
                int first = Math.max(0, remoteStartRow * 4);
                int max = Math.min(remotePatternIds.size(), first + 16);
                for (int i = first; i < max; i++) {
                    int local = i - first, col = local % 4, row = local / 4;
                    int x = left + 60 + col * 18, y = top + 14 + row * 18;
                    graphics.fill(x, y, x + 16, y + 16, i == remoteSelectedPatternIndex ? 0xFFFFFFFF : 0xFF777777);
                    graphics.fill(x + 2, y + 2, x + 14, y + 14, 0xFF303030);
                    graphics.centeredText(font, Integer.toString(i + 1), x + 8, y + 4, 0xFFFFFFFF);
                }
            }
            if (remoteHasMaxPatterns) graphics.text(font, "Pattern limit reached", left + 58, top + 73, 0xFFAA0000, false);
            else graphics.text(font, remoteResultAvailable ? "Result ready" : "Select a pattern", left + 58, top + 73, 0xFF555555, false);
            for (ObserverNativeScreenPayloads.SlotState slot : remoteSlots) {
                int sx = left + slot.x(), sy = top + slot.y();
                graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF666666);
                graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF202020);
                ItemStack stack = itemStack(slot);
                if (!stack.isEmpty()) { graphics.item(stack, sx, sy); graphics.itemDecorations(font, stack, sx, sy); }
            }
            extractedFrames++;
        }
    }
}
