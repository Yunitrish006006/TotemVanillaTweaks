package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import dev.totem.vanillatweaks.network.ObserverRemnantBackpackPayloads;
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

/** Optional reflective adapter for TotemRemnant's BackpackScreen. */
public final class ObserverRemnantBackpackScreenClient {
    private static final String BACKPACK_SCREEN_CLASS = "dev.totem.remnant.client.screen.BackpackScreen";
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;
    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static int remoteRowCount;
    private static int remoteVisibleRows;
    private static int remoteFirstVisibleRow;
    private static int remoteUpgradeSlotCount;
    private static boolean remoteCraftingEnabled;
    private static boolean remoteEnderAccessVisible;
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();
    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverRemnantBackpackScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ObserverRemnantBackpackPayloads.BackpackRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverRemnantBackpackScreenClient::tick);
    }

    public static boolean isRemnantBackpackScreen(Screen screen) {
        return screen != null && BACKPACK_SCREEN_CLASS.equals(screen.getClass().getName());
    }

    private static void tick(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) {
            targetOpen = false;
            lastSnapshotNanos = 0L;
        } else tickTarget(minecraft);
        if (!ObserverNativeClient.observerSessionActive()) {
            clearRemote();
            closeMirror();
        } else if (remoteOpen) ensureMirror();
    }

    private static void tickTarget(Minecraft minecraft) {
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_REMNANT_BACKPACK);
        Screen screen = minecraft.gui.screen();
        if (!supported || !isRemnantBackpackScreen(screen) || !(screen instanceof AbstractContainerScreen<?> container)) {
            closeTarget(supported);
            return;
        }
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        targetOpen = true;
        lastSnapshotNanos = now;
        ClientPlayNetworking.send(captureTargetState(screen, ++nextTargetSequence));
    }

    private static void closeTarget(boolean canSend) {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) ClientPlayNetworking.send(closedTargetState(++nextTargetSequence));
    }

    /** Exact production extractor shared by the target tick and cross-module runtime gate. */
    public static ObserverRemnantBackpackPayloads.BackpackState captureTargetState(Screen screen, long sequence) {
        if (!isRemnantBackpackScreen(screen) || !(screen instanceof AbstractContainerScreen<?> container)) {
            throw new IllegalArgumentException("Expected TotemRemnant BackpackScreen");
        }
        AbstractContainerMenu menu = container.getMenu();
        int rows = invokeInt(menu, "getRowCount");
        int visibleRows = Math.min(rows, 6);
        return new ObserverRemnantBackpackPayloads.BackpackState(
                ObserverRemnantBackpackPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverNativeScreenPayloads.FAMILY_REMNANT_BACKPACK, screen.getClass().getName(),
                screen.getTitle() == null ? "" : screen.getTitle().getString(), rows, visibleRows,
                invokeInt(screen, "firstVisibleRow"), invokeInt(menu, "upgradeSlotCount"),
                invokeBoolean(menu, "isCraftingEnabled"), invokeBoolean(screen, "isEnderAccessButtonVisible"),
                captureSlots(menu));
    }

    public static ObserverRemnantBackpackPayloads.BackpackState closedTargetState(long sequence) {
        return new ObserverRemnantBackpackPayloads.BackpackState(
                ObserverRemnantBackpackPayloads.PROTOCOL_VERSION, sequence, false,
                ObserverNativeScreenPayloads.FAMILY_REMNANT_BACKPACK, "", "", 0, 0, 0, 0,
                false, false, List.of());
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

    private static int invokeInt(Object owner, String name) {
        Object value = invoke(owner, name);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static boolean invokeBoolean(Object owner, String name) {
        Object value = invoke(owner, name);
        return value instanceof Boolean bool && bool;
    }

    private static Object invoke(Object owner, String name) {
        try {
            Method method = owner.getClass().getMethod(name);
            return method.invoke(owner);
        } catch (ReflectiveOperationException error) {
            TotemVanillaTweaks.LOGGER.debug("Remnant backpack semantic method {} unavailable on {}", name, owner.getClass().getName());
            return null;
        }
    }

    private static void acceptRelay(ObserverRemnantBackpackPayloads.BackpackRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_REMNANT_BACKPACK)
                || targetId == null || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverRemnantBackpackPayloads.PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_REMNANT_BACKPACK.equals(payload.familyId())
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverNativeScreenPayloads.FAMILY_REMNANT_BACKPACK,
                        payload.targetId(), payload.sequence())) return;
        if (!payload.open()) {
            clearRemote(); closeMirror(); return;
        }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = payload.title();
        remoteRowCount = payload.rowCount();
        remoteVisibleRows = payload.visibleRows();
        remoteFirstVisibleRow = payload.firstVisibleRow();
        remoteUpgradeSlotCount = payload.upgradeSlotCount();
        remoteCraftingEnabled = payload.craftingEnabled();
        remoteEnderAccessVisible = payload.enderAccessVisible();
        remoteSlots = List.copyOf(payload.slots());
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeRemnantBackpackMirrorScreen)) {
            suppressMirrorStop = true;
            try { minecraft.setScreenAndShow(new NativeRemnantBackpackMirrorScreen()); }
            finally { suppressMirrorStop = false; }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeRemnantBackpackMirrorScreen)) return;
        suppressMirrorStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressMirrorStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false; remoteTitle = ""; remoteRowCount = 0; remoteVisibleRows = 0; remoteFirstVisibleRow = 0;
        remoteUpgradeSlotCount = 0; remoteCraftingEnabled = false; remoteEnderAccessVisible = false; remoteSlots = List.of();
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

    private static final class NativeRemnantBackpackMirrorScreen extends ObserverMirrorScreen {
        private NativeRemnantBackpackMirrorScreen() { super(Component.literal("Observer Backpack")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) ClientPlayNetworking.send(new ObserverPayloads.Stop());
            super.onClose();
        }
        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0x90000000);
            int storageWidth = 176;
            int storageHeight = 114 + remoteVisibleRows * 18;
            int sideWidth = remoteUpgradeSlotCount > 0 ? 106 : 0;
            int left = (width - storageWidth - sideWidth) / 2;
            int top = (height - storageHeight) / 2;
            graphics.fill(left, top, left + storageWidth, top + storageHeight, 0xEEC6C6C6);
            graphics.text(font, remoteTitle.isBlank() ? "Backpack" : remoteTitle, left + 8, top + 7, 0xFF404040, false);
            graphics.text(font, "Rows " + remoteRowCount + "  View " + (remoteFirstVisibleRow + 1) + "-" + (remoteFirstVisibleRow + remoteVisibleRows),
                    left + 8, top + 18, 0xFF555555, false);
            if (sideWidth > 0) {
                int side = left + storageWidth + 4;
                graphics.fill(side, top, side + 102, top + 40, 0xEEC6C6C6);
                graphics.text(font, "Upgrades " + remoteUpgradeSlotCount, side + 6, top + 7, 0xFF404040, false);
                if (remoteEnderAccessVisible) graphics.text(font, "Ender Access", side + 6, top + 23, 0xFF404040, false);
                if (remoteCraftingEnabled) {
                    graphics.fill(side, top + 44, side + 102, top + 122, 0xEEC6C6C6);
                    graphics.text(font, "Crafting 3x3", side + 6, top + 51, 0xFF404040, false);
                }
            }
            for (ObserverNativeScreenPayloads.SlotState slot : remoteSlots) {
                if (slot.x() < 0 || slot.y() < 0 || slot.x() > 280 || slot.y() > storageHeight + 20) continue;
                int sx = left + slot.x();
                int sy = top + slot.y();
                graphics.fill(sx, sy, sx + 18, sy + 18, 0xFF555555);
                graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF171717);
                ItemStack stack = itemStack(slot);
                if (!stack.isEmpty()) {
                    graphics.item(stack, sx + 1, sy + 1);
                    graphics.itemDecorations(font, stack, sx + 1, sy + 1);
                }
            }
            extractedFrames++;
        }
    }
}
