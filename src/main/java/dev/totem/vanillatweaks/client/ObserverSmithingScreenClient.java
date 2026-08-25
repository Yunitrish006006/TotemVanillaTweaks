package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import dev.totem.vanillatweaks.network.ObserverSmithingScreenPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Semantic adapter and local reconstruction for the vanilla Smithing Table screen. */
public final class ObserverSmithingScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;
    private static long lastRemoteSequence = -1L;
    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static boolean remoteRecipeError;
    private static boolean remoteResultAvailable;
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();
    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverSmithingScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ObserverSmithingScreenPayloads.SmithingRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverSmithingScreenClient::tick);
    }

    public static boolean isSmithingScreen(Screen screen) {
        return screen != null && ObserverSmithingScreenPayloads.SCREEN_CLASS.equals(screen.getClass().getName());
    }

    private static void tick(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) closeTarget(false);
        else tickTarget(minecraft);
        if (!ObserverNativeClient.observerSessionActive()) { clearRemote(); closeMirror(); }
        else if (remoteOpen) ensureMirror();
    }

    private static void tickTarget(Minecraft minecraft) {
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverSmithingScreenPayloads.CAPABILITY);
        Screen screen = minecraft.gui.screen();
        if (!supported || !isSmithingScreen(screen) || !(screen instanceof AbstractContainerScreen<?> container)) {
            closeTarget(supported);
            return;
        }
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        targetOpen = true;
        lastSnapshotNanos = now;
        AbstractContainerMenu menu = container.getMenu();
        boolean recipeError = invokeBoolean(menu, "hasRecipeError");
        boolean resultAvailable = menu.slots.size() > 3 && !menu.slots.get(3).getItem().isEmpty();
        ClientPlayNetworking.send(new ObserverSmithingScreenPayloads.SmithingState(
                ObserverSmithingScreenPayloads.PROTOCOL_VERSION, ++nextTargetSequence, true,
                ObserverSmithingScreenPayloads.FAMILY_ID, screen.getClass().getName(),
                screen.getTitle() == null ? "" : screen.getTitle().getString(), recipeError, resultAvailable,
                captureSlots(menu)));
    }

    private static void closeTarget(boolean canSend) {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) ClientPlayNetworking.send(ObserverSmithingScreenPayloads.closed(++nextTargetSequence));
    }

    private static List<ObserverNativeScreenPayloads.SlotState> captureSlots(AbstractContainerMenu menu) {
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

    private static boolean invokeBoolean(Object owner, String name) {
        try {
            Method method = owner.getClass().getMethod(name);
            Object value = method.invoke(owner);
            return value instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException error) {
            TotemVanillaTweaks.LOGGER.debug("Smithing semantic method {} unavailable on {}", name, owner.getClass().getName());
            return false;
        }
    }

    private static void acceptRelay(ObserverSmithingScreenPayloads.SmithingRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverSmithingScreenPayloads.CAPABILITY)
                || targetId == null || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverSmithingScreenPayloads.PROTOCOL_VERSION
                || !ObserverSmithingScreenPayloads.FAMILY_ID.equals(payload.familyId())
                || payload.sequence() <= lastRemoteSequence) return;
        lastRemoteSequence = payload.sequence();
        if (!payload.open()) { clearRemote(); closeMirror(); return; }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = payload.title();
        remoteRecipeError = payload.recipeError();
        remoteResultAvailable = payload.resultAvailable();
        remoteSlots = List.copyOf(payload.slots());
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeSmithingMirrorScreen)) {
            suppressMirrorStop = true;
            try { minecraft.setScreenAndShow(new NativeSmithingMirrorScreen()); }
            finally { suppressMirrorStop = false; }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeSmithingMirrorScreen)) return;
        suppressMirrorStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressMirrorStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
        remoteRecipeError = false;
        remoteResultAvailable = false;
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

    private static final class NativeSmithingMirrorScreen extends Screen {
        private NativeSmithingMirrorScreen() { super(Component.literal("Observer Smithing Table")); }
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
            graphics.text(font, remoteTitle.isBlank() ? "Smithing Table" : remoteTitle, left + 8, top + 6, 0xFF404040, false);
            graphics.text(font, "Template", left + 8, top + 26, 0xFF555555, false);
            graphics.text(font, "Base", left + 50, top + 26, 0xFF555555, false);
            graphics.text(font, "Addition", left + 82, top + 26, 0xFF555555, false);
            graphics.text(font, remoteRecipeError ? "Invalid recipe" : (remoteResultAvailable ? "Result ready" : "Waiting"),
                    left + 120, top + 26, remoteRecipeError ? 0xFFB00020 : 0xFF404040, false);
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
