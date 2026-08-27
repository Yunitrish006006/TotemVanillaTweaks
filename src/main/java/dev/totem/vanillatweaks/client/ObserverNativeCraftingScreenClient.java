package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.vanillatweaks.mixin.client.AbstractRecipeBookScreenAccessor;
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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
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
import java.util.function.ToIntFunction;

/** Target adapter and Observer-side reconstruction for player/crafting-table crafting screens. */
public final class ObserverNativeCraftingScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static final int DEFAULT_CONTENT_WIDTH = 176;
    private static final int DEFAULT_CONTENT_HEIGHT = 166;
    private static final int HEADER_GAP = 8;
    private static final Identifier INVENTORY_BACKGROUND = Identifier.withDefaultNamespace(
            "textures/gui/container/inventory.png");
    private static final Identifier CRAFTING_TABLE_BACKGROUND = Identifier.withDefaultNamespace(
            "textures/gui/container/crafting_table.png");
    private static final Identifier RECIPE_BOOK_BACKGROUND = Identifier.withDefaultNamespace(
            "textures/gui/recipe_book.png");
    private static final Identifier RECIPE_BOOK_TAB_SELECTED = Identifier.withDefaultNamespace(
            "recipe_book/tab_selected");
    private static final Identifier RECIPE_BOOK_FILTER_ENABLED = Identifier.withDefaultNamespace(
            "recipe_book/filter_enabled");
    private static final Identifier RECIPE_BOOK_FILTER_DISABLED = Identifier.withDefaultNamespace(
            "recipe_book/filter_disabled");
    private static final Identifier EFFECT_BACKGROUND =
            Identifier.withDefaultNamespace("container/inventory/effect_background");
    private static final Identifier EFFECT_BACKGROUND_AMBIENT =
            Identifier.withDefaultNamespace("container/inventory/effect_background_ambient");

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

    /** Fits the remote title and crafting mode onto one vanilla-style header row. */
    static CraftingHeaderLayout craftingHeaderLayout(
            String title,
            String mode,
            int contentWidth,
            ToIntFunction<String> widthOf
    ) {
        String safeTitle = title == null ? "" : title;
        String safeMode = mode == null ? "" : mode;
        int availableWidth = Math.max(0, contentWidth);
        int titleWidth = widthOf.applyAsInt(safeTitle);
        int modeWidth = widthOf.applyAsInt(safeMode);

        if (titleWidth + HEADER_GAP + modeWidth <= availableWidth) {
            return new CraftingHeaderLayout(safeTitle, titleWidth, safeMode,
                    availableWidth - modeWidth, modeWidth, HEADER_GAP, availableWidth);
        }

        int ellipsisWidth = widthOf.applyAsInt("…");
        int gap = availableWidth >= ellipsisWidth * 2 + HEADER_GAP ? HEADER_GAP : 0;
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
        String fittedMode = fitHeaderText(safeMode, modeBudget, widthOf);
        int fittedTitleWidth = widthOf.applyAsInt(fittedTitle);
        int fittedModeWidth = widthOf.applyAsInt(fittedMode);
        int modeX = availableWidth - fittedModeWidth;
        return new CraftingHeaderLayout(fittedTitle, fittedTitleWidth, fittedMode,
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

    static record CraftingHeaderLayout(
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

    /** Converts bounded semantic tab ids into player-facing text without exposing registry/debug syntax. */
    static String recipeBookTabLabel(String tabId) {
        String safeId = tabId == null ? "" : tabId.trim().toLowerCase(java.util.Locale.ROOT);
        String path = safeId.startsWith("search:")
                ? safeId.substring("search:".length())
                : safeId.substring(Math.max(0, safeId.indexOf(':') + 1));
        String translationKey = switch (path) {
            case "crafting" -> "container.crafting";
            case "furnace" -> "container.furnace";
            case "blast_furnace" -> "container.blast_furnace";
            case "smoker" -> "container.smoker";
            case "crafting_building_blocks", "furnace_blocks", "blast_furnace_blocks" ->
                    "itemGroup.buildingBlocks";
            case "crafting_redstone" -> "itemGroup.redstone";
            case "crafting_equipment", "smithing" -> "itemGroup.tools";
            case "furnace_food", "smoker_food", "campfire" -> "itemGroup.foodAndDrink";
            case "crafting_misc", "furnace_misc", "blast_furnace_misc", "stonecutter" ->
                    "itemGroup.ingredients";
            default -> "";
        };
        if (!translationKey.isEmpty()) {
            return Component.translatable(translationKey).getString();
        }
        if (path.isBlank()) {
            return Component.translatable("itemGroup.search").getString();
        }
        String[] words = path.replace('-', '_').split("_+");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!label.isEmpty()) label.append(' ');
            int first = word.offsetByCodePoints(0, 1);
            label.append(word.substring(0, first).toUpperCase(java.util.Locale.ROOT));
            label.append(word.substring(first));
        }
        return label.isEmpty() ? Component.translatable("itemGroup.search").getString() : label.toString();
    }

    private static List<ItemStack> recipeBookTabIcons(String tabId) {
        String safeId = tabId == null ? "" : tabId.toLowerCase(java.util.Locale.ROOT);
        String path = safeId.startsWith("search:")
                ? safeId.substring("search:".length())
                : safeId.substring(Math.max(0, safeId.indexOf(':') + 1));
        return switch (path) {
            case "crafting", "furnace", "blast_furnace", "smoker" -> List.of(new ItemStack(Items.COMPASS));
            case "crafting_building_blocks", "furnace_blocks", "blast_furnace_blocks" ->
                    List.of(new ItemStack(Items.BRICKS));
            case "crafting_redstone" -> List.of(new ItemStack(Items.REDSTONE));
            case "crafting_equipment", "smithing" ->
                    List.of(new ItemStack(Items.IRON_AXE), new ItemStack(Items.GOLDEN_SWORD));
            case "furnace_food", "smoker_food", "campfire" -> List.of(new ItemStack(Items.PORKCHOP));
            case "stonecutter" -> List.of(new ItemStack(Items.STONECUTTER));
            default -> List.of(new ItemStack(Items.COMPASS));
        };
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

    private record TargetSnapshot(int contentWidth, int contentHeight, int mouseX, int mouseY,
                                  List<ObserverNativeScreenPayloads.SlotState> slots) {
    }

    private record RecipeBookSnapshot(boolean visible, boolean widthTooNarrow, boolean filtering,
                                      boolean searchActive, String selectedTab, int page, int pageCount) {
        private static final RecipeBookSnapshot CLOSED =
                new RecipeBookSnapshot(false, false, false, false, "", 0, 0);
    }

    private static final class NativeCraftingMirrorScreen extends ObserverMirrorScreen {
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
            boolean playerInventory = ObserverCraftingScreenPayloads.VARIANT_PLAYER_2X2.equals(remoteVariant);
            int contentWidth = DEFAULT_CONTENT_WIDTH;
            int contentHeight = DEFAULT_CONTENT_HEIGHT;
            int left = remoteRecipeBookVisible && !remoteRecipeBookWidthTooNarrow
                    ? 177 + (width - contentWidth - 200) / 2
                    : (width - contentWidth) / 2;
            int top = (height - contentHeight) / 2;

            Identifier background = playerInventory ? INVENTORY_BACKGROUND : CRAFTING_TABLE_BACKGROUND;
            graphics.blit(RenderPipelines.GUI_TEXTURED, background, left, top, 0.0F, 0.0F,
                    contentWidth, contentHeight, 256, 256);

            boolean showContainerContents = !(remoteRecipeBookVisible && remoteRecipeBookWidthTooNarrow);
            if (showContainerContents) {
                for (ObserverNativeScreenPayloads.SlotState slot : remoteSlots) {
                    int slotX = left + slot.x();
                    int slotY = top + slot.y();
                    ItemStack stack = itemStack(slot);
                    if (stack.isEmpty() && playerInventory) {
                        Identifier empty = emptyInventorySlotSprite(slot.index());
                        if (empty != null) {
                            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, empty, slotX, slotY, 16, 16);
                        }
                    }
                    if (!stack.isEmpty()) {
                        graphics.item(stack, slotX, slotY);
                        graphics.itemDecorations(this.minecraft.font, stack, slotX, slotY);
                    }
                }
            }

            if (remoteRecipeBookVisible) {
                int bookX = (width - 147) / 2 - (remoteRecipeBookWidthTooNarrow ? 0 : 86);
                graphics.blit(RenderPipelines.GUI_TEXTURED, RECIPE_BOOK_BACKGROUND, bookX, top,
                        1.0F, 1.0F, 147, 166, 256, 256);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, RECIPE_BOOK_TAB_SELECTED,
                        bookX - 32, top + 3, 35, 27);
                List<ItemStack> tabIcons = recipeBookTabIcons(remoteSelectedRecipeBookTab);
                if (tabIcons.size() > 1) {
                    graphics.fakeItem(tabIcons.get(0), bookX - 31, top + 8);
                    graphics.fakeItem(tabIcons.get(1), bookX - 20, top + 8);
                } else {
                    graphics.fakeItem(tabIcons.getFirst(), bookX - 25, top + 8);
                }
                String search = remoteRecipeBookSearchActive
                        ? Component.translatable("itemGroup.search").getString()
                        : Component.translatable("gui.recipebook.search_hint").getString();
                graphics.text(font, fitHeaderText(search, 77, font::width), bookX + 27, top + 17,
                        0xFFFFFFFF, true);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        remoteRecipeBookFiltering ? RECIPE_BOOK_FILTER_ENABLED : RECIPE_BOOK_FILTER_DISABLED,
                        bookX + 110, top + 12, 26, 16);
                String tab = recipeBookTabLabel(remoteSelectedRecipeBookTab);
                graphics.text(font, fitHeaderText(tab, 92, font::width), bookX + 26, top + 34,
                        0xFFFFFFFF, true);
                if (remoteRecipeBookPageCount > 0) {
                    String page = (remoteRecipeBookPage + 1) + "/" + remoteRecipeBookPageCount;
                    graphics.centeredText(font, page, bookX + 73, top + 143, 0xFF404040);
                }
            }

            if (remoteActiveEffectsVisible && playerInventory && !remoteActiveEffects.isEmpty()) {
                extractEffects(graphics, left + contentWidth + 2, top);
            }

            int cursorX = left + remoteMouseX;
            int cursorY = top + remoteMouseY;
            if (cursorX >= 0 && cursorX < width && cursorY >= 0 && cursorY < height) {
                graphics.fill(cursorX - 4, cursorY, cursorX + 5, cursorY + 1, 0xFFFFFFFF);
                graphics.fill(cursorX, cursorY - 4, cursorX + 1, cursorY + 5, 0xFFFFFFFF);
            }
            extractedFrames++;
        }

        private static Identifier emptyInventorySlotSprite(int slotIndex) {
            return switch (slotIndex) {
                case 5 -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
                case 6 -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
                case 7 -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
                case 8 -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
                case 45 -> InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
                default -> null;
            };
        }

        private void extractEffects(GuiGraphicsExtractor graphics, int x, int top) {
            int available = Math.max(32, width - x);
            int panelWidth = available >= 120 ? Math.min(available - 7, 120) : 32;
            int spacing = remoteActiveEffects.size() > 5
                    ? Math.max(1, 132 / (remoteActiveEffects.size() - 1)) : 33;
            int y = top;
            for (ObserverCraftingScreenPayloads.EffectState effect : remoteActiveEffects) {
                var resolved = resolveEffect(effect.effectId());
                if (resolved == null) continue;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        effect.ambient() ? EFFECT_BACKGROUND_AMBIENT : EFFECT_BACKGROUND,
                        x, y, panelWidth, 32);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(resolved),
                        x + 7, y + 7, 18, 18);
                if (panelWidth > 32) {
                    MobEffectInstance instance = new MobEffectInstance(resolved, effect.durationTicks(),
                            effect.amplifier(), effect.ambient(), effect.visible(), effect.showIcon());
                    Component name = effectName(instance);
                    Component duration = MobEffectUtil.formatDuration(instance, 1.0F,
                            minecraft.level == null ? 20.0F : minecraft.level.tickRateManager().tickrate());
                    int textWidth = panelWidth - 39;
                    graphics.text(font, fitHeaderText(name.getString(), textWidth, font::width),
                            x + 32, y + 7, 0xFFFFFFFF, false);
                    graphics.text(font, fitHeaderText(duration.getString(), textWidth, font::width),
                            x + 32, y + 16, 0xFF808080, false);
                }
                y += spacing;
            }
        }

        private static Component effectName(MobEffectInstance effect) {
            var name = effect.getEffect().value().getDisplayName().copy();
            if (effect.getAmplifier() >= 1 && effect.getAmplifier() <= 9) {
                name.append(" ").append(Component.translatable("potion.potency." + effect.getAmplifier()));
            }
            return name;
        }

        private static net.minecraft.core.Holder<MobEffect> resolveEffect(String effectId) {
            try {
                MobEffect effect = BuiltInRegistries.MOB_EFFECT.getValue(Identifier.parse(effectId));
                return effect == null ? null : BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
            } catch (RuntimeException error) {
                return null;
            }
        }
    }
}
