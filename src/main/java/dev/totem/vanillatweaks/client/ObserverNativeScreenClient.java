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
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * Protocol-native screen transport. Negotiated semantic families use structured snapshots;
 * unsupported screens use metadata-only local placeholders. Neither path receives pixels.
 */
public final class ObserverNativeScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static final int DEFAULT_CONTENT_WIDTH = 176;
    private static final int DEFAULT_CONTENT_HEIGHT = 166;
    private static final int GENERIC_SCREEN_DELAY_TICKS = 3;
    private static final int FURNACE_HEADER_GAP = 8;
    private static final String FURNACE_MODE_LABEL = "Protocol-native furnace";
    private static final String REMNANT_BACKPACK_SCREEN_CLASS =
            "dev.totem.remnant.client.screen.BackpackScreen";

    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetContainerOpen;
    private static boolean targetFurnaceOpen;

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

    private static boolean suppressObserverScreenStop;
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

    static boolean hasStructuredRemoteScreen() {
        return (remoteFurnaceOpen || remoteContainerOpen) && ObserverNativeClient.observerSessionActive();
    }

    static boolean isNativeContainerObserverScreen(Screen screen) {
        return screen instanceof NativeContainerObserverScreen;
    }

    static boolean isNativeFurnaceObserverScreen(Screen screen) {
        return screen instanceof NativeFurnaceObserverScreen;
    }

    static boolean isNativeGenericObserverScreen(Screen screen) {
        return screen instanceof ObserverMetadataScreen;
    }

    static boolean isNativeObserverScreen(Screen screen) {
        return isNativeContainerObserverScreen(screen) || isNativeFurnaceObserverScreen(screen) || isNativeGenericObserverScreen(screen);
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

    /**
     * Fits the primary furnace title and secondary mode label onto one vanilla-style header row.
     * Both labels receive space on narrow panels; unused space is handed to the longer label.
     */
    static FurnaceHeaderLayout furnaceHeaderLayout(
            String title,
            int contentWidth,
            ToIntFunction<String> widthOf
    ) {
        String safeTitle = title == null ? "" : title;
        int availableWidth = Math.max(0, contentWidth);
        int titleWidth = widthOf.applyAsInt(safeTitle);
        int modeWidth = widthOf.applyAsInt(FURNACE_MODE_LABEL);

        if (titleWidth + FURNACE_HEADER_GAP + modeWidth <= availableWidth) {
            return new FurnaceHeaderLayout(safeTitle, titleWidth, FURNACE_MODE_LABEL,
                    availableWidth - modeWidth, modeWidth, FURNACE_HEADER_GAP, availableWidth);
        }

        int ellipsisWidth = widthOf.applyAsInt("…");
        int gap = availableWidth >= ellipsisWidth * 2 + FURNACE_HEADER_GAP
                ? FURNACE_HEADER_GAP
                : 0;
        int textWidth = Math.max(0, availableWidth - gap);
        int titleBudget = textWidth / 2;
        int modeBudget = textWidth - titleBudget;
        if (titleWidth < titleBudget) {
            modeBudget += titleBudget - titleWidth;
            titleBudget = titleWidth;
        } else if (modeWidth < modeBudget) {
            titleBudget += modeBudget - modeWidth;
            modeBudget = modeWidth;
        }

        String fittedTitle = fitHeaderText(safeTitle, titleBudget, widthOf);
        String fittedMode = fitHeaderText(FURNACE_MODE_LABEL, modeBudget, widthOf);
        int fittedTitleWidth = widthOf.applyAsInt(fittedTitle);
        int fittedModeWidth = widthOf.applyAsInt(fittedMode);
        int modeX = availableWidth - fittedModeWidth;
        return new FurnaceHeaderLayout(fittedTitle, fittedTitleWidth, fittedMode,
                modeX, fittedModeWidth, gap, availableWidth);
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

    static record FurnaceHeaderLayout(
            String title,
            int titleWidth,
            String mode,
            int modeX,
            int modeWidth,
            int gap,
            int availableWidth
    ) {
        boolean fits() {
            return titleWidth <= modeX - gap && modeX >= 0 && modeX + modeWidth <= availableWidth;
        }
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
            if (remoteFurnaceOpen || remoteContainerOpen || remoteGenericOpen || isNativeObserverScreen(minecraft.gui.screen())) {
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
        boolean supportsRemnantBackpack = ObserverNativeClient.targetSupportsScreen(
                ObserverNativeScreenPayloads.CAPABILITY_REMNANT_BACKPACK
        );

        if (supportsRemnantBackpack && screen != null
                && REMNANT_BACKPACK_SCREEN_CLASS.equals(screen.getClass().getName())) {
            closeTargetFurnace(supportsFurnace);
            closeTargetContainer(supportsContainer);
            return;
        }

        if (supportsFurnace && screen instanceof AbstractFurnaceScreen<?> furnaceScreen) {
            closeTargetContainer(supportsContainer);
            tickTargetFurnace(minecraft, furnaceScreen);
            return;
        }

        if (supportsContainer && screen instanceof AbstractContainerScreen<?> containerScreen
                && isExactGenericContainerScreen(screen)) {
            closeTargetFurnace(supportsFurnace);
            tickTargetContainer(minecraft, containerScreen);
            return;
        }

        closeTargetFurnace(supportsFurnace);
        closeTargetContainer(supportsContainer);
    }

    private static boolean isExactGenericContainerScreen(Screen screen) {
        return screen != null && switch (screen.getClass().getName()) {
            case "net.minecraft.client.gui.screens.inventory.ContainerScreen",
                    "net.minecraft.client.gui.screens.inventory.HopperScreen",
                    "net.minecraft.client.gui.screens.inventory.DispenserScreen",
                    "net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen" -> true;
            default -> false;
        };
    }

    private static void tickTargetContainer(
            Minecraft minecraft,
            AbstractContainerScreen<?> screen
    ) {
        long now = System.nanoTime();
        if (targetContainerOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) {
            return;
        }
        targetContainerOpen = true;
        lastSnapshotNanos = now;
        ClientPlayNetworking.send(captureContainerState(minecraft, screen, ++nextTargetSequence));
    }

    static ObserverNativeScreenPayloads.ContainerState captureContainerState(
            Minecraft minecraft,
            AbstractContainerScreen<?> screen,
            long sequence
    ) {
        TargetScreenSnapshot snapshot = captureTargetScreen(minecraft, screen.getMenu());
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        return new ObserverNativeScreenPayloads.ContainerState(
                ObserverNativeScreenPayloads.SCREEN_PROTOCOL_VERSION,
                sequence,
                true,
                ObserverNativeScreenPayloads.FAMILY_CONTAINER_SLOTS,
                screen.getClass().getName(),
                title,
                snapshot.contentWidth(),
                snapshot.contentHeight(),
                snapshot.mouseX(),
                snapshot.mouseY(),
                snapshot.slots()
        );
    }

    private static void tickTargetFurnace(Minecraft minecraft, AbstractFurnaceScreen<?> screen) {
        long now = System.nanoTime();
        if (targetFurnaceOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) {
            return;
        }
        targetFurnaceOpen = true;
        lastSnapshotNanos = now;
        ClientPlayNetworking.send(captureFurnaceState(minecraft, screen, ++nextTargetSequence));
    }

    static ObserverNativeScreenPayloads.FurnaceState captureFurnaceState(
            Minecraft minecraft,
            AbstractFurnaceScreen<?> screen,
            long sequence
    ) {
        AbstractFurnaceMenu furnaceMenu = screen.getMenu();
        TargetScreenSnapshot snapshot = captureTargetScreen(minecraft, furnaceMenu);
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        return new ObserverNativeScreenPayloads.FurnaceState(
                ObserverNativeScreenPayloads.FURNACE_PROTOCOL_VERSION,
                sequence,
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
        );
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

    private static TargetScreenSnapshot captureTargetScreen(
            Minecraft minecraft,
            AbstractContainerMenu menu
    ) {
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>();
        int maxX = 0;
        int maxY = 0;
        int slotLimit = Math.min(
                menu.slots.size(),
                ObserverNativeScreenPayloads.MAX_SLOTS
        );
        for (int i = 0; i < slotLimit; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            String itemId = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            int count = stack.isEmpty() ? 0 : stack.getCount();
            int damage = stack.isEmpty() ? 0 : stack.getDamageValue();
            slots.add(new ObserverNativeScreenPayloads.SlotState(
                    i,
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
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverNativeScreenPayloads.FAMILY_CONTAINER_SLOTS,
                        payload.targetId(), payload.sequence())) {
            return;
        }
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
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverNativeScreenPayloads.FAMILY_FURNACE,
                        payload.targetId(), payload.sequence())) {
            return;
        }
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
        if (!(minecraft.gui.screen() instanceof NativeContainerObserverScreen)) replaceNativeScreen(createContainerScreen());
        if (minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen) {
            ObserverVanillaScreenSupport.applyMenu(screen.getMenu(), remoteSlots);
        }
    }

    private static void ensureFurnaceScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteFurnaceOpen || !ObserverNativeClient.observerSessionActive()) {
            return;
        }
        if (!(minecraft.gui.screen() instanceof NativeFurnaceObserverScreen)
                || !minecraft.gui.screen().getClass().getName().contains(expectedFurnaceScreenName())) {
            replaceNativeScreen(createFurnaceScreen());
        }
        if (minecraft.gui.screen() instanceof AbstractFurnaceScreen<?> screen) {
            ObserverVanillaScreenSupport.applyMenu(screen.getMenu(), remoteFurnaceSlots);
            int fuelTotal = 100;
            screen.getMenu().setData(0, Math.round(remoteFuelProgress * fuelTotal));
            screen.getMenu().setData(1, fuelTotal);
            screen.getMenu().setData(2, Math.round(remoteCookProgress * 100));
            screen.getMenu().setData(3, 100);
        }
    }

    private static void ensureGenericScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteGenericOpen || remoteFurnaceOpen || remoteContainerOpen
                || !ObserverNativeClient.observerSessionActive()) {
            return;
        }
        if (!(minecraft.gui.screen() instanceof ObserverMetadataScreen)) {
            replaceNativeScreen(new ObserverMetadataScreen());
        }
    }

    private static void replaceNativeScreen(Screen next) {
        suppressObserverScreenStop = true;
        try {
            Minecraft.getInstance().setScreenAndShow(next);
        } finally {
            suppressObserverScreenStop = false;
        }
    }

    private static void closeNativeScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isNativeObserverScreen(minecraft.gui.screen())) {
            return;
        }
        suppressObserverScreenStop = true;
        try {
            minecraft.setScreenAndShow(null);
        } finally {
            suppressObserverScreenStop = false;
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

    static ItemStack itemStack(ObserverNativeScreenPayloads.SlotState slot) {
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

    private static Screen createContainerScreen() {
        Inventory inventory = ObserverVanillaScreenSupport.detachedInventory();
        Component title = Component.literal(remoteTitle.isBlank() ? "Container" : remoteTitle);
        return switch (remoteScreenClass) {
            case "net.minecraft.client.gui.screens.inventory.HopperScreen" ->
                    new ObserverHopperScreen(new HopperMenu(-1, inventory, new SimpleContainer(5)), inventory, title);
            case "net.minecraft.client.gui.screens.inventory.DispenserScreen" ->
                    new ObserverDispenserScreen(new DispenserMenu(-1, inventory, new SimpleContainer(9)), inventory, title);
            case "net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen" ->
                    new ObserverShulkerScreen(new ShulkerBoxMenu(-1, inventory, new SimpleContainer(27)), inventory, title);
            default -> {
                int containerSlots = Math.max(9, remoteSlots.size() - 36);
                int rows = Math.clamp((containerSlots + 8) / 9, 1, 6);
                yield new ObserverContainerScreen(new ChestMenu(switch (rows) {
                    case 1 -> net.minecraft.world.inventory.MenuType.GENERIC_9x1;
                    case 2 -> net.minecraft.world.inventory.MenuType.GENERIC_9x2;
                    case 3 -> net.minecraft.world.inventory.MenuType.GENERIC_9x3;
                    case 4 -> net.minecraft.world.inventory.MenuType.GENERIC_9x4;
                    case 5 -> net.minecraft.world.inventory.MenuType.GENERIC_9x5;
                    default -> net.minecraft.world.inventory.MenuType.GENERIC_9x6;
                }, -1, inventory, new SimpleContainer(rows * 9), rows), inventory, title);
            }
        };
    }

    private static String expectedFurnaceScreenName() {
        if (remoteFurnaceClass.endsWith("BlastFurnaceScreen")) return "ObserverBlastFurnaceScreen";
        if (remoteFurnaceClass.endsWith("SmokerScreen")) return "ObserverSmokerScreen";
        return "ObserverFurnaceScreen";
    }

    private static Screen createFurnaceScreen() {
        Inventory inventory = ObserverVanillaScreenSupport.detachedInventory();
        Component title = Component.literal(remoteFurnaceTitle.isBlank() ? "Furnace" : remoteFurnaceTitle);
        if (remoteFurnaceClass.endsWith("BlastFurnaceScreen"))
            return new ObserverBlastFurnaceScreen(new BlastFurnaceMenu(-1, inventory), inventory, title);
        if (remoteFurnaceClass.endsWith("SmokerScreen"))
            return new ObserverSmokerScreen(new SmokerMenu(-1, inventory), inventory, title);
        return new ObserverFurnaceScreen(new FurnaceMenu(-1, inventory), inventory, title);
    }

    private interface NativeContainerObserverScreen { }
    private interface NativeFurnaceObserverScreen { }

    private static void closeObserverScreen() {
        if (!suppressObserverScreenStop) ObserverVanillaScreenSupport.stopObserving();
    }

    private static final class ObserverContainerScreen extends ContainerScreen
            implements dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen, NativeContainerObserverScreen {
        private ObserverContainerScreen(ChestMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void onClose() { closeObserverScreen(); }
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){super.extractRenderState(g,x,y,tick);extractedFrames++;}
    }
    private static final class ObserverHopperScreen extends HopperScreen
            implements dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen, NativeContainerObserverScreen {
        private ObserverHopperScreen(HopperMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void onClose() { closeObserverScreen(); }
    }
    private static final class ObserverDispenserScreen extends DispenserScreen
            implements dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen, NativeContainerObserverScreen {
        private ObserverDispenserScreen(DispenserMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void onClose() { closeObserverScreen(); }
    }
    private static final class ObserverShulkerScreen extends ShulkerBoxScreen
            implements dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen, NativeContainerObserverScreen {
        private ObserverShulkerScreen(ShulkerBoxMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void onClose() { closeObserverScreen(); }
    }
    private static final class ObserverFurnaceScreen extends FurnaceScreen
            implements dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen, NativeFurnaceObserverScreen {
        private ObserverFurnaceScreen(FurnaceMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void onClose() { closeObserverScreen(); }
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){super.extractRenderState(g,x,y,tick);furnaceExtractedFrames++;}
    }
    private static final class ObserverBlastFurnaceScreen extends BlastFurnaceScreen
            implements dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen, NativeFurnaceObserverScreen {
        private ObserverBlastFurnaceScreen(BlastFurnaceMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void onClose() { closeObserverScreen(); }
    }
    private static final class ObserverSmokerScreen extends SmokerScreen
            implements dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen, NativeFurnaceObserverScreen {
        private ObserverSmokerScreen(SmokerMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void onClose() { closeObserverScreen(); }
    }

    /** Explicit metadata-only unsupported state; it is not a semantic lookalike. */
    private static final class ObserverMetadataScreen extends Screen
            implements dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen {
        private ObserverMetadataScreen() {
            super(Component.literal("Observer Remote Screen"));
        }

        @Override public boolean totem$isObserverReadOnly() { return true; }

        @Override public void onClose() { closeObserverScreen(); }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xD0101010);
            int panelWidth = Math.min(360, Math.max(220, width - 80));
            ObserverMetadataScreenPolicy.Presentation presentation =
                    ObserverMetadataScreenPolicy.classify(remoteGenericClass);
            int panelHeight = presentation.detailLine().isBlank() ? 108 : 124;
            int left = (width - panelWidth) / 2;
            int top = (height - panelHeight) / 2;
            graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xEE242424);
            graphics.fill(left, top, left + panelWidth, top + 2, 0xFF808080);

            String title = remoteGenericTitle.isBlank() ? "Remote screen" : remoteGenericTitle;
            graphics.text(this.minecraft.font, title, left + 10, top + 10, 0xFFFFFFFF, true);
            graphics.text(this.minecraft.font, remoteGenericClass, left + 10, top + 28, 0xFFB0B0B0, false);
            graphics.text(
                    this.minecraft.font,
                    "No framebuffer transmitted.",
                    left + 10,
                    top + 50,
                    0xFF80CBC4,
                    false
            );
            graphics.text(
                    this.minecraft.font,
                    presentation.statusLine(),
                    left + 10,
                    top + 66,
                    0xFF80CBC4,
                    false
            );
            int instructionY = top + 82;
            if (!presentation.detailLine().isBlank()) {
                graphics.text(
                        this.minecraft.font,
                        presentation.detailLine(),
                        left + 10,
                        top + 82,
                        0xFFB0B0B0,
                        false
                );
                instructionY = top + 98;
            }
            graphics.text(
                    this.minecraft.font,
                    "Press Esc to stop observing.",
                    left + 10,
                    instructionY,
                    0xFF9E9E9E,
                    false
            );
            genericExtractedFrames++;
        }
    }
}
