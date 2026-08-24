package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Protocol-native container screen transport. The Target sends logical slot state only;
 * the Observer reconstructs item icons and layout locally with its own Minecraft renderer.
 */
public final class ObserverNativeScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static final int DEFAULT_CONTENT_WIDTH = 176;
    private static final int DEFAULT_CONTENT_HEIGHT = 166;

    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetContainerOpen;

    private static long lastRemoteSequence = -1L;
    private static boolean remoteContainerOpen;
    private static String remoteScreenClass = "";
    private static String remoteTitle = "";
    private static int remoteContentWidth;
    private static int remoteContentHeight;
    private static int remoteMouseX;
    private static int remoteMouseY;
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();
    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverNativeScreenClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverNativeScreenPayloads.ContainerRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload))
        );
        ClientTickEvents.END_CLIENT_TICK.register(ObserverNativeScreenClient::tick);
    }

    static boolean isStructuredTargetScreen(Screen screen) {
        return ObserverNativeClient.targetStateEnabled() && screen instanceof AbstractContainerScreen<?>;
    }

    static boolean hasStructuredRemoteScreen() {
        return remoteContainerOpen && ObserverNativeClient.observerSessionActive();
    }

    static boolean isNativeContainerMirror(Screen screen) {
        return screen instanceof NativeContainerMirrorScreen;
    }

    static long extractedFrames() {
        return extractedFrames;
    }

    static long lastRemoteSequence() {
        return lastRemoteSequence;
    }

    private static void tick(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) {
            targetContainerOpen = false;
            lastSnapshotNanos = 0L;
        } else {
            tickTarget(minecraft);
        }

        if (!ObserverNativeClient.observerSessionActive() && remoteContainerOpen) {
            clearRemoteContainer();
            closeMirrorScreen();
        }
    }

    private static void tickTarget(Minecraft minecraft) {
        Screen screen = minecraft.gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            if (targetContainerOpen) {
                targetContainerOpen = false;
                ClientPlayNetworking.send(new ObserverNativeScreenPayloads.ContainerState(
                        ObserverNativeScreenPayloads.SCREEN_PROTOCOL_VERSION,
                        ++nextTargetSequence,
                        false,
                        "",
                        "",
                        0,
                        0,
                        0,
                        0,
                        List.of()
                ));
            }
            return;
        }

        long now = System.nanoTime();
        if (targetContainerOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) {
            return;
        }
        targetContainerOpen = true;
        lastSnapshotNanos = now;

        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>();
        int maxX = 0;
        int maxY = 0;
        int slotLimit = Math.min(
                minecraft.player.containerMenu.slots.size(),
                ObserverNativeScreenPayloads.MAX_SLOTS
        );
        for (int i = 0; i < slotLimit; i++) {
            Slot slot = minecraft.player.containerMenu.slots.get(i);
            ItemStack stack = slot.getItem();
            String itemId = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            int count = stack.isEmpty() ? 0 : stack.getCount();
            int damage = stack.isEmpty() ? 0 : stack.getDamageValue();
            slots.add(new ObserverNativeScreenPayloads.SlotState(
                    slot.index,
                    slot.x,
                    slot.y,
                    itemId,
                    count,
                    damage
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
        int mouseX = guiMouseX - (guiWidth - contentWidth) / 2;
        int mouseY = guiMouseY - (guiHeight - contentHeight) / 2;

        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        ClientPlayNetworking.send(new ObserverNativeScreenPayloads.ContainerState(
                ObserverNativeScreenPayloads.SCREEN_PROTOCOL_VERSION,
                ++nextTargetSequence,
                true,
                screen.getClass().getName(),
                title,
                contentWidth,
                contentHeight,
                mouseX,
                mouseY,
                List.copyOf(slots)
        ));
    }

    private static void acceptRelay(ObserverNativeScreenPayloads.ContainerRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || targetId == null
                || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverNativeScreenPayloads.SCREEN_PROTOCOL_VERSION
                || payload.sequence() <= lastRemoteSequence) {
            return;
        }
        lastRemoteSequence = payload.sequence();
        if (!payload.open()) {
            clearRemoteContainer();
            closeMirrorScreen();
            return;
        }

        remoteContainerOpen = true;
        remoteScreenClass = payload.screenClass();
        remoteTitle = payload.title();
        remoteContentWidth = clamp(payload.contentWidth(), 64, 512);
        remoteContentHeight = clamp(payload.contentHeight(), 64, 512);
        remoteMouseX = payload.mouseX();
        remoteMouseY = payload.mouseY();
        remoteSlots = List.copyOf(payload.slots());
        ensureMirrorScreen();
    }

    private static void ensureMirrorScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteContainerOpen || !ObserverNativeClient.observerSessionActive()) {
            return;
        }
        if (!(minecraft.gui.screen() instanceof NativeContainerMirrorScreen)) {
            minecraft.setScreenAndShow(new NativeContainerMirrorScreen());
        }
    }

    private static void closeMirrorScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeContainerMirrorScreen)) {
            return;
        }
        suppressMirrorStop = true;
        try {
            minecraft.setScreenAndShow(null);
        } finally {
            suppressMirrorStop = false;
        }
    }

    private static void clearRemoteContainer() {
        remoteContainerOpen = false;
        remoteScreenClass = "";
        remoteTitle = "";
        remoteContentWidth = 0;
        remoteContentHeight = 0;
        remoteMouseX = 0;
        remoteMouseY = 0;
        remoteSlots = List.of();
    }

    private static ItemStack itemStack(ObserverNativeScreenPayloads.SlotState slot) {
        if (slot.itemId().isBlank() || slot.count() <= 0) {
            return ItemStack.EMPTY;
        }
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(slot.itemId()));
            ItemStack stack = new ItemStack(item, Math.max(1, slot.count()));
            if (slot.damage() > 0 && stack.isDamageableItem()) {
                stack.setDamageValue(slot.damage());
            }
            return stack;
        } catch (RuntimeException error) {
            TotemVanillaTweaks.LOGGER.debug("Ignoring invalid Observer item id {}", slot.itemId());
            return ItemStack.EMPTY;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class NativeContainerMirrorScreen extends Screen {
        private NativeContainerMirrorScreen() {
            super(Component.literal("Observer Container"));
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
            graphics.text(
                    this.minecraft.font,
                    "Protocol-native container",
                    left + contentWidth - this.minecraft.font.width("Protocol-native container"),
                    top - 14,
                    0xFF9E9E9E,
                    false
            );

            for (ObserverNativeScreenPayloads.SlotState slot : remoteSlots) {
                int slotX = left + slot.x();
                int slotY = top + slot.y();
                if (slotX < left - 2 || slotY < top - 2
                        || slotX + 18 > left + contentWidth + 2
                        || slotY + 18 > top + contentHeight + 2) {
                    continue;
                }
                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF555555);
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
    }
}
