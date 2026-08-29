package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.vanillatweaks.mixin.client.AbstractRecipeBookScreenAccessor;
import dev.totem.vanillatweaks.mixin.client.AbstractContainerScreenMenuAccessor;
import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import dev.totem.vanillatweaks.mixin.client.RecipeBookComponentAccessor;
import dev.totem.vanillatweaks.mixin.client.RecipeBookPageAccessor;
import dev.totem.vanillatweaks.network.ObserverCraftingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
    private static boolean remoteRecipeBookVisible;
    private static boolean remoteRecipeBookWidthTooNarrow;
    private static boolean remoteRecipeBookFiltering;
    private static boolean remoteRecipeBookSearchActive;
    private static String remoteSelectedRecipeBookTab = "";
    private static int remoteRecipeBookPage;
    private static int remoteRecipeBookPageCount;
    private static boolean remoteActiveEffectsVisible;
    private static List<ObserverCraftingScreenPayloads.EffectState> remoteActiveEffects = List.of();
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();

    private static boolean suppressObserverScreenStop;
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

    static boolean isNativeObserverScreen(Screen screen) {
        return screen instanceof ObserverInventoryScreen || screen instanceof ObserverCraftingTableScreen;
    }

    static boolean hasStructuredRemoteScreen() {
        return remoteOpen && ObserverNativeClient.observerSessionActive();
    }

    static long extractedFrames() {
        return extractedFrames;
    }

    /** Render hook used by vanilla EffectsInInventory; never reads the Observer player's effects. */
    public static java.util.Collection<MobEffectInstance> activeEffectsFor(
            Screen screen, java.util.Collection<MobEffectInstance> localEffects) {
        if (!(screen instanceof ObserverInventoryScreen) || !remoteOpen) return localEffects;
        if (!remoteActiveEffectsVisible) return List.of();
        List<MobEffectInstance> effects = new ArrayList<>(remoteActiveEffects.size());
        for (var state : remoteActiveEffects) {
            try {
                BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(state.effectId())).ifPresent(holder ->
                        effects.add(new MobEffectInstance(holder, state.durationTicks(), state.amplifier(),
                                state.ambient(), state.visible(), state.showIcon())));
            } catch (RuntimeException invalidId) {
                TotemVanillaTweaks.LOGGER.debug("Ignoring invalid remote effect id {}", state.effectId());
            }
        }
        return List.copyOf(effects);
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
            closeObserverScreen();
            return;
        }
        if (remoteOpen) {
            ensureObserverScreen();
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

        ObserverCraftingScreenPayloads.CraftingState state = captureTargetState(minecraft, screen,
                ++nextTargetSequence);
        if (state == null) {
            closeTarget(supported);
            return;
        }
        ClientPlayNetworking.send(state);
    }

    static ObserverCraftingScreenPayloads.CraftingState captureTargetState(
            Minecraft minecraft, Screen screen, long sequence) {
        if (!isTargetCraftingScreen(screen) || !(screen instanceof AbstractContainerScreen<?> container)) {
            return null;
        }
        TargetSnapshot snapshot = capture(minecraft, container.getMenu());
        RecipeBookSnapshot recipeBook = captureRecipeBook(screen);
        boolean inventory = screen instanceof InventoryScreen;
        String variant = inventory
                ? ObserverCraftingScreenPayloads.VARIANT_PLAYER_2X2
                : ObserverCraftingScreenPayloads.VARIANT_TABLE_3X3;
        int grid = inventory ? 2 : 3;
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        return new ObserverCraftingScreenPayloads.CraftingState(
                ObserverCraftingScreenPayloads.PROTOCOL_VERSION,
                sequence,
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
                recipeBook.visible(),
                recipeBook.widthTooNarrow(),
                recipeBook.filtering(),
                recipeBook.searchActive(),
                recipeBook.selectedTab(),
                recipeBook.page(),
                recipeBook.pageCount(),
                inventory && ((InventoryScreen) screen).showsActiveEffects(),
                inventory ? captureEffects(minecraft) : List.of(),
                snapshot.slots());
    }

    private static List<ObserverCraftingScreenPayloads.EffectState> captureEffects(Minecraft minecraft) {
        if (minecraft.player == null) return List.of();
        return minecraft.player.getActiveEffects().stream()
                .limit(ObserverCraftingScreenPayloads.MAX_EFFECTS)
                .map(effect -> new ObserverCraftingScreenPayloads.EffectState(
                        effect.getEffect().unwrapKey().map(key -> key.identifier().toString()).orElse(""),
                        effect.getAmplifier(), effect.getDuration(), effect.isAmbient(),
                        effect.isVisible(), effect.showIcon()))
                .toList();
    }

    private static RecipeBookSnapshot captureRecipeBook(Screen screen) {
        if (!(screen instanceof AbstractRecipeBookScreen<?> recipeBookScreen)) {
            return RecipeBookSnapshot.CLOSED;
        }
        AbstractRecipeBookScreenAccessor screenAccessor = (AbstractRecipeBookScreenAccessor) recipeBookScreen;
        RecipeBookComponent<?> component = screenAccessor.totem$getRecipeBookComponent();
        if (component == null) return RecipeBookSnapshot.CLOSED;
        RecipeBookComponentAccessor componentAccessor = (RecipeBookComponentAccessor) component;
        boolean visible = component.isVisible();
        boolean filtering = componentAccessor.totem$getFilterButton() != null
                && Boolean.TRUE.equals(componentAccessor.totem$getFilterButton().getValue());
        // Deliberately transmit only whether a query exists, never the user's local draft text.
        boolean searchActive = visible && componentAccessor.totem$getSearchBox() != null
                && !componentAccessor.totem$getSearchBox().getValue().isBlank();
        RecipeBookTabButton selectedTab = componentAccessor.totem$getSelectedTab();
        String selectedTabId = selectedTab == null ? "" : recipeBookTabId(selectedTab.getCategory());
        RecipeBookPage page = componentAccessor.totem$getRecipeBookPage();
        int pageIndex = 0;
        int pageCount = 0;
        if (page != null) {
            RecipeBookPageAccessor pageAccessor = (RecipeBookPageAccessor) page;
            pageIndex = Math.max(0, pageAccessor.totem$getCurrentPage());
            pageCount = Math.max(0, pageAccessor.totem$getTotalPages());
        }
        return new RecipeBookSnapshot(visible, screenAccessor.totem$getWidthTooNarrow(), filtering,
                searchActive, selectedTabId, pageIndex, pageCount);
    }

    private static String recipeBookTabId(ExtendedRecipeBookCategory category) {
        if (category instanceof SearchRecipeBookCategory search) {
            return "search:" + search.name().toLowerCase(java.util.Locale.ROOT);
        }
        if (category instanceof RecipeBookCategory recipeCategory) {
            Identifier id = BuiltInRegistries.RECIPE_BOOK_CATEGORY.getKey(recipeCategory);
            return id == null ? "" : id.toString();
        }
        return "";
    }


    private static void closeTarget(boolean canSend) {
        if (!targetOpen) {
            return;
        }
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) {
            ClientPlayNetworking.send(ObserverCraftingScreenPayloads.closed(++nextTargetSequence));
        }
    }

    private static TargetSnapshot capture(Minecraft minecraft, AbstractContainerMenu menu) {
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>();
        int maxX = 0;
        int maxY = 0;
        int limit = Math.min(menu.slots.size(), ObserverNativeScreenPayloads.MAX_SLOTS);
        for (int i = 0; i < limit; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            String itemId = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            slots.add(new ObserverNativeScreenPayloads.SlotState(
                    i,
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
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverNativeScreenPayloads.FAMILY_CRAFTING,
                        payload.targetId(), payload.sequence())) {
            return;
        }
        if (!payload.open()) {
            clearRemote();
            closeObserverScreen();
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
        remoteRecipeBookVisible = payload.recipeBookVisible();
        remoteRecipeBookWidthTooNarrow = payload.recipeBookWidthTooNarrow();
        remoteRecipeBookFiltering = payload.recipeBookFiltering();
        remoteRecipeBookSearchActive = payload.recipeBookSearchActive();
        remoteSelectedRecipeBookTab = payload.selectedRecipeBookTab();
        remoteRecipeBookPage = payload.recipeBookPage();
        remoteRecipeBookPageCount = payload.recipeBookPageCount();
        remoteActiveEffectsVisible = payload.activeEffectsVisible();
        remoteActiveEffects = List.copyOf(payload.activeEffects());
        remoteSlots = List.copyOf(payload.slots());
        ensureObserverScreen();
    }

    private static void ensureObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) {
            return;
        }
        boolean playerInventory = ObserverCraftingScreenPayloads.VARIANT_PLAYER_2X2.equals(remoteVariant);
        boolean correct = playerInventory ? minecraft.gui.screen() instanceof ObserverInventoryScreen
                : minecraft.gui.screen() instanceof ObserverCraftingTableScreen;
        if (!correct) {
            suppressObserverScreenStop = true;
            try {
                if (playerInventory) minecraft.setScreenAndShow(new ObserverInventoryScreen(
                        (net.minecraft.world.entity.player.Player) ObserverObservedPlayerIdentity.resolve(minecraft.player)));
                else {
                    var inventory = ObserverVanillaScreenSupport.detachedInventory();
                    minecraft.setScreenAndShow(new ObserverCraftingTableScreen(new CraftingMenu(-1, inventory),
                            inventory, Component.literal(remoteTitle.isBlank() ? "Crafting" : remoteTitle)));
                }
            } finally {
                suppressObserverScreenStop = false;
            }
        }
        if (minecraft.gui.screen() instanceof AbstractRecipeBookScreen<?> screen) {
            ObserverVanillaScreenSupport.applyMenu(screen.getMenu(), remoteSlots);
            applyRecipeBookState(screen);
        }
    }

    private static void closeObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ObserverInventoryScreen
                || minecraft.gui.screen() instanceof ObserverCraftingTableScreen)) {
            return;
        }
        suppressObserverScreenStop = true;
        try {
            minecraft.setScreenAndShow(null);
        } finally {
            suppressObserverScreenStop = false;
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
        remoteRecipeBookVisible = false;
        remoteRecipeBookWidthTooNarrow = false;
        remoteRecipeBookFiltering = false;
        remoteRecipeBookSearchActive = false;
        remoteSelectedRecipeBookTab = "";
        remoteRecipeBookPage = 0;
        remoteRecipeBookPageCount = 0;
        remoteActiveEffectsVisible = false;
        remoteActiveEffects = List.of();
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

    private static void applyRecipeBookState(AbstractRecipeBookScreen<?> screen) {
        AbstractRecipeBookScreenAccessor screenAccessor = (AbstractRecipeBookScreenAccessor) screen;
        screenAccessor.totem$setWidthTooNarrow(remoteRecipeBookWidthTooNarrow);
        RecipeBookComponent<?> component = screenAccessor.totem$getRecipeBookComponent();
        if (component == null) return;
        RecipeBookComponentAccessor componentAccessor = (RecipeBookComponentAccessor) component;
        componentAccessor.totem$setVisible(remoteRecipeBookVisible);
        componentAccessor.totem$setWidthTooNarrow(remoteRecipeBookWidthTooNarrow);
        if (!remoteRecipeBookVisible) {
            // Vanilla intentionally leaves RecipeBookPage uninitialized while hidden.
            // Do not force local recipe state into existence for an Observer-only mirror.
            return;
        }
        RecipeBookPage page = componentAccessor.totem$getRecipeBookPage();
        if (page != null && ((RecipeBookPageAccessor) page).totem$getMinecraft() == null) {
            // Use vanilla's production widget initialization, without toggling the local
            // player's recipe-book setting or sending a settings packet to the server.
            componentAccessor.totem$initVisuals();
        }
        if (componentAccessor.totem$getFilterButton() != null) {
            componentAccessor.totem$getFilterButton().setValue(remoteRecipeBookFiltering);
        }
        if (componentAccessor.totem$getSearchBox() != null) {
            // The wire carries only whether a query exists, never either client's draft text.
            componentAccessor.totem$getSearchBox().setValue(remoteRecipeBookSearchActive ? "…" : "");
        }
        RecipeBookTabButton selected = null;
        for (RecipeBookTabButton tab : componentAccessor.totem$getTabButtons()) {
            boolean matches = recipeBookTabId(tab.getCategory()).equals(remoteSelectedRecipeBookTab);
            if (matches) { tab.select(); selected = tab; }
            else tab.unselect();
        }
        if (selected != null) componentAccessor.totem$setSelectedTab(selected);
        page = componentAccessor.totem$getRecipeBookPage();
        if (page != null) {
            // Recipe identities are intentionally absent from the protocol. Clear Observer-local unlocks.
            page.updateCollections(List.of(), remoteRecipeBookFiltering, false);
            RecipeBookPageAccessor pageAccessor = (RecipeBookPageAccessor) page;
            pageAccessor.totem$setCurrentPage(remoteRecipeBookPage);
            pageAccessor.totem$setTotalPages(remoteRecipeBookPageCount);
        }
    }

    private record TargetSnapshot(int contentWidth, int contentHeight, int mouseX, int mouseY,
                                  List<ObserverNativeScreenPayloads.SlotState> slots) {
    }

    private record RecipeBookSnapshot(boolean visible, boolean widthTooNarrow, boolean filtering,
                                      boolean searchActive, String selectedTab, int page, int pageCount) {
        private static final RecipeBookSnapshot CLOSED =
                new RecipeBookSnapshot(false, false, false, false, "", 0, 0);
    }

    private static final class ObserverInventoryScreen extends InventoryScreen implements ObserverReadOnlyScreen {
        private ObserverInventoryScreen(net.minecraft.world.entity.player.Player player) {
            super(player);
            var detached = ObserverVanillaScreenSupport.detachedInventory();
            ((AbstractContainerScreenMenuAccessor) (Object) this)
                    .totem$setMenu(new InventoryMenu(detached, true, player));
        }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void onClose() { if (!suppressObserverScreenStop) ObserverVanillaScreenSupport.stopObserving(); }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick){
            super.extractRenderState(graphics,x,y,tick); extractedFrames++;
        }
    }

    private static final class ObserverCraftingTableScreen extends CraftingScreen implements ObserverReadOnlyScreen {
        private ObserverCraftingTableScreen(CraftingMenu menu, net.minecraft.world.entity.player.Inventory inventory,
                                            Component title) { super(menu, inventory, title); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void onClose() { if (!suppressObserverScreenStop) ObserverVanillaScreenSupport.stopObserving(); }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick){
            super.extractRenderState(graphics,x,y,tick); extractedFrames++;
        }
    }
}
