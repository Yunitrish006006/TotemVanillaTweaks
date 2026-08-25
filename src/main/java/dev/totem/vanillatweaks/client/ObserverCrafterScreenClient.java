package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.network.ObserverCrafterScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Semantic adapter and local reconstruction for the vanilla Crafter screen. */
public final class ObserverCrafterScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static long lastRemoteSequence = -1L;
    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static boolean remotePowered;
    private static int remoteDisabledMask;
    private static int remoteOccupiedInputSlots;
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();
    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverCrafterScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ObserverCrafterScreenPayloads.CrafterRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverCrafterScreenClient::tick);
    }

    public static boolean isCrafterScreen(Screen screen) {
        return screen instanceof CrafterScreen;
    }

    private static void tick(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) closeTarget(false);
        else tickTarget(minecraft);
        if (!ObserverNativeClient.observerSessionActive()) { clearRemote(); closeMirror(); }
        else if (remoteOpen) ensureMirror();
    }

    private static void tickTarget(Minecraft minecraft) {
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverCrafterScreenPayloads.CAPABILITY);
        Screen current = minecraft.gui.screen();
        if (!supported || !(current instanceof CrafterScreen screen)) {
            closeTarget(supported);
            return;
        }
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        targetOpen = true;
        lastSnapshotNanos = now;

        CrafterMenu menu = screen.getMenu();
        List<ObserverNativeScreenPayloads.SlotState> slots = captureSlots(menu);
        int disabledMask = 0;
        int occupied = 0;
        for (int i = 0; i < 9; i++) {
            if (menu.isSlotDisabled(i)) disabledMask |= 1 << i;
            if (i < menu.slots.size() && !menu.slots.get(i).getItem().isEmpty()) occupied++;
        }
        ClientPlayNetworking.send(new ObserverCrafterScreenPayloads.CrafterState(
                ObserverCrafterScreenPayloads.PROTOCOL_VERSION, ++nextTargetSequence, true,
                ObserverCrafterScreenPayloads.FAMILY_ID, screen.getClass().getName(),
                screen.getTitle() == null ? "" : screen.getTitle().getString(),
                menu.isPowered(), disabledMask, occupied, slots));
    }

    private static void closeTarget(boolean canSend) {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) ClientPlayNetworking.send(ObserverCrafterScreenPayloads.closed(++nextTargetSequence));
    }

    private static List<ObserverNativeScreenPayloads.SlotState> captureSlots(CrafterMenu menu) {
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

    private static void acceptRelay(ObserverCrafterScreenPayloads.CrafterRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverCrafterScreenPayloads.CAPABILITY)
                || targetId == null || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverCrafterScreenPayloads.PROTOCOL_VERSION
                || !ObserverCrafterScreenPayloads.FAMILY_ID.equals(payload.familyId())
                || payload.sequence() <= lastRemoteSequence) return;
        lastRemoteSequence = payload.sequence();
        if (!payload.open()) { clearRemote(); closeMirror(); return; }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = payload.title();
        remotePowered = payload.powered();
        remoteDisabledMask = payload.disabledMask();
        remoteOccupiedInputSlots = payload.occupiedInputSlots();
        remoteSlots = List.copyOf(payload.slots());
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeCrafterMirrorScreen)) {
            suppressMirrorStop = true;
            try { minecraft.setScreenAndShow(new NativeCrafterMirrorScreen()); }
            finally { suppressMirrorStop = false; }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeCrafterMirrorScreen)) return;
        suppressMirrorStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressMirrorStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
        remotePowered = false;
        remoteDisabledMask = 0;
        remoteOccupiedInputSlots = 0;
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

    private static final class NativeCrafterMirrorScreen extends Screen {
        private NativeCrafterMirrorScreen() { super(Component.literal("Observer Crafter")); }
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
            graphics.text(font, remoteTitle.isBlank() ? "Crafter" : remoteTitle, left + 8, top + 6, 0xFF404040, false);
            graphics.text(font, remotePowered ? "Redstone: powered" : "Redstone: idle", left + 96, top + 22,
                    remotePowered ? 0xFF8A2020 : 0xFF555555, false);
            graphics.text(font, "Inputs: " + remoteOccupiedInputSlots + "/9", left + 96, top + 36, 0xFF555555, false);
            graphics.text(font, "Disabled: " + Integer.bitCount(remoteDisabledMask), left + 96, top + 50, 0xFF555555, false);
            for (int i = 0; i < remoteSlots.size(); i++) {
                ObserverNativeScreenPayloads.SlotState slot = remoteSlots.get(i);
                int sx = left + slot.x(), sy = top + slot.y();
                boolean disabled = i < 9 && (remoteDisabledMask & (1 << i)) != 0;
                graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, disabled ? 0xFF7A3030 : 0xFF666666);
                graphics.fill(sx, sy, sx + 16, sy + 16, disabled ? 0xFF4A2020 : 0xFF202020);
                if (disabled) {
                    graphics.text(font, "X", sx + 5, sy + 4, 0xFFFF8080, false);
                    continue;
                }
                ItemStack stack = itemStack(slot);
                if (!stack.isEmpty()) { graphics.item(stack, sx, sy); graphics.itemDecorations(font, stack, sx, sy); }
            }
            extractedFrames++;
        }
    }
}
