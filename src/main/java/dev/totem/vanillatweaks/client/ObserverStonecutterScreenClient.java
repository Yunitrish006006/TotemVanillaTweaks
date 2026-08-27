package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.mixin.client.StonecutterScreenAccessor;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import dev.totem.vanillatweaks.network.ObserverStonecutterScreenPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Semantic adapter and local reconstruction for the vanilla Stonecutter screen. */
public final class ObserverStonecutterScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;
    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static int remoteSelectedRecipeIndex = -1;
    private static int remoteRecipeCount;
    private static int remoteStartIndex;
    private static float remoteScrollOffset;
    private static boolean remoteDisplayRecipes;
    private static boolean remoteHasInputItem;
    private static boolean remoteResultAvailable;
    private static List<ObserverStonecutterScreenPayloads.RecipeState> remoteRecipes = List.of();
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();
    private static boolean suppressMirrorStop;
    private static long extractedFrames;
    private static final int RECIPE_COLUMNS = 4;
    private static final int RECIPE_ROWS = 3;
    private static final int RECIPE_CELL_WIDTH = 16;
    private static final int RECIPE_CELL_HEIGHT = 18;
    private static final Identifier BACKGROUND =
            Identifier.withDefaultNamespace("textures/gui/container/stonecutter.png");
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/stonecutter/scroller");
    private static final Identifier SCROLLER_DISABLED =
            Identifier.withDefaultNamespace("container/stonecutter/scroller_disabled");
    private static final Identifier RECIPE = Identifier.withDefaultNamespace("container/stonecutter/recipe");
    private static final Identifier RECIPE_SELECTED =
            Identifier.withDefaultNamespace("container/stonecutter/recipe_selected");

    private ObserverStonecutterScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ObserverStonecutterScreenPayloads.StonecutterRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverStonecutterScreenClient::tick);
    }

    public static boolean isStonecutterScreen(Screen screen) {
        return screen instanceof StonecutterScreen;
    }

    private static void tick(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) closeTarget(false);
        else tickTarget(minecraft);
        if (!ObserverNativeClient.observerSessionActive()) { clearRemote(); closeMirror(); }
        else if (remoteOpen) ensureMirror();
    }

    private static void tickTarget(Minecraft minecraft) {
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverStonecutterScreenPayloads.CAPABILITY);
        Screen screen = minecraft.gui.screen();
        if (!supported || !(screen instanceof StonecutterScreen stonecutterScreen)) {
            closeTarget(supported);
            return;
        }
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        targetOpen = true;
        lastSnapshotNanos = now;
        ClientPlayNetworking.send(captureTargetState(stonecutterScreen, ++nextTargetSequence));
    }

    static ObserverStonecutterScreenPayloads.StonecutterState captureTargetState(
            StonecutterScreen screen, long sequence) {
        StonecutterMenu menu = screen.getMenu();
        StonecutterScreenAccessor accessor = (StonecutterScreenAccessor) screen;
        int recipeCount = Math.min(menu.getNumberOfVisibleRecipes(), ObserverStonecutterScreenPayloads.MAX_RECIPES);
        int selected = menu.getSelectedRecipeIndex();
        if (selected >= recipeCount) selected = -1;
        boolean hasInput = menu.hasInputItem();
        boolean resultAvailable = menu.slots.size() > 1 && !menu.slots.get(1).getItem().isEmpty();
        List<ObserverStonecutterScreenPayloads.RecipeState> recipes = captureRecipes(menu, recipeCount);
        int maxStartIndex = Math.max(0, Math.ceilDiv(recipeCount, RECIPE_COLUMNS) - RECIPE_ROWS) * RECIPE_COLUMNS;
        int startIndex = Math.max(0, Math.min(accessor.totem$getStartIndex(), maxStartIndex));
        return new ObserverStonecutterScreenPayloads.StonecutterState(
                ObserverStonecutterScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverStonecutterScreenPayloads.FAMILY_ID, screen.getClass().getName(),
                screen.getTitle() == null ? "" : screen.getTitle().getString(),
                selected, recipeCount, startIndex, accessor.totem$getScrollOffs(),
                accessor.totem$getDisplayRecipes(), hasInput, resultAvailable, recipes, captureSlots(menu));
    }

    private static List<ObserverStonecutterScreenPayloads.RecipeState> captureRecipes(
            StonecutterMenu menu, int limit) {
        if (Minecraft.getInstance().level == null || limit <= 0) return List.of();
        List<ObserverStonecutterScreenPayloads.RecipeState> result = new ArrayList<>(limit);
        List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> entries =
                menu.getVisibleRecipes().entries();
        var context = SlotDisplayContext.fromLevel(Minecraft.getInstance().level);
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            SelectableRecipe<StonecutterRecipe> recipe = entries.get(i).recipe();
            String recipeId = recipe.recipe()
                    .map(holder -> holder.id().identifier().toString()).orElse("");
            ItemStack output = recipe.optionDisplay().resolveForFirstStack(context);
            result.add(new ObserverStonecutterScreenPayloads.RecipeState(i, recipeId,
                    output.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(output.getItem()).toString(),
                    output.isEmpty() ? 0 : output.getCount(), output.isEmpty() ? 0 : output.getDamageValue()));
        }
        return List.copyOf(result);
    }

    private static void closeTarget(boolean canSend) {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) ClientPlayNetworking.send(ObserverStonecutterScreenPayloads.closed(++nextTargetSequence));
    }

    private static List<ObserverNativeScreenPayloads.SlotState> captureSlots(StonecutterMenu menu) {
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

    private static void acceptRelay(ObserverStonecutterScreenPayloads.StonecutterRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverStonecutterScreenPayloads.CAPABILITY)
                || targetId == null || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverStonecutterScreenPayloads.PROTOCOL_VERSION
                || !ObserverStonecutterScreenPayloads.FAMILY_ID.equals(payload.familyId())
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverStonecutterScreenPayloads.FAMILY_ID,
                        payload.targetId(), payload.sequence())) return;
        if (!payload.open()) { clearRemote(); closeMirror(); return; }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = payload.title();
        remoteSelectedRecipeIndex = payload.selectedRecipeIndex();
        remoteRecipeCount = payload.recipeCount();
        remoteStartIndex = payload.startIndex();
        remoteScrollOffset = payload.scrollOffset();
        remoteDisplayRecipes = payload.displayRecipes();
        remoteHasInputItem = payload.hasInputItem();
        remoteResultAvailable = payload.resultAvailable();
        remoteRecipes = List.copyOf(payload.recipes());
        remoteSlots = List.copyOf(payload.slots());
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeStonecutterMirrorScreen)) {
            suppressMirrorStop = true;
            try { minecraft.setScreenAndShow(new NativeStonecutterMirrorScreen()); }
            finally { suppressMirrorStop = false; }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeStonecutterMirrorScreen)) return;
        suppressMirrorStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressMirrorStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
        remoteSelectedRecipeIndex = -1;
        remoteRecipeCount = 0;
        remoteStartIndex = 0;
        remoteScrollOffset = 0.0F;
        remoteDisplayRecipes = false;
        remoteHasInputItem = false;
        remoteResultAvailable = false;
        remoteRecipes = List.of();
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

    private static final class NativeStonecutterMirrorScreen extends ObserverMirrorScreen {
        private NativeStonecutterMirrorScreen() { super(Component.literal("Observer Stonecutter")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) ClientPlayNetworking.send(new ObserverPayloads.Stop());
            super.onClose();
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0x90000000);
            int pw = 176, ph = 166, left = (width - pw) / 2, top = (height - ph) / 2;
            graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 0.0F, 0.0F,
                    pw, ph, 256, 256);
            Identifier scroller = remoteDisplayRecipes && remoteRecipeCount > RECIPE_COLUMNS * RECIPE_ROWS
                    ? SCROLLER : SCROLLER_DISABLED;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, scroller, left + 119,
                    top + 15 + (int) (41.0F * remoteScrollOffset), 12, 15);
            if (remoteDisplayRecipes) {
                int end = Math.min(remoteRecipeCount, remoteStartIndex + RECIPE_COLUMNS * RECIPE_ROWS);
                for (int recipeIndex = remoteStartIndex; recipeIndex < end; recipeIndex++) {
                    ObserverStonecutterScreenPayloads.RecipeState recipe = recipeAt(recipeIndex);
                    if (recipe == null) continue;
                    int local = recipeIndex - remoteStartIndex;
                    int x = left + 52 + (local % RECIPE_COLUMNS) * RECIPE_CELL_WIDTH;
                    int y = top + 14 + (local / RECIPE_COLUMNS) * RECIPE_CELL_HEIGHT;
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                            recipeIndex == remoteSelectedRecipeIndex ? RECIPE_SELECTED : RECIPE,
                            x, y + 1, RECIPE_CELL_WIDTH, RECIPE_CELL_HEIGHT);
                    ItemStack output = itemStack(recipe);
                    if (!output.isEmpty()) graphics.item(output, x, y + 2);
                }
            }
            for (ObserverNativeScreenPayloads.SlotState slot : remoteSlots) {
                int sx = left + slot.x(), sy = top + slot.y();
                ItemStack stack = ObserverStonecutterScreenClient.itemStack(slot);
                if (!stack.isEmpty()) { graphics.item(stack, sx, sy); graphics.itemDecorations(font, stack, sx, sy); }
            }
            extractedFrames++;
        }

        private static ObserverStonecutterScreenPayloads.RecipeState recipeAt(int index) {
            if (index >= 0 && index < remoteRecipes.size() && remoteRecipes.get(index).index() == index) {
                return remoteRecipes.get(index);
            }
            for (var recipe : remoteRecipes) if (recipe.index() == index) return recipe;
            return null;
        }

        private static ItemStack itemStack(ObserverStonecutterScreenPayloads.RecipeState recipe) {
            if (recipe.outputItemId().isBlank() || recipe.outputCount() <= 0) return ItemStack.EMPTY;
            try {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(recipe.outputItemId()));
                if (item == null) return ItemStack.EMPTY;
                ItemStack stack = new ItemStack(item, recipe.outputCount());
                if (recipe.outputDamage() > 0 && stack.isDamageableItem()) {
                    stack.setDamageValue(recipe.outputDamage());
                }
                return stack;
            } catch (RuntimeException error) {
                return ItemStack.EMPTY;
            }
        }
    }
}
