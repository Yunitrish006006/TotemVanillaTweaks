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
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Protocol-native screen transport. Negotiated semantic families use structured snapshots;
 * unsupported screens use metadata-only local placeholders. Neither path receives pixels.
 */
public final class ObserverNativeScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static final int DEFAULT_CONTENT_WIDTH = 176;
    private static final int DEFAULT_CONTENT_HEIGHT = 166;
    private static final int GENERIC_SCREEN_DELAY_TICKS = 3;

    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetContainerOpen;
    private static boolean targetFurnaceOpen;

    private static long lastRemoteSequence = -1L;
    private static boolean remoteContainerOpen;
    private static String remoteScreenClass = "";
    private static String remoteTitle = "";
    private static int remoteContentWidth;
    private static int remoteContentHeight;
    private static int remoteMouseX;
    private static int remoteMouseY;
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();

    private static boolean remoteFurnaceOpen;
    private static String remoteFurnaceClass = "";
    private static String remoteFurnaceTitle = "";
    private static int remoteFurnaceContentWidth;
    private static int remoteFurnaceContentHeight;
    private static int remoteFurnaceMouseX;
    private static int remoteFurnaceMouseY;
    private static List<ObserverNativeScreenPayloads.SlotState> remoteFurnaceSlots = List.of();
    private static float remoteCookProgress;
    private static float remoteFuelProgress;
    private static boolean remoteFurnaceLit;

    private static boolean remoteGenericOpen;
    private static String remoteGenericClass = "";
    private static String remoteGenericTitle = "";
    private static int genericScreenTicks;

    private static boolean suppressMirrorStop;
    private static long extractedFrames;
    private static long furnaceExtractedFrames;
    private static long genericExtractedFrames;

    private ObserverNativeScreenClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverNativeScreenPayloads.ContainerRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverNativeScreenPayloads.FurnaceRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptFurnaceRelay(payload))
        );
        ClientTickEvents.END_CLIENT_TICK.register(ObserverNativeScreenClient::tick);
    }

    static boolean isStructuredTargetScreen(Screen screen) {
        if (screen instanceof AbstractFurnaceScreen<?>
                && ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_FURNACE)) {
            return true;
        }
        return ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS)
                && screen instanceof AbstractContainerScreen<?>;
    }

    static boolean hasStructuredRemoteScreen() {
        return (remoteFurnaceOpen || remoteContainerOpen) && ObserverNativeClient.observerSessionActive();
    }

    static boolean isNativeContainerMirror(Screen screen) {
        return screen instanceof NativeContainerMirrorScreen;
    }

    static boolean isNativeFurnaceMirror(Screen screen) {
        return screen instanceof NativeFurnaceMirrorScreen;
    }

    static boolean isNativeGenericMirror(Screen screen) {
        return screen instanceof NativeGenericMirrorScreen;
    }

    static boolean isNativeMirrorScreen(Screen screen) {
        return isNativeContainerMirror(screen) || isNativeFurnaceMirror(screen) || isNativeGenericMirror(screen);
    }

    static long extractedFrames() {
        return extractedFrames;
    }

    static long furnaceExtractedFrames() {
        return furnaceExtractedFrames;
    }

    static long genericExtractedFrames() {
        return genericExtractedFrames;
    }

    static long lastRemoteSequence() {
        return lastRemoteSequence;
    }

    static void applyGenericScreenState(boolean open, String screenClass, String title) {
        if (!ObserverNativeClient.observerSessionActive()) {
            clearRemoteGeneric();
            return;
        }
        if (!open) {
            clearRemoteGeneric();
            if (!remoteFurnaceOpen && !remoteContainerOpen) {
                closeNativeScreen();
            }
            return;
        }
        remoteGenericOpen = true;
        remoteGenericClass = screenClass == null ? "" : screenClass;
        remoteGenericTitle = title == null ? "" : title;
        genericScreenTicks = 0;
    }

    private static void tick(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) {
            targetContainerOpen = false;
            targetFurnaceOpen = false;
            lastSnapshotNanos = 0L;
        } else {
            tickTarget(minecraft);
        }

        if (!ObserverNativeClient.observerSessionActive()) {
            if (remoteFurnaceOpen || remoteContainerOpen || remoteGenericOpen || isNativeMirrorScreen(minecraft.gui.screen())) {
                clearRemoteFurnace();
                clearRemoteContainer();
                clearRemoteGeneric();
                closeNativeScreen();
            }
            return;
        }

        if (remoteFurnaceOpen) {
            ensureFurnaceScreen();
        } else if (remoteContainerOpen) {
            ensureContainerScreen();
        } else if (remoteGenericOpen) {
            genericScreenTicks++;
            if (genericScreenTicks >= GENERIC_SCREEN_DELAY_TICKS) {
                ensureGenericScreen();
            }
        }
    }

    private static void tickTarget(Minecraft minecraft) {
        Screen screen = minecraft.gui.screen();
        boolean supportsFurnace = ObserverNativeClient.targetSupportsScreen(
                ObserverNativeScreenPayloads.CAPABILITY_FURNACE
        );
        boolean supportsContainer = ObserverNativeClient.targetSupportsScreen(
                ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS
        );

        if (supportsFurnace
                && screen instanceof AbstractFurnaceScreen<?>
                && minecraft.player.containerMenu instanceof AbstractFurnaceMenu furnaceMenu) {
            closeTargetContainer(supportsContainer);
            tickTargetFurnace(minecraft, screen, furnaceMenu);
            return;
        }

        if (supportsContainer && screen instanceof AbstractContainerScreen<?>) {
            closeTargetFurnace(supportsFurnace);
            tickTargetContainer(minecraft, screen);
            return;
        }

        closeTargetFurnace(supportsFurnace);
        closeTargetContainer(supportsContainer);
    }

    private static void tickTargetContainer(Minecraft minecraft, Screen screen) {
        long now = System.nanoTime();
        if (targetContainerOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) {
            return;
        }
        targetContainerOpen = true;
        lastSnapshotNanos = now;
        TargetScreenSnapshot snapshot = captureTargetScreen(minecraft);
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        ClientPlayNetworking.send(new ObserverNativeScreenPayloads.ContainerState(
                ObserverNativeScreenPayloads.SCREEN_PROTOCOL_VERSION,
                ++nextTargetSequence,
                true,
                ObserverNativeScreenPayloads.FAMILY_CONTAINER_SLOTS,
                screen.getClass().getName(),
                title,
                snapshot.contentWidth(),
                snapshot.contentHeight(),
                snapshot.mouseX(),
                snapshot.mouseY(),
                snapshot.slots()
        ));
    }

    private static void tickTargetFurnace(Minecraft minecraft, Screen screen, AbstractFurnaceMenu furnaceMenu) {
        long now = System.nanoTime();
        if (targetFurnaceOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) {
            return;
        }
        targetFurnaceOpen = true;
        lastSnapshotNanos = now;
        TargetScreenSnapshot snapshot = captureTargetScreen(minecraft);
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        ClientPlayNetworking.send(new ObserverNativeScreenPayloads.FurnaceState(
                ObserverNativeScreenPayloads.FURNACE_PROTOCOL_VERSION,
                ++nextTargetSequence,
                true,
                ObserverNativeScreenPayloads.FAMILY_FURNACE,
                screen.getClass().getName(),
                title,
                snapshot.contentWidth(),
                snapshot.contentHeight(),
                snapshot.mouseX(),
                snapshot.mouseY(),
                snapshot.slots(),
                furnaceMenu.getBurnProgress(),
                furnaceMenu.getLitProgress(),
                furnaceMenu.isLit()
        ));
    }

    private static void closeTargetContainer(boolean canSend) {
        if (!targetContainerOpen) {
            return;
        }
        targetContainerOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) {
            ClientPlayNetworking.send(new ObserverNativeScreenPayloads.ContainerState(
                    ObserverNativeScreenPayloads.SCREEN_PROTOCOL_VERSION,
                    ++nextTargetSequence,
                    false,
                    ObserverNativeScreenPayloads.FAMILY_CONTAINER_SLOTS,
                    "",
                    "",
                    0,
                    0,
                    0,
                    0,
                    List.of()
            ));
        }
    }

    private static void closeTargetFurnace(boolean canSend) {
        if (!targetFurnaceOpen) {
            return;
        }
        targetFurnaceOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) {
            ClientPlayNetworking.send(new ObserverNativeScreenPayloads.FurnaceState(
                    ObserverNativeScreenPayloads.FURNACE_PROTOCOL_VERSION,
                    ++nextTargetSequence,
                    false,
                    ObserverNativeScreenPayloads.FAMILY_FURNACE,
                    "",
                    "",
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    0.0F,
                    0.0F,
                    false
            ));
        }
    }

    private static TargetScreenSnapshot captureTargetScreen(Minecraft minecraft) {
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
        return new TargetScreenSnapshot(
                contentWidth,
                contentHeight,
                guiMouseX - (guiWidth - contentWidth) / 2,
                guiMouseY - (guiHeight - contentHeight) / 2,
                List.copyOf(slots)
        );
    }

    private static void acceptRelay(ObserverNativeScreenPayloads.ContainerRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(
                        ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS
                )
                || targetId == null
                || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverNativeScreenPayloads.SCREEN_PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_CONTAINER_SLOTS.equals(payload.familyId())
                || payload.sequence() <= lastRemoteSequence) {
            return;
        }
        lastRemoteSequence = payload.sequence();
        if (!payload.open()) {
            clearRemoteContainer();
            ensureBestRemoteScreen();
            return;
        }

        clearRemoteFurnace();
        remoteContainerOpen = true;
        remoteScreenClass = payload.screenClass();
        remoteTitle = payload.title();
        remoteContentWidth = clamp(payload.contentWidth(), 64, 512);
        remoteContentHeight = clamp(payload.contentHeight(), 64, 512);
        remoteMouseX = payload.mouseX();
        remoteMouseY = payload.mouseY();
        remoteSlots = List.copyOf(payload.slots());
        ensureContainerScreen();
    }

    private static void acceptFurnaceRelay(ObserverNativeScreenPayloads.FurnaceRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_FURNACE)
                || targetId == null
                || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverNativeScreenPayloads.FURNACE_PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_FURNACE.equals(payload.familyId())
                || payload.sequence() <= lastRemoteSequence) {
            return;
        }
        lastRemoteSequence = payload.sequence();
        if (!payload.open()) {
            clearRemoteFurnace();
            ensureBestRemoteScreen();
            return;
        }

        clearRemoteContainer();
        remoteFurnaceOpen = true;
        remoteFurnaceClass = payload.screenClass();
        remoteFurnaceTitle = payload.title();
        remoteFurnaceContentWidth = clamp(payload.contentWidth(), 64, 512);
        remoteFurnaceContentHeight = clamp(payload.contentHeight(), 64, 512);
        remoteFurnaceMouseX = payload.mouseX();
        remoteFurnaceMouseY = payload.mouseY();
        remoteFurnaceSlots = List.copyOf(payload.slots());
        remoteCookProgress = clamp01(payload.cookProgress());
        remoteFuelProgress = clamp01(payload.fuelProgress());
        remoteFurnaceLit = payload.lit();
        ensureFurnaceScreen();
    }

    private static void ensureBestRemoteScreen() {
        if (remoteFurnaceOpen) {
            ensureFurnaceScreen();
        } else if (remoteContainerOpen) {
            ensureContainerScreen();
        } else if (remoteGenericOpen) {
            ensureGenericScreen();
        } else {
            closeNativeScreen();
        }
    }

    private static void ensureContainerScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteContainerOpen || remoteFurnaceOpen || !ObserverNativeClient.observerSessionActive()) {
            return;
        }
        if (!(minecraft.gui.screen() instanceof NativeContainerMirrorScreen)) {
            replaceNativeScreen(new NativeContainerMirrorScreen());
        }
    }

    private static void ensureFurnaceScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteFurnaceOpen || !ObserverNativeClient.observerSessionActive()) {
            return;
        }
        if (!(minecraft.gui.screen() instanceof NativeFurnaceMirrorScreen)) {
            replaceNativeScreen(new NativeFurnaceMirrorScreen());
        }
    }

    private static void ensureGenericScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteGenericOpen || remoteFurnaceOpen || remoteContainerOpen
                || !ObserverNativeClient.observerSessionActive()) {
            return;
        }
        if (!(minecraft.gui.screen() instanceof NativeGenericMirrorScreen)) {
            replaceNativeScreen(new NativeGenericMirrorScreen());
        }
    }

    private static void replaceNativeScreen(Screen next) {
        suppressMirrorStop = true;
        try {
            Minecraft.getInstance().setScreenAndShow(next);
        } finally {
            suppressMirrorStop = false;
        }
    }

    private static void closeNativeScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isNativeMirrorScreen(minecraft.gui.screen())) {
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

    private static void clearRemoteFurnace() {
        remoteFurnaceOpen = false;
        remoteFurnaceClass = "";
        remoteFurnaceTitle = "";
        remoteFurnaceContentWidth = 0;
        remoteFurnaceContentHeight = 0;
        remoteFurnaceMouseX = 0;
        remoteFurnaceMouseY = 0;
        remoteFurnaceSlots = List.of();
        remoteCookProgress = 0.0F;
        remoteFuelProgress = 0.0F;
        remoteFurnaceLit = false;
    }

    private static void clearRemoteGeneric() {
        remoteGenericOpen = false;
        remoteGenericClass = "";
        remoteGenericTitle = "";
        genericScreenTicks = 0;
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
            TotemVanillaTweaks.LOGGER.debug("Ignoring invalid Observer item id {}", slot.itemId());
            return ItemStack.EMPTY;
        }
    }

    private static void renderSlots(
            NativeObserverScreen screen,
            GuiGraphicsExtractor graphics,
            int left,
            int top,
            int contentWidth,
            int contentHeight,
            List<ObserverNativeScreenPayloads.SlotState> slots
    ) {
        for (ObserverNativeScreenPayloads.SlotState slot : slots) {
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
                graphics.itemDecorations(screen.minecraft.font, stack, slotX + 1, slotY + 1);
            }
        }
    }

    private static void renderCursor(
            GuiGraphicsExtractor graphics,
            int left,
            int top,
            int mouseX,
            int mouseY,
            int width,
            int height
    ) {
        int cursorX = left + mouseX;
        int cursorY = top + mouseY;
        if (cursorX >= 0 && cursorX < width && cursorY >= 0 && cursorY < height) {
            graphics.fill(cursorX - 4, cursorY, cursorX + 5, cursorY + 1, 0xFFFFFFFF);
            graphics.fill(cursorX, cursorY - 4, cursorX + 1, cursorY + 5, 0xFFFFFFFF);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private record TargetScreenSnapshot(
            int contentWidth,
            int contentHeight,
            int mouseX,
            int mouseY,
            List<ObserverNativeScreenPayloads.SlotState> slots
    ) {
    }

    private abstract static class NativeObserverScreen extends Screen {
        private NativeObserverScreen(Component title) {
            super(title);
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

    private static final class NativeContainerMirrorScreen extends NativeObserverScreen {
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
            String mode = "Protocol-native container";
            graphics.text(
                    this.minecraft.font,
                    mode,
                    left + contentWidth - this.minecraft.font.width(mode),
                    top - 14,
                    0xFF9E9E9E,
                    false
            );

            renderSlots(this, graphics, left, top, contentWidth, contentHeight, remoteSlots);
            renderCursor(graphics, left, top, remoteMouseX, remoteMouseY, width, height);
            extractedFrames++;
        }
    }

    private static final class NativeFurnaceMirrorScreen extends NativeObserverScreen {
        private NativeFurnaceMirrorScreen() {
            super(Component.literal("Observer Furnace"));
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0x90000000);

            int contentWidth = clamp(remoteFurnaceContentWidth, 64, Math.max(64, width - 24));
            int contentHeight = clamp(remoteFurnaceContentHeight, 64, Math.max(64, height - 24));
            int left = (width - contentWidth) / 2;
            int top = (height - contentHeight) / 2;
            graphics.fill(left - 7, top - 18, left + contentWidth + 7, top + contentHeight + 7, 0xEE202020);
            graphics.fill(left - 5, top - 16, left + contentWidth + 5, top - 3, 0xFF303030);

            String title = remoteFurnaceTitle.isBlank() ? remoteFurnaceClass : remoteFurnaceTitle;
            graphics.text(this.minecraft.font, title, left, top - 14, 0xFFFFFFFF, true);
            String mode = "Protocol-native furnace";
            graphics.text(
                    this.minecraft.font,
                    mode,
                    left + contentWidth - this.minecraft.font.width(mode),
                    top - 14,
                    0xFF9E9E9E,
                    false
            );

            renderSlots(this, graphics, left, top, contentWidth, contentHeight, remoteFurnaceSlots);

            int progressX = left + Math.min(contentWidth - 38, 79);
            int progressY = top + 34;
            int cookWidth = Math.round(24.0F * remoteCookProgress);
            graphics.fill(progressX, progressY, progressX + 24, progressY + 6, 0xFF3A3A3A);
            if (cookWidth > 0) {
                graphics.fill(progressX, progressY, progressX + cookWidth, progressY + 6, 0xFFE0A040);
            }

            int fuelX = left + Math.min(contentWidth - 58, 56);
            int fuelBottom = top + 53;
            int fuelHeight = Math.round(14.0F * remoteFuelProgress);
            graphics.fill(fuelX, fuelBottom - 14, fuelX + 6, fuelBottom, 0xFF3A3A3A);
            if (fuelHeight > 0) {
                graphics.fill(fuelX, fuelBottom - fuelHeight, fuelX + 6, fuelBottom, 0xFFFF8A3D);
            }

            String cookText = "Cook " + Math.round(remoteCookProgress * 100.0F) + "%";
            String fuelText = (remoteFurnaceLit ? "Lit " : "Fuel ") + Math.round(remoteFuelProgress * 100.0F) + "%";
            graphics.text(this.minecraft.font, cookText, left + 8, top + contentHeight - 25, 0xFFCFCFCF, false);
            graphics.text(this.minecraft.font, fuelText, left + 8, top + contentHeight - 13, 0xFFCFCFCF, false);

            renderCursor(
                    graphics,
                    left,
                    top,
                    remoteFurnaceMouseX,
                    remoteFurnaceMouseY,
                    width,
                    height
            );
            furnaceExtractedFrames++;
        }
    }

    private static final class NativeGenericMirrorScreen extends NativeObserverScreen {
        private NativeGenericMirrorScreen() {
            super(Component.literal("Observer Remote Screen"));
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xD0101010);
            int panelWidth = Math.min(360, Math.max(220, width - 80));
            int panelHeight = 92;
            int left = (width - panelWidth) / 2;
            int top = (height - panelHeight) / 2;
            graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xEE242424);
            graphics.fill(left, top, left + panelWidth, top + 2, 0xFF808080);

            String title = remoteGenericTitle.isBlank() ? "Remote screen" : remoteGenericTitle;
            graphics.text(this.minecraft.font, title, left + 10, top + 10, 0xFFFFFFFF, true);
            graphics.text(this.minecraft.font, remoteGenericClass, left + 10, top + 28, 0xFFB0B0B0, false);
            graphics.text(
                    this.minecraft.font,
                    "No framebuffer transmitted; semantic adapter pending.",
                    left + 10,
                    top + 50,
                    0xFF80CBC4,
                    false
            );
            graphics.text(
                    this.minecraft.font,
                    "Press Esc to stop observing.",
                    left + 10,
                    top + 66,
                    0xFF9E9E9E,
                    false
            );
            genericExtractedFrames++;
        }
    }
}
