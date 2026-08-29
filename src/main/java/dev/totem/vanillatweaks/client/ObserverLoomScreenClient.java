package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.mixin.client.LoomScreenAccessor;
import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import dev.totem.vanillatweaks.network.ObserverLoomScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Semantic adapter and local reconstruction for the vanilla Loom screen. */
public final class ObserverLoomScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    static final int PATTERN_COLUMNS = 4;
    static final int PATTERN_ROWS = 4;
    static final int PATTERN_CELL_SIZE = 14;
    static final int PATTERN_GRID_X = 60;
    static final int PATTERN_GRID_Y = 13;
    static final int PATTERN_GRID_BOTTOM = PATTERN_GRID_Y + PATTERN_ROWS * PATTERN_CELL_SIZE;
    static final int INVENTORY_SLOT_BORDER_TOP = 83;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static int remoteSelectedPatternIndex = -1;
    private static int remoteStartRow;
    private static float remoteScrollOffset;
    private static boolean remoteDisplayPatterns;
    private static boolean remoteHasMaxPatterns;
    private static boolean remoteResultAvailable;
    private static int remoteResultBaseColorId = -1;
    private static List<ObserverLoomScreenPayloads.PatternState> remotePatterns = List.of();
    private static List<ObserverLoomScreenPayloads.BannerLayerState> remoteResultLayers = List.of();
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();
    private static boolean suppressObserverScreenStop;
    private static long extractedFrames;

    private ObserverLoomScreenClient() {}

    /** Matches vanilla's bounded four-by-four Loom viewport. */
    static LoomPatternViewport loomPatternViewport(int startRow, int patternCount) {
        int total = Math.max(0, patternCount);
        long requestedFirst = (long) Math.max(0, startRow) * PATTERN_COLUMNS;
        int first = (int) Math.min(total, requestedFirst);
        int lastExclusive = Math.min(total, first + PATTERN_COLUMNS * PATTERN_ROWS);
        return new LoomPatternViewport(first, lastExclusive);
    }

    static record LoomPatternViewport(int first, int lastExclusive) {
        int visibleCount() { return lastExclusive - first; }
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ObserverLoomScreenPayloads.LoomRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverLoomScreenClient::tick);
    }

    public static boolean isLoomScreen(Screen screen) {
        return screen instanceof LoomScreen;
    }

    private static void tick(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) closeTarget(false);
        else tickTarget(minecraft);
        if (!ObserverNativeClient.observerSessionActive()) { clearRemote(); closeObserverScreen(); }
        else if (remoteOpen) ensureObserverScreen();
    }

    private static void tickTarget(Minecraft minecraft) {
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverLoomScreenPayloads.CAPABILITY);
        Screen current = minecraft.gui.screen();
        if (!supported || !(current instanceof LoomScreen screen)) {
            closeTarget(supported);
            return;
        }
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        targetOpen = true;
        lastSnapshotNanos = now;

        ClientPlayNetworking.send(captureTargetState(screen, ++nextTargetSequence));
    }

    static ObserverLoomScreenPayloads.LoomState captureTargetState(LoomScreen screen, long sequence) {
        LoomMenu menu = screen.getMenu();
        LoomScreenAccessor accessor = (LoomScreenAccessor) screen;
        List<ObserverLoomScreenPayloads.PatternState> patternStates = menu.getSelectablePatterns().stream()
                .limit(ObserverLoomScreenPayloads.MAX_PATTERNS)
                .map(ObserverLoomScreenClient::patternState)
                .toList();
        ItemStack resultStack = menu.getResultSlot().getItem();
        boolean resultAvailable = !resultStack.isEmpty();
        int resultBaseColorId = resultStack.getItem() instanceof BannerItem bannerItem
                ? bannerItem.getColor().getId() : -1;
        List<ObserverLoomScreenPayloads.BannerLayerState> resultLayers = resultStack
                .getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)
                .layers().stream().limit(ObserverLoomScreenPayloads.MAX_BANNER_LAYERS)
                .map(layer -> new ObserverLoomScreenPayloads.BannerLayerState(
                        layer.pattern().value().assetId().toString(), layer.color().getId()))
                .toList();
        return new ObserverLoomScreenPayloads.LoomState(
                ObserverLoomScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverLoomScreenPayloads.FAMILY_ID, screen.getClass().getName(),
                screen.getTitle() == null ? "" : screen.getTitle().getString(),
                menu.getSelectedBannerPatternIndex(), accessor.totem$getStartRow(), accessor.totem$getScrollOffs(),
                accessor.totem$getDisplayPatterns(), accessor.totem$getHasMaxPatterns(), resultAvailable,
                resultBaseColorId, patternStates, resultLayers, captureSlots(menu));
    }

    private static ObserverLoomScreenPayloads.PatternState patternState(Holder<BannerPattern> holder) {
        String registryId = holder.unwrapKey().map(key -> key.identifier().toString()).orElse("");
        return new ObserverLoomScreenPayloads.PatternState(registryId, holder.value().assetId().toString());
    }

    private static void closeTarget(boolean canSend) {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) ClientPlayNetworking.send(ObserverLoomScreenPayloads.closed(++nextTargetSequence));
    }

    private static List<ObserverNativeScreenPayloads.SlotState> captureSlots(LoomMenu menu) {
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

    private static void acceptRelay(ObserverLoomScreenPayloads.LoomRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverLoomScreenPayloads.CAPABILITY)
                || targetId == null || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverLoomScreenPayloads.PROTOCOL_VERSION
                || !ObserverLoomScreenPayloads.FAMILY_ID.equals(payload.familyId())
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverLoomScreenPayloads.FAMILY_ID,
                        payload.targetId(), payload.sequence())) return;
        if (!payload.open()) { clearRemote(); closeObserverScreen(); return; }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = payload.title();
        remoteSelectedPatternIndex = payload.selectedPatternIndex();
        remoteStartRow = payload.startRow();
        remoteScrollOffset = payload.scrollOffset();
        remoteDisplayPatterns = payload.displayPatterns();
        remoteHasMaxPatterns = payload.hasMaxPatterns();
        remoteResultAvailable = payload.resultAvailable();
        remoteResultBaseColorId = payload.resultBaseColorId();
        remotePatterns = List.copyOf(payload.patterns());
        remoteResultLayers = List.copyOf(payload.resultLayers());
        remoteSlots = List.copyOf(payload.slots());
        ensureObserverScreen();
    }

    private static void ensureObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof ObserverLoomScreen)) {
            suppressObserverScreenStop = true;
            try {
                var inventory = ObserverVanillaScreenSupport.detachedInventory();
                minecraft.setScreenAndShow(new ObserverLoomScreen(new LoomMenu(-1, inventory), inventory,
                        Component.literal(remoteTitle.isBlank() ? "Loom" : remoteTitle)));
            }
            finally { suppressObserverScreenStop = false; }
        }
        if (minecraft.gui.screen() instanceof ObserverLoomScreen screen) {
            LoomMenu menu = screen.getMenu();
            ObserverVanillaScreenSupport.applyMenu(menu, remoteSlots);
            menu.slotsChanged(menu.getBannerSlot().container);
            if (remoteSelectedPatternIndex >= 0
                    && remoteSelectedPatternIndex < menu.getSelectablePatterns().size()) {
                menu.clickMenuButton(minecraft.player, remoteSelectedPatternIndex);
            }
            ItemStack result = menu.getResultSlot().getItem();
            if (!result.isEmpty()) result.set(DataComponents.BANNER_PATTERNS, bannerLayers(remoteResultLayers));
            LoomScreenAccessor accessor = (LoomScreenAccessor) (Object) screen;
            accessor.totem$setScrollOffs(remoteScrollOffset);
            accessor.totem$setStartRow(remoteStartRow);
            accessor.totem$setDisplayPatterns(remoteDisplayPatterns);
            accessor.totem$setHasMaxPatterns(remoteHasMaxPatterns);
        }
    }

    private static void closeObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ObserverLoomScreen)) return;
        suppressObserverScreenStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressObserverScreenStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
        remoteSelectedPatternIndex = -1;
        remoteStartRow = 0;
        remoteScrollOffset = 0.0F;
        remoteDisplayPatterns = false;
        remoteHasMaxPatterns = false;
        remoteResultAvailable = false;
        remoteResultBaseColorId = -1;
        remotePatterns = List.of();
        remoteResultLayers = List.of();
        remoteSlots = List.of();
    }

    private static final class ObserverLoomScreen extends LoomScreen implements ObserverReadOnlyScreen {
        private ObserverLoomScreen(LoomMenu menu, net.minecraft.world.entity.player.Inventory inventory,
                                   Component title) { super(menu, inventory, title); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void onClose() {
            if (!suppressObserverScreenStop) ObserverVanillaScreenSupport.stopObserving();
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            extractedFrames++;
        }
    }

    private static BannerPatternLayers bannerLayers(List<ObserverLoomScreenPayloads.BannerLayerState> layers) {
        List<BannerPatternLayers.Layer> resolved = new ArrayList<>();
        for (ObserverLoomScreenPayloads.BannerLayerState layer : layers) {
            Holder<BannerPattern> holder = patternHolder(layer.assetId());
            if (holder != null) resolved.add(new BannerPatternLayers.Layer(holder, DyeColor.byId(layer.dyeColorId())));
        }
        return new BannerPatternLayers(List.copyOf(resolved));
    }

    private static Holder<BannerPattern> patternHolder(String assetId) {
        try { return Holder.direct(new BannerPattern(Identifier.parse(assetId), "")); }
        catch (RuntimeException error) { return null; }
    }
}
