package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.vanillatweaks.network.ObserverBrewingScreenPayloads;
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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** Semantic adapter and local reconstruction for the vanilla Brewing Stand screen. */
public final class ObserverBrewingScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static final int HEADER_GAP = 8;
    static final int HEADER_TEXT_Y = 6;
    static final int HEADER_TEXT_HEIGHT = 9;
    static final int BREWING_SLOT_BORDER_TOP = 16;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static long lastRemoteSequence = -1L;
    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static int remoteBrewingTicks;
    private static int remoteFuel;
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();
    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverBrewingScreenClient() {}

    /** Keeps the brewing title and fuel state on one collision-free vanilla-style header row. */
    static BrewingHeaderLayout brewingHeaderLayout(
            String title,
            int fuel,
            int contentWidth,
            ToIntFunction<String> widthOf
    ) {
        String safeTitle = title == null ? "" : title;
        int safeFuel = clamp(fuel, 0, ObserverBrewingScreenPayloads.MAX_FUEL);
        String fullFuel = "Fuel " + safeFuel + "/" + ObserverBrewingScreenPayloads.MAX_FUEL;
        String compactFuel = safeFuel + "/" + ObserverBrewingScreenPayloads.MAX_FUEL;
        int availableWidth = Math.max(0, contentWidth);
        int ellipsisWidth = widthOf.applyAsInt("…");
        int minimumTitleWidth = safeTitle.isEmpty() ? 0 : ellipsisWidth;

        String fuelLabel = fullFuel;
        if (widthOf.applyAsInt(fullFuel) + minimumTitleWidth
                + (safeTitle.isEmpty() ? 0 : HEADER_GAP) > availableWidth) {
            fuelLabel = compactFuel;
        }

        int minimumFuelWidth = fuelLabel.isEmpty() ? 0 : ellipsisWidth;
        int gap = !safeTitle.isEmpty() && !fuelLabel.isEmpty()
                && availableWidth >= minimumTitleWidth + HEADER_GAP + minimumFuelWidth
                ? HEADER_GAP
                : 0;
        int fuelBudget = Math.max(0, availableWidth - minimumTitleWidth - gap);
        String fittedFuel = fitHeaderText(fuelLabel, fuelBudget, widthOf);
        int fuelWidth = widthOf.applyAsInt(fittedFuel);
        int titleBudget = Math.max(0, availableWidth - fuelWidth - gap);
        String fittedTitle = fitHeaderText(safeTitle, titleBudget, widthOf);
        int titleWidth = widthOf.applyAsInt(fittedTitle);
        int actualGap = fittedTitle.isEmpty() || fittedFuel.isEmpty() ? 0 : gap;
        int fuelX = availableWidth - fuelWidth;
        return new BrewingHeaderLayout(fittedTitle, titleWidth, fittedFuel,
                fuelX, fuelWidth, actualGap, availableWidth);
    }

    private static String fitHeaderText(String text, int maxWidth, ToIntFunction<String> widthOf) {
        if (text.isEmpty() || maxWidth <= 0) {
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

    static record BrewingHeaderLayout(
            String title,
            int titleWidth,
            String fuel,
            int fuelX,
            int fuelWidth,
            int gap,
            int availableWidth
    ) {
        boolean fits() {
            return titleWidth <= fuelX - gap
                    && fuelX >= 0
                    && fuelX + fuelWidth <= availableWidth;
        }
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ObserverBrewingScreenPayloads.BrewingRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverBrewingScreenClient::tick);
    }

    public static boolean isBrewingScreen(Screen screen) {
        return screen != null && ObserverBrewingScreenPayloads.SCREEN_CLASS.equals(screen.getClass().getName());
    }

    private static void tick(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) {
            closeTarget(false);
        } else {
            tickTarget(minecraft);
        }
        if (!ObserverNativeClient.observerSessionActive()) {
            clearRemote();
            closeMirror();
        } else if (remoteOpen) {
            ensureMirror();
        }
    }

    private static void tickTarget(Minecraft minecraft) {
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverBrewingScreenPayloads.CAPABILITY);
        Screen screen = minecraft.gui.screen();
        if (!supported || !isBrewingScreen(screen) || !(screen instanceof AbstractContainerScreen<?> container)) {
            closeTarget(supported);
            return;
        }
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        targetOpen = true;
        lastSnapshotNanos = now;

        AbstractContainerMenu menu = container.getMenu();
        int brewingTicks = invokeInt(menu, "getBrewingTicks", 0);
        int fuel = invokeInt(menu, "getFuel", 0);
        ClientPlayNetworking.send(new ObserverBrewingScreenPayloads.BrewingState(
                ObserverBrewingScreenPayloads.PROTOCOL_VERSION,
                ++nextTargetSequence,
                true,
                ObserverBrewingScreenPayloads.FAMILY_ID,
                screen.getClass().getName(),
                screen.getTitle() == null ? "" : screen.getTitle().getString(),
                clamp(brewingTicks, 0, ObserverBrewingScreenPayloads.MAX_BREW_TICKS),
                clamp(fuel, 0, ObserverBrewingScreenPayloads.MAX_FUEL),
                captureSlots(menu)
        ));
    }

    private static void closeTarget(boolean canSend) {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) ClientPlayNetworking.send(ObserverBrewingScreenPayloads.closed(++nextTargetSequence));
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

    private static int invokeInt(Object owner, String name, int fallback) {
        try {
            Method method = owner.getClass().getMethod(name);
            Object value = method.invoke(owner);
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (ReflectiveOperationException error) {
            TotemVanillaTweaks.LOGGER.debug("Brewing semantic method {} unavailable on {}", name, owner.getClass().getName());
            return fallback;
        }
    }

    private static void acceptRelay(ObserverBrewingScreenPayloads.BrewingRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverBrewingScreenPayloads.CAPABILITY)
                || targetId == null || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverBrewingScreenPayloads.PROTOCOL_VERSION
                || !ObserverBrewingScreenPayloads.FAMILY_ID.equals(payload.familyId())
                || payload.sequence() <= lastRemoteSequence) return;
        lastRemoteSequence = payload.sequence();
        if (!payload.open()) {
            clearRemote();
            closeMirror();
            return;
        }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = payload.title();
        remoteBrewingTicks = payload.brewingTicks();
        remoteFuel = payload.fuel();
        remoteSlots = List.copyOf(payload.slots());
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeBrewingMirrorScreen)) {
            suppressMirrorStop = true;
            try { minecraft.setScreenAndShow(new NativeBrewingMirrorScreen()); }
            finally { suppressMirrorStop = false; }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeBrewingMirrorScreen)) return;
        suppressMirrorStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressMirrorStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
        remoteBrewingTicks = 0;
        remoteFuel = 0;
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
        } catch (RuntimeException error) {
            return ItemStack.EMPTY;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class NativeBrewingMirrorScreen extends Screen {
        private NativeBrewingMirrorScreen() { super(Component.literal("Observer Brewing Stand")); }
        @Override public boolean isPauseScreen() { return false; }

        @Override
        public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) {
                ClientPlayNetworking.send(new ObserverPayloads.Stop());
            }
            super.onClose();
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0x90000000);
            int panelWidth = 176;
            int panelHeight = 166;
            int left = (width - panelWidth) / 2;
            int top = (height - panelHeight) / 2;
            graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xFFE8E8E8);
            graphics.fill(left + 3, top + 3, left + panelWidth - 3, top + panelHeight - 3, 0xFFC6C6C6);
            BrewingHeaderLayout header = brewingHeaderLayout(
                    remoteTitle.isBlank() ? "Brewing Stand" : remoteTitle,
                    remoteFuel,
                    panelWidth - 16,
                    font::width
            );
            graphics.text(font, header.title(), left + 8, top + HEADER_TEXT_Y, 0xFF404040, false);
            graphics.text(font, header.fuel(), left + 8 + header.fuelX(),
                    top + HEADER_TEXT_Y, 0xFF404040, false);

            int brewDone = ObserverBrewingScreenPayloads.MAX_BREW_TICKS - remoteBrewingTicks;
            int brewWidth = clamp((brewDone * 48) / ObserverBrewingScreenPayloads.MAX_BREW_TICKS, 0, 48);
            graphics.fill(left + 63, top + 34, left + 113, top + 42, 0xFF505050);
            graphics.fill(left + 64, top + 35, left + 64 + brewWidth, top + 41, 0xFF8A5A2B);
            int fuelWidth = clamp((remoteFuel * 18) / ObserverBrewingScreenPayloads.MAX_FUEL, 0, 18);
            graphics.fill(left + 60, top + 16, left + 80, top + 22, 0xFF505050);
            graphics.fill(left + 61, top + 17, left + 61 + fuelWidth, top + 21, 0xFFE0A020);
            graphics.text(font, "Brew " + remoteBrewingTicks + "t", left + 116, top + 34, 0xFF404040, false);

            for (ObserverNativeScreenPayloads.SlotState slot : remoteSlots) {
                int sx = left + slot.x();
                int sy = top + slot.y();
                graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF666666);
                graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF202020);
                ItemStack stack = itemStack(slot);
                if (!stack.isEmpty()) {
                    graphics.item(stack, sx, sy);
                    graphics.itemDecorations(font, stack, sx, sy);
                }
            }
            extractedFrames++;
        }
    }
}
