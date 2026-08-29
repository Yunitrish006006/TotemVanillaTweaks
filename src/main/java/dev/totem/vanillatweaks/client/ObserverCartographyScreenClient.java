package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.network.ObserverCartographyScreenPayloads;
import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Semantic adapter and local reconstruction for the vanilla Cartography Table screen. */
public final class ObserverCartographyScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static String remoteOperation = "none";
    private static boolean remoteMapPresent;
    private static boolean remoteAdditionalPresent;
    private static boolean remoteResultAvailable;
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();
    private static boolean suppressObserverScreenStop;
    private static long extractedFrames;

    private ObserverCartographyScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ObserverCartographyScreenPayloads.CartographyRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverCartographyScreenClient::tick);
    }

    public static boolean isCartographyScreen(Screen screen) {
        return screen instanceof CartographyTableScreen;
    }

    private static void tick(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) closeTarget(false);
        else tickTarget(minecraft);
        if (!ObserverNativeClient.observerSessionActive()) { clearRemote(); closeObserverScreen(); }
        else if (remoteOpen) ensureObserverScreen();
    }

    private static void tickTarget(Minecraft minecraft) {
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverCartographyScreenPayloads.CAPABILITY);
        Screen current = minecraft.gui.screen();
        if (!supported || !(current instanceof CartographyTableScreen screen)) {
            closeTarget(supported);
            return;
        }
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        targetOpen = true;
        lastSnapshotNanos = now;

        ClientPlayNetworking.send(captureTargetState(screen, ++nextTargetSequence));
    }

    static ObserverCartographyScreenPayloads.CartographyState captureTargetState(
            CartographyTableScreen screen,
            long sequence
    ) {
        CartographyTableMenu menu = screen.getMenu();
        List<ObserverNativeScreenPayloads.SlotState> slots = captureSlots(menu);
        String mapId = itemId(slots, 0);
        String extraId = itemId(slots, 1);
        boolean mapPresent = "minecraft:filled_map".equals(mapId);
        boolean additionalPresent = !extraId.isBlank();
        boolean resultAvailable = slotPresent(slots, 2);
        return new ObserverCartographyScreenPayloads.CartographyState(
                ObserverCartographyScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverCartographyScreenPayloads.FAMILY_ID, screen.getClass().getName(),
                screen.getTitle() == null ? "" : screen.getTitle().getString(),
                operation(mapPresent, extraId), mapPresent, additionalPresent, resultAvailable, slots);
    }

    private static String operation(boolean mapPresent, String extraId) {
        if (!mapPresent) return "none";
        return switch (extraId) {
            case "minecraft:paper" -> "scale";
            case "minecraft:map" -> "clone";
            case "minecraft:glass_pane" -> "lock";
            default -> "none";
        };
    }

    private static void closeTarget(boolean canSend) {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) ClientPlayNetworking.send(ObserverCartographyScreenPayloads.closed(++nextTargetSequence));
    }

    private static List<ObserverNativeScreenPayloads.SlotState> captureSlots(CartographyTableMenu menu) {
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

    private static String itemId(List<ObserverNativeScreenPayloads.SlotState> slots, int index) {
        for (var slot : slots) if (slot.index() == index) return slot.count() > 0 ? slot.itemId() : "";
        return "";
    }

    private static boolean slotPresent(List<ObserverNativeScreenPayloads.SlotState> slots, int index) {
        for (var slot : slots) if (slot.index() == index) return slot.count() > 0 && !slot.itemId().isBlank();
        return false;
    }

    private static void acceptRelay(ObserverCartographyScreenPayloads.CartographyRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverCartographyScreenPayloads.CAPABILITY)
                || targetId == null || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverCartographyScreenPayloads.PROTOCOL_VERSION
                || !ObserverCartographyScreenPayloads.FAMILY_ID.equals(payload.familyId())
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverCartographyScreenPayloads.FAMILY_ID,
                        payload.targetId(), payload.sequence())) return;
        if (!payload.open()) { clearRemote(); closeObserverScreen(); return; }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = payload.title();
        remoteOperation = payload.operation();
        remoteMapPresent = payload.mapPresent();
        remoteAdditionalPresent = payload.additionalPresent();
        remoteResultAvailable = payload.resultAvailable();
        remoteSlots = List.copyOf(payload.slots());
        ensureObserverScreen();
    }

    private static void ensureObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof ObserverCartographyScreen)) {
            suppressObserverScreenStop = true;
            try {
                var inventory = ObserverVanillaScreenSupport.detachedInventory();
                minecraft.setScreenAndShow(new ObserverCartographyScreen(new CartographyTableMenu(-1, inventory), inventory,
                        Component.literal(remoteTitle.isBlank() ? "Cartography Table" : remoteTitle)));
            }
            finally { suppressObserverScreenStop = false; }
        }
        if (minecraft.gui.screen() instanceof ObserverCartographyScreen screen) {
            ObserverVanillaScreenSupport.applyMenu(screen.getMenu(), remoteSlots);
        }
    }

    private static void closeObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ObserverCartographyScreen)) return;
        suppressObserverScreenStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressObserverScreenStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
        remoteOperation = "none";
        remoteMapPresent = false;
        remoteAdditionalPresent = false;
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

    private static String operationLabel() {
        return switch (remoteOperation) {
            case "scale" -> "Scale map";
            case "clone" -> "Clone map";
            case "lock" -> "Lock map";
            default -> remoteMapPresent && remoteAdditionalPresent ? "Invalid cartography operation" : "Add map and material";
        };
    }


    private static final class ObserverCartographyScreen extends CartographyTableScreen implements ObserverReadOnlyScreen {
        private ObserverCartographyScreen(CartographyTableMenu menu, net.minecraft.world.entity.player.Inventory inventory,
                                          Component title) { super(menu, inventory, title); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void onClose() { if (!suppressObserverScreenStop) ObserverVanillaScreenSupport.stopObserving(); }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick){
            super.extractRenderState(graphics,x,y,tick); extractedFrames++;
        }
    }
}
