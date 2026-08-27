package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.mixin.client.LoomScreenAccessor;
import dev.totem.vanillatweaks.network.ObserverLoomScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
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
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/loom.png");
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/loom/scroller");
    private static final Identifier SCROLLER_DISABLED =
            Identifier.withDefaultNamespace("container/loom/scroller_disabled");
    private static final Identifier PATTERN = Identifier.withDefaultNamespace("container/loom/pattern");
    private static final Identifier PATTERN_SELECTED =
            Identifier.withDefaultNamespace("container/loom/pattern_selected");
    private static final Identifier ERROR = Identifier.withDefaultNamespace("container/loom/error");
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
    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverLoomScreenClient() {}

    /** Mirrors vanilla's bounded four-by-four Loom viewport. */
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
        if (!ObserverNativeClient.observerSessionActive()) { clearRemote(); closeMirror(); }
        else if (remoteOpen) ensureMirror();
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
        if (!payload.open()) { clearRemote(); closeMirror(); return; }
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
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeLoomMirrorScreen)) {
            suppressMirrorStop = true;
            try { minecraft.setScreenAndShow(new NativeLoomMirrorScreen()); }
            finally { suppressMirrorStop = false; }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeLoomMirrorScreen)) return;
        suppressMirrorStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressMirrorStop = false; }
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

    private static final class NativeLoomMirrorScreen extends ObserverMirrorScreen {
        private BannerFlagModel flag;

        private NativeLoomMirrorScreen() { super(Component.literal("Observer Loom")); }
        @Override protected void init() {
            super.init();
            flag = new BannerFlagModel(minecraft.getEntityModels().bakeLayer(ModelLayers.STANDING_BANNER_FLAG));
        }
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
            Identifier scroller = remoteDisplayPatterns && remotePatterns.size() > PATTERN_COLUMNS * PATTERN_ROWS
                    ? SCROLLER : SCROLLER_DISABLED;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, scroller, left + 119,
                    top + 13 + (int) (41.0F * remoteScrollOffset), 12, 15);

            BannerPatternLayers resultLayers = bannerLayers(remoteResultLayers);
            if (remoteResultAvailable && remoteResultBaseColorId >= 0 && flag != null) {
                graphics.bannerPattern(flag, DyeColor.byId(remoteResultBaseColorId), resultLayers,
                        left + 141, top + 8, left + 161, top + 48);
            } else if (remoteHasMaxPatterns) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ERROR, left + 138, top + 52, 26, 26);
            }

            if (remoteDisplayPatterns) {
                LoomPatternViewport viewport = loomPatternViewport(remoteStartRow, remotePatterns.size());
                for (int i = viewport.first(); i < viewport.lastExclusive(); i++) {
                    int local = i - viewport.first(), col = local % PATTERN_COLUMNS, row = local / PATTERN_COLUMNS;
                    int x = left + PATTERN_GRID_X + col * PATTERN_CELL_SIZE;
                    int y = top + PATTERN_GRID_Y + row * PATTERN_CELL_SIZE;
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                            i == remoteSelectedPatternIndex ? PATTERN_SELECTED : PATTERN,
                            x, y, PATTERN_CELL_SIZE, PATTERN_CELL_SIZE);
                    extractPatternOnButton(graphics, x, y, remotePatterns.get(i));
                }
            }
            for (ObserverNativeScreenPayloads.SlotState slot : remoteSlots) {
                int sx = left + slot.x(), sy = top + slot.y();
                ItemStack stack = itemStack(slot);
                if (!stack.isEmpty()) { graphics.item(stack, sx, sy); graphics.itemDecorations(font, stack, sx, sy); }
            }
            extractedFrames++;
        }

        private static void extractPatternOnButton(GuiGraphicsExtractor graphics, int x, int y,
                                                   ObserverLoomScreenPayloads.PatternState pattern) {
            Holder<BannerPattern> holder = patternHolder(pattern.assetId());
            if (holder == null) return;
            var sprite = graphics.getSprite(Sheets.getBannerSprite(holder));
            float u0 = sprite.getU0();
            float u1 = u0 + (sprite.getU1() - u0) * 21.0F / 64.0F;
            float vRange = sprite.getV1() - sprite.getV0();
            float v0 = sprite.getV0() + vRange / 64.0F;
            float v1 = v0 + vRange * 40.0F / 64.0F;
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + 4.0F, y + 2.0F);
            graphics.fill(0, 0, 5, 10, DyeColor.GRAY.getTextureDiffuseColor());
            graphics.blit(sprite.atlasLocation(), 0, 0, 5, 10, u0, u1, v0, v1);
            graphics.pose().popMatrix();
        }

        private static BannerPatternLayers bannerLayers(
                List<ObserverLoomScreenPayloads.BannerLayerState> layers) {
            List<BannerPatternLayers.Layer> resolved = new ArrayList<>();
            for (ObserverLoomScreenPayloads.BannerLayerState layer : layers) {
                Holder<BannerPattern> holder = patternHolder(layer.assetId());
                if (holder != null) {
                    resolved.add(new BannerPatternLayers.Layer(holder, DyeColor.byId(layer.dyeColorId())));
                }
            }
            return new BannerPatternLayers(List.copyOf(resolved));
        }

        private static Holder<BannerPattern> patternHolder(String assetId) {
            try {
                Identifier id = Identifier.parse(assetId);
                return Holder.direct(new BannerPattern(id, ""));
            } catch (RuntimeException error) {
                return null;
            }
        }
    }
}
