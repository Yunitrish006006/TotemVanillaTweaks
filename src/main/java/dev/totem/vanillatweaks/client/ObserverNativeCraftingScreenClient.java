package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.vanillatweaks.network.ObserverCraftingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Target adapter and Observer-side reconstruction for player/crafting-table crafting screens. */
public final class ObserverNativeCraftingScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static final int DEFAULT_CONTENT_WIDTH = 176;
    private static final int DEFAULT_CONTENT_HEIGHT = 166;

    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static long lastRemoteSequence = -1L;
    private static boolean remoteOpen;
    private static String remoteVariant = "";
    private static String remoteScreenClass = "";
    private static String remoteTitle = "";
    private static int remoteContentWidth;
    private static int remoteContentHeight;
    private static int remoteMouseX;
    private static int remoteMouseY;
    private static int remoteGridWidth;
    private static int remoteGridHeight;
    private static int remoteResultSlotIndex;
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();

    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverNativeCraftingScreenClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverCraftingScreenPayloads.CraftingRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload))
        );
        ClientTickEvents.END_CLIENT_TICK.register(ObserverNativeCraftingScreenClient::tick);
    }

    static boolean isTargetCraftingScreen(Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof CraftingScreen;
    }

    static boolean isNativeMirrorScreen(Screen screen) {
        return screen instanceof NativeCraftingMirrorScreen;
    }

    static boolean hasStructuredRemoteScreen() {
        return remoteOpen && ObserverNativeClient.observerSessionActive();
    }

    static long extractedFrames() {
        return extractedFrames;
    }

    private static void tick(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) {
            targetOpen = false;
            lastSnapshotNanos = 0L;
        } else {
            tickTarget(minecraft);
        }

        if (!ObserverNativeClient.observerSessionActive()) {
            clearRemote();
            closeMirror();
            return;
        }
        if (remoteOpen) {
            ensureMirror();
        }
    }

    private static void tickTarget(Minecraft minecraft) {
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_CRAFTING);
        Screen screen = minecraft.gui.screen();
        if (!supported || !isTargetCraftingScreen(screen)) {
            closeTarget(supported);
            return;
        }

        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) {
            return;
        }
        targetOpen = true;
        lastSnapshotNanos = now;

        TargetSnapshot snapshot = capture(minecraft);
        boolean inventory = screen instanceof InventoryScreen;
        String variant = inventory
                ? ObserverCraftingScreenPayloads.VARIANT_PLAYER_2X2
                : ObserverCraftingScreenPayloads.VARIANT_TABLE_3X3;
        int grid = inventory ? 2 : 3;
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        ClientPlayNetworking.send(new ObserverCraftingScreenPayloads.CraftingState(
                ObserverCraftingScreenPayloads.PROTOCOL_VERSION,
                ++nextTargetSequence,
                true,
                ObserverNativeScreenPayloads.FAMILY_CRAFTING,
                variant,
                screen.getClass().getName(),
                title,
                snapshot.contentWidth(),
                snapshot.contentHeight(),
                snapshot.mouseX(),
                snapshot.mouseY(),
                grid,
                grid,
                0,
                snapshot.slots()
        ));
    }

    private static void closeTarget(boolean canSend) {
        if (!targetOpen) {
            return;
        }
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) {
            ClientPlayNetworking.send(new ObserverCraftingScreenPayloads.CraftingState(
                    ObserverCraftingScreenPayloads.PROTOCOL_VERSION,
                    ++nextTargetSequence,
                    false,
                    ObserverNativeScreenPayloads.FAMILY_CRAFTING,
                    "",
                    "",
                    "",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of()
            ));
        }
    }

    private static TargetSnapshot capture(Minecraft minecraft) {
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>();
        int maxX = 0;
        int maxY = 0;
        int limit = Math.min(minecraft.player.containerMenu.slots.size(), ObserverNativeScreenPayloads.MAX_SLOTS);
        for (int i = 0; i < limit; i++) {
            Slot slot = minecraft.player.containerMenu.slots.get(i);
            ItemStack stack = slot.getItem();
            String itemId = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            slots.add(new ObserverNativeScreenPayloads.SlotState(
                    slot.index,
                    slot.x,
                    slot.y,
                    itemId,
                    stack.isEmpty() ? 0 : stack.getCount(),
                    stack.isEmpty() ? 0 : stack.getDamageValue()
            ));
            maxX = Math.max(maxX, slot.x + 18);
            maxY = Math.max(maxY, slot.y + 18);
        }

        int contentWidth = Math.max(DEFAULT_CONTENT_WIDTH, maxX + 8);
        int contentHeight = Math.max(DEFAULT_CONTENT_HEIGHT, maxY + 8);
        int guiWidth = Math.max(1, minecraft.getWindow().getGuiScaledWidth());
        int guiHeight = Math.max(1, minecraft.getWindow().getGuiScaledHeight());
        int screenWidth = Math.max(1, minecraft.getWindow().getScreenWidth());
        int screenHeight = Math.max(1, minecraft.getWindow().getScreenHeight());
        int guiMouseX = (int) Math.round(minecraft.mouseHandler.xpos() * guiWidth / screenWidth);
        int guiMouseY = (int) Math.round(minecraft.mouseHandler.ypos() * guiHeight / screenHeight);
        return new TargetSnapshot(
                contentWidth,
                contentHeight,
                guiMouseX - (guiWidth - contentWidth) / 2,
                guiMouseY - (guiHeight - contentHeight) / 2,
                List.copyOf(slots)
        );
    }

    private static void acceptRelay(ObserverCraftingScreenPayloads.CraftingRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_CRAFTING)
                || targetId == null
                || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverCraftingScreenPayloads.PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_CRAFTING.equals(payload.familyId())
                || payload.sequence() <= lastRemoteSequence) {
            return;
        }
        lastRemoteSequence = payload.sequence();
        if (!payload.open()) {
            clearRemote();
            closeMirror();
            return;
        }

        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteVariant = payload.variant();
        remoteScreenClass = payload.screenClass();
        remoteTitle = payload.title();
        remoteContentWidth = clamp(payload.contentWidth(), 64, 512);
        remoteContentHeight = clamp(payload.contentHeight(), 64, 512);
        remoteMouseX = payload.mouseX();
        remoteMouseY = payload.mouseY();
        remoteGridWidth = payload.gridWidth();
        remoteGridHeight = payload.gridHeight();
        remoteResultSlotIndex = payload.resultSlotIndex();
        remoteSlots = List.copyOf(payload.slots());
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) {
            return;
        }
        if (!(minecraft.gui.screen() instanceof NativeCraftingMirrorScreen)) {
            suppressMirrorStop = true;
            try {
                minecraft.setScreenAndShow(new NativeCraftingMirrorScreen());
            } finally {
                suppressMirrorStop = false;
            }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeCraftingMirrorScreen)) {
            return;
        }
        suppressMirrorStop = true;
        try {
            minecraft.setScreenAndShow(null);
        } finally {
            suppressMirrorStop = false;
        }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteVariant = "";
        remoteScreenClass = "";
        remoteTitle = "";
        remoteContentWidth = 0;
        remoteContentHeight = 0;
        remoteMouseX = 0;
        remoteMouseY = 0;
        remoteGridWidth = 0;
        remoteGridHeight = 0;
        remoteResultSlotIndex = 0;
        remoteSlots = List.of();
    }

    private static ItemStack itemStack(ObserverNativeScreenPayloads.SlotState slot) {
        if (slot.itemId().isBlank() || slot.count() <= 0) {
            return ItemStack.EMPTY;
        }
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(slot.itemId()));
            if (item == null) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(item, Math.max(1, slot.count()));
            if (slot.damage() > 0 && stack.isDamageableItem()) {
                stack.setDamageValue(slot.damage());
            }
            return stack;
        } catch (RuntimeException error) {
            TotemVanillaTweaks.LOGGER.debug("Ignoring invalid crafting Observer item id {}", slot.itemId());
            return ItemStack.EMPTY;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record TargetSnapshot(int contentWidth, int contentHeight, int mouseX, int mouseY,
                                  List<ObserverNativeScreenPayloads.SlotState> slots) {
    }

    private static final class NativeCraftingMirrorScreen extends Screen {
        private NativeCraftingMirrorScreen() {
            super(Component.literal("Observer Crafting"));
        }

        @Override
        public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) {
                ClientPlayNetworking.send(new ObserverPayloads.Stop());
            }
            super.onClose();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0x90000000);
            int contentWidth = clamp(remoteContentWidth, 64, Math.max(64, width - 24));
            int contentHeight = clamp(remoteContentHeight, 64, Math.max(64, height - 24));
            int left = (width - contentWidth) / 2;
            int top = (height - contentHeight) / 2;
            graphics.fill(left - 7, top - 18, left + contentWidth + 7, top + contentHeight + 7, 0xEE202020);
            graphics.fill(left - 5, top - 16, left + contentWidth + 5, top - 3, 0xFF303030);

            String title = remoteTitle.isBlank() ? remoteScreenClass : remoteTitle;
            graphics.text(this.minecraft.font, title, left, top - 14, 0xFFFFFFFF, true);
            String mode = "Crafting " + remoteGridWidth + "x" + remoteGridHeight + " / " + remoteVariant;
            graphics.text(this.minecraft.font, mode,
                    left + contentWidth - this.minecraft.font.width(mode), top - 14, 0xFF9E9E9E, false);

            for (ObserverNativeScreenPayloads.SlotState slot : remoteSlots) {
                int slotX = left + slot.x();
                int slotY = top + slot.y();
                boolean result = slot.index() == remoteResultSlotIndex;
                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, result ? 0xFF8A7337 : 0xFF555555);
                graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF171717);
                ItemStack stack = itemStack(slot);
                if (!stack.isEmpty()) {
                    graphics.item(stack, slotX + 1, slotY + 1);
                    graphics.itemDecorations(this.minecraft.font, stack, slotX + 1, slotY + 1);
                }
            }

            int cursorX = left + remoteMouseX;
            int cursorY = top + remoteMouseY;
            if (cursorX >= 0 && cursorX < width && cursorY >= 0 && cursorY < height) {
                graphics.fill(cursorX - 4, cursorY, cursorX + 5, cursorY + 1, 0xFFFFFFFF);
                graphics.fill(cursorX, cursorY - 4, cursorX + 1, cursorY + 5, 0xFFFFFFFF);
            }
            extractedFrames++;
        }
    }
}
