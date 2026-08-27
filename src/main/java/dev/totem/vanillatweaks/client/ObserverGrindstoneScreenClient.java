package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.network.ObserverGrindstoneScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** Semantic adapter and local reconstruction for the vanilla Grindstone screen. */
public final class ObserverGrindstoneScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static final int HORIZONTAL_MARGIN = 8;
    static final int TEXT_HEIGHT = 9;
    static final int MACHINE_SLOT_BORDER_BOTTOM = 57;
    static final int STATUS_Y = 64;
    static final int INVENTORY_SLOT_BORDER_TOP = 83;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static boolean remotePrimaryInputPresent;
    private static boolean remoteSecondaryInputPresent;
    private static boolean remoteResultAvailable;
    private static boolean remoteInvalidCombination;
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();
    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverGrindstoneScreenClient() {}

    /** Keeps the Grindstone state readable inside a dedicated row below its machine slots. */
    static GrindstoneStatusLayout grindstoneStatusLayout(
            boolean primaryInputPresent,
            boolean secondaryInputPresent,
            boolean resultAvailable,
            boolean invalidCombination,
            int availableWidth,
            ToIntFunction<String> widthOf
    ) {
        String fullStatus;
        String compactStatus;
        if (invalidCombination) {
            fullStatus = "Incompatible inputs";
            compactStatus = "Invalid";
        } else if (resultAvailable) {
            fullStatus = "Result ready";
            compactStatus = "Ready";
        } else if (primaryInputPresent || secondaryInputPresent) {
            fullStatus = "Processing input";
            compactStatus = "Working";
        } else {
            fullStatus = "Insert item";
            compactStatus = "Insert";
        }

        int safeWidth = Math.max(0, availableWidth);
        String status = widthOf.applyAsInt(fullStatus) <= safeWidth
                ? fullStatus
                : widthOf.applyAsInt(compactStatus) <= safeWidth
                        ? compactStatus
                        : fitStatusText(compactStatus, safeWidth, widthOf);
        int textWidth = widthOf.applyAsInt(status);
        int statusX = Math.max(0, (safeWidth - textWidth) / 2);
        return new GrindstoneStatusLayout(status, statusX, textWidth, safeWidth);
    }

    static String fitStatusText(String text, int maxWidth, ToIntFunction<String> widthOf) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (widthOf.applyAsInt(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "…";
        if (widthOf.applyAsInt(ellipsis) > maxWidth) {
            return "";
        }
        int low = 0;
        int high = text.codePointCount(0, text.length());
        while (low < high) {
            int middle = (low + high + 1) / 2;
            int end = text.offsetByCodePoints(0, middle);
            if (widthOf.applyAsInt(text.substring(0, end) + ellipsis) <= maxWidth) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return text.substring(0, text.offsetByCodePoints(0, low)) + ellipsis;
    }

    static record GrindstoneStatusLayout(String status, int statusX, int textWidth, int availableWidth) {
        boolean fits() {
            return statusX >= 0 && statusX + textWidth <= availableWidth;
        }
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ObserverGrindstoneScreenPayloads.GrindstoneRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverGrindstoneScreenClient::tick);
    }

    public static boolean isGrindstoneScreen(Screen screen) {
        return screen != null && ObserverGrindstoneScreenPayloads.SCREEN_CLASS.equals(screen.getClass().getName());
    }

    private static void tick(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) closeTarget(false);
        else tickTarget(minecraft);
        if (!ObserverNativeClient.observerSessionActive()) { clearRemote(); closeMirror(); }
        else if (remoteOpen) ensureMirror();
    }

    private static void tickTarget(Minecraft minecraft) {
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverGrindstoneScreenPayloads.CAPABILITY);
        Screen screen = minecraft.gui.screen();
        if (!supported || !(screen instanceof GrindstoneScreen grindstoneScreen)) {
            closeTarget(supported);
            return;
        }
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        targetOpen = true;
        lastSnapshotNanos = now;
        ClientPlayNetworking.send(captureTargetState(grindstoneScreen, ++nextTargetSequence));
    }

    static ObserverGrindstoneScreenPayloads.GrindstoneState captureTargetState(
            GrindstoneScreen grindstoneScreen,
            long sequence
    ) {
        AbstractContainerMenu menu = grindstoneScreen.getMenu();
        boolean primary = menu.slots.size() > 0 && !menu.slots.get(0).getItem().isEmpty();
        boolean secondary = menu.slots.size() > 1 && !menu.slots.get(1).getItem().isEmpty();
        boolean result = menu.slots.size() > 2 && !menu.slots.get(2).getItem().isEmpty();
        boolean invalid = primary && secondary && !result;
        return new ObserverGrindstoneScreenPayloads.GrindstoneState(
                ObserverGrindstoneScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverGrindstoneScreenPayloads.FAMILY_ID, grindstoneScreen.getClass().getName(),
                grindstoneScreen.getTitle() == null ? "" : grindstoneScreen.getTitle().getString(),
                primary, secondary, result, invalid, captureSlots(menu));
    }

    private static void closeTarget(boolean canSend) {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) ClientPlayNetworking.send(ObserverGrindstoneScreenPayloads.closed(++nextTargetSequence));
    }

    private static List<ObserverNativeScreenPayloads.SlotState> captureSlots(AbstractContainerMenu menu) {
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>();
        int limit = Math.min(menu.slots.size(), ObserverNativeScreenPayloads.MAX_SLOTS);
        for (int i = 0; i < limit; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            slots.add(new ObserverNativeScreenPayloads.SlotState(i, slot.x, slot.y,
                    stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    stack.isEmpty() ? 0 : stack.getCount(), stack.isEmpty() ? 0 : stack.getDamageValue()));
        }
        return List.copyOf(slots);
    }

    private static void acceptRelay(ObserverGrindstoneScreenPayloads.GrindstoneRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverGrindstoneScreenPayloads.CAPABILITY)
                || targetId == null || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverGrindstoneScreenPayloads.PROTOCOL_VERSION
                || !ObserverGrindstoneScreenPayloads.FAMILY_ID.equals(payload.familyId())
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverGrindstoneScreenPayloads.FAMILY_ID,
                        payload.targetId(), payload.sequence())) return;
        if (!payload.open()) { clearRemote(); closeMirror(); return; }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = payload.title();
        remotePrimaryInputPresent = payload.primaryInputPresent();
        remoteSecondaryInputPresent = payload.secondaryInputPresent();
        remoteResultAvailable = payload.resultAvailable();
        remoteInvalidCombination = payload.invalidCombination();
        remoteSlots = List.copyOf(payload.slots());
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeGrindstoneMirrorScreen)) {
            suppressMirrorStop = true;
            try { minecraft.setScreenAndShow(new NativeGrindstoneMirrorScreen()); }
            finally { suppressMirrorStop = false; }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeGrindstoneMirrorScreen)) return;
        suppressMirrorStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressMirrorStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
        remotePrimaryInputPresent = false;
        remoteSecondaryInputPresent = false;
        remoteResultAvailable = false;
        remoteInvalidCombination = false;
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

    private static final class NativeGrindstoneMirrorScreen extends ObserverMirrorScreen {
        private NativeGrindstoneMirrorScreen() { super(Component.literal("Observer Grindstone")); }
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
            graphics.text(font, remoteTitle.isBlank() ? "Grindstone" : remoteTitle, left + 8, top + 6, 0xFF404040, false);
            GrindstoneStatusLayout status = grindstoneStatusLayout(
                    remotePrimaryInputPresent, remoteSecondaryInputPresent,
                    remoteResultAvailable, remoteInvalidCombination,
                    pw - HORIZONTAL_MARGIN * 2, font::width);
            graphics.text(font, status.status(), left + HORIZONTAL_MARGIN + status.statusX(), top + STATUS_Y,
                    remoteInvalidCombination ? 0xFFAA0000 : 0xFF555555, false);
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
