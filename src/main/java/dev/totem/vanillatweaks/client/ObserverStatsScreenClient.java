package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.mixin.client.StatsScreenAccessor;
import dev.totem.vanillatweaks.mixin.client.StatsScreenItemStatisticsListAccessor;
import dev.totem.vanillatweaks.mixin.client.StatsScreenStatisticsTabAccessor;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import dev.totem.vanillatweaks.network.ObserverStatsScreenPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** Framebuffer-free semantic adapter and local reconstruction for vanilla Statistics. */
public final class ObserverStatsScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    static final int SAFE_SCREEN_MARGIN = 12;
    static final int MAX_PANEL_WIDTH = 620;
    static final int MAX_PANEL_HEIGHT = 390;
    static final int PANEL_CONTENT_MARGIN = 10;
    static final int BODY_MARGIN = 8;
    static final int BODY_INNER_PADDING = 8;
    static final int BODY_TOP = 48;
    static final int BODY_BOTTOM_MARGIN = 10;
    static final int TITLE_Y = 8;
    static final int TEXT_HEIGHT = 9;
    static final int TITLE_SORT_GAP = 8;
    static final int MAX_SORT_WIDTH = 150;
    static final int TAB_Y = 25;
    static final int TAB_HEIGHT = 19;
    static final int TAB_GAP = 8;
    static final int MAX_TAB_WIDTH = 84;
    static final int GENERAL_ROW_HEIGHT = 16;
    static final int TABLE_HEADER_HEIGHT = 20;
    static final int TABLE_ROW_HEIGHT = 20;
    static final int TABLE_BOTTOM_PADDING = 4;
    static final int ITEM_ICON_TEXT_GAP = 4;
    static final int COLUMN_GAP = 6;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static long lastRemoteSequence = -1L;
    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static String remoteActiveTab = "general";
    private static boolean remoteLoading;
    private static double remoteScrollAmount;
    private static String remoteItemSortColumn = "";
    private static int remoteItemSortOrder;
    private static List<ObserverStatsScreenPayloads.GeneralRow> remoteGeneralRows = List.of();
    private static List<ObserverStatsScreenPayloads.ItemRow> remoteItemRows = List.of();
    private static List<ObserverStatsScreenPayloads.MobRow> remoteMobRows = List.of();
    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverStatsScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ObserverStatsScreenPayloads.StatsRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverStatsScreenClient::tick);
    }

    public static boolean isTargetScreen(Screen screen) {
        return screen instanceof StatsScreen;
    }

    public static boolean hasRemoteScreen() {
        return remoteOpen && ObserverNativeClient.observerSessionActive();
    }

    private static void tick(Minecraft minecraft) {
        Screen screen = minecraft.gui.screen();
        if (ObserverNativeClient.targetStateEnabled() && minecraft.player != null && minecraft.level != null
                && ObserverNativeClient.targetSupportsScreen(ObserverStatsScreenPayloads.CAPABILITY)
                && screen instanceof StatsScreen statsScreen) {
            tickTarget(statsScreen);
        } else {
            closeTargetIfNeeded();
        }

        if (!ObserverNativeClient.observerSessionActive()) {
            if (remoteOpen || minecraft.gui.screen() instanceof NativeStatsMirrorScreen) {
                clearRemote();
                closeMirror();
            }
        } else if (remoteOpen) {
            ensureMirror();
        }
    }

    private static void tickTarget(StatsScreen screen) {
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        ObserverStatsScreenPayloads.StatsState state = capture(screen);
        if (state == null) return;
        targetOpen = true;
        lastSnapshotNanos = now;
        ClientPlayNetworking.send(state);
    }

    private static void closeTargetIfNeeded() {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (ObserverNativeClient.targetSupportsScreen(ObserverStatsScreenPayloads.CAPABILITY)) {
            ClientPlayNetworking.send(ObserverStatsScreenPayloads.closed(++nextTargetSequence));
        }
    }

    private static ObserverStatsScreenPayloads.StatsState capture(StatsScreen screen) {
        StatsScreenAccessor accessor = (StatsScreenAccessor) (Object) screen;
        StatsCounter counter = accessor.totem$getStats();
        TabManager tabManager = accessor.totem$getTabManager();
        MenuTabBar tabBar = accessor.totem$getTabNavigationBar();
        Tab currentTab = tabManager == null ? null : tabManager.getCurrentTab();
        int tabIndex = tabBar == null || currentTab == null ? 0 : tabBar.getTabs().indexOf(currentTab);
        String activeTab = switch (tabIndex) {
            case 1 -> "items";
            case 2 -> "mobs";
            default -> "general";
        };

        AbstractSelectionList<?> activeList = currentTab instanceof StatsScreenStatisticsTabAccessor tab
                ? tab.totem$getList() : null;
        double scrollAmount = activeList == null ? 0.0D : activeList.scrollAmount();
        boolean loading = accessor.totem$isLoading();
        String sortColumn = "";
        int sortOrder = 0;
        if ("items".equals(activeTab) && activeList instanceof StatsScreenItemStatisticsListAccessor itemList) {
            sortColumn = sortColumn(itemList.totem$getSortColumn());
            sortOrder = itemList.totem$getSortOrder();
        }

        List<ObserverStatsScreenPayloads.GeneralRow> general = List.of();
        List<ObserverStatsScreenPayloads.ItemRow> items = List.of();
        List<ObserverStatsScreenPayloads.MobRow> mobs = List.of();
        if (!loading) {
            switch (activeTab) {
                case "items" -> items = captureItems(counter, sortColumn, sortOrder);
                case "mobs" -> mobs = captureMobs(counter);
                default -> general = captureGeneral(counter);
            }
        }

        return new ObserverStatsScreenPayloads.StatsState(
                ObserverStatsScreenPayloads.PROTOCOL_VERSION, ++nextTargetSequence, true,
                ObserverStatsScreenPayloads.FAMILY_ID, screen.getClass().getName(),
                screen.getTitle() == null ? "" : screen.getTitle().getString(), activeTab, loading, scrollAmount,
                sortColumn, sortOrder, general, items, mobs);
    }

    private static List<ObserverStatsScreenPayloads.GeneralRow> captureGeneral(StatsCounter counter) {
        List<ObserverStatsScreenPayloads.GeneralRow> rows = new ArrayList<>();
        for (Stat<Identifier> stat : Stats.CUSTOM) {
            if (rows.size() >= ObserverStatsScreenPayloads.MAX_GENERAL_ROWS) break;
            int value = Math.max(0, counter.getValue(stat));
            String label = Component.translatable(StatsScreenAccessor.totem$getTranslationKey(stat)).getString();
            rows.add(new ObserverStatsScreenPayloads.GeneralRow(
                    stat.getValue().toString(), label, value, stat.format(value)));
        }
        return List.copyOf(rows);
    }

    private static List<ObserverStatsScreenPayloads.ItemRow> captureItems(StatsCounter counter, String sortColumn, int sortOrder) {
        List<ObserverStatsScreenPayloads.ItemRow> rows = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            int mined = item instanceof BlockItem blockItem
                    ? Math.max(0, counter.getValue(Stats.BLOCK_MINED, blockItem.getBlock())) : 0;
            int broken = Math.max(0, counter.getValue(Stats.ITEM_BROKEN, item));
            int crafted = Math.max(0, counter.getValue(Stats.ITEM_CRAFTED, item));
            int used = Math.max(0, counter.getValue(Stats.ITEM_USED, item));
            int pickedUp = Math.max(0, counter.getValue(Stats.ITEM_PICKED_UP, item));
            int dropped = Math.max(0, counter.getValue(Stats.ITEM_DROPPED, item));
            if (mined == 0 && broken == 0 && crafted == 0 && used == 0 && pickedUp == 0 && dropped == 0) continue;
            rows.add(new ObserverStatsScreenPayloads.ItemRow(
                    BuiltInRegistries.ITEM.getKey(item).toString(), mined, broken, crafted, used, pickedUp, dropped));
        }
        Comparator<ObserverStatsScreenPayloads.ItemRow> comparator;
        if (sortOrder == 0 || sortColumn.isEmpty()) {
            comparator = Comparator.comparing(ObserverStatsScreenPayloads.ItemRow::itemId);
        } else {
            comparator = Comparator.comparingInt(row -> itemSortValue(row, sortColumn));
            if (sortOrder < 0) comparator = comparator.reversed();
            comparator = comparator.thenComparing(ObserverStatsScreenPayloads.ItemRow::itemId);
        }
        rows.sort(comparator);
        if (rows.size() > ObserverStatsScreenPayloads.MAX_ITEM_ROWS) {
            rows = new ArrayList<>(rows.subList(0, ObserverStatsScreenPayloads.MAX_ITEM_ROWS));
        }
        return List.copyOf(rows);
    }

    private static List<ObserverStatsScreenPayloads.MobRow> captureMobs(StatsCounter counter) {
        List<ObserverStatsScreenPayloads.MobRow> rows = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            int killed = Math.max(0, counter.getValue(Stats.ENTITY_KILLED, type));
            int killedBy = Math.max(0, counter.getValue(Stats.ENTITY_KILLED_BY, type));
            if (killed == 0 && killedBy == 0) continue;
            rows.add(new ObserverStatsScreenPayloads.MobRow(
                    BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), type.getDescription().getString(), killed, killedBy));
        }
        rows.sort(Comparator.comparing(ObserverStatsScreenPayloads.MobRow::entityId));
        if (rows.size() > ObserverStatsScreenPayloads.MAX_MOB_ROWS) {
            rows = new ArrayList<>(rows.subList(0, ObserverStatsScreenPayloads.MAX_MOB_ROWS));
        }
        return List.copyOf(rows);
    }

    private static String sortColumn(StatType<?> type) {
        if (type == Stats.BLOCK_MINED) return "mined";
        if (type == Stats.ITEM_BROKEN) return "broken";
        if (type == Stats.ITEM_CRAFTED) return "crafted";
        if (type == Stats.ITEM_USED) return "used";
        if (type == Stats.ITEM_PICKED_UP) return "picked_up";
        if (type == Stats.ITEM_DROPPED) return "dropped";
        return "";
    }

    private static int itemSortValue(ObserverStatsScreenPayloads.ItemRow row, String column) {
        return switch (column) {
            case "mined" -> row.mined();
            case "broken" -> row.broken();
            case "crafted" -> row.crafted();
            case "used" -> row.used();
            case "picked_up" -> row.pickedUp();
            case "dropped" -> row.dropped();
            default -> 0;
        };
    }

    private static void acceptRelay(ObserverStatsScreenPayloads.StatsRelay p) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverStatsScreenPayloads.CAPABILITY)
                || targetId == null || !targetId.equals(p.targetId())
                || p.protocolVersion() != ObserverStatsScreenPayloads.PROTOCOL_VERSION
                || !ObserverStatsScreenPayloads.FAMILY_ID.equals(p.familyId())
                || p.sequence() <= lastRemoteSequence) return;
        lastRemoteSequence = p.sequence();
        if (!p.open()) { clearRemote(); closeMirror(); return; }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = p.title();
        remoteActiveTab = p.activeTab();
        remoteLoading = p.loading();
        remoteScrollAmount = p.scrollAmount();
        remoteItemSortColumn = p.itemSortColumn();
        remoteItemSortOrder = p.itemSortOrder();
        remoteGeneralRows = List.copyOf(p.generalRows());
        remoteItemRows = List.copyOf(p.itemRows());
        remoteMobRows = List.copyOf(p.mobRows());
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeStatsMirrorScreen)) {
            suppressMirrorStop = true;
            try { minecraft.setScreenAndShow(new NativeStatsMirrorScreen()); }
            finally { suppressMirrorStop = false; }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeStatsMirrorScreen)) return;
        suppressMirrorStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressMirrorStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
        remoteActiveTab = "general";
        remoteLoading = false;
        remoteScrollAmount = 0.0D;
        remoteItemSortColumn = "";
        remoteItemSortOrder = 0;
        remoteGeneralRows = List.of();
        remoteItemRows = List.of();
        remoteMobRows = List.of();
    }

    private static ItemStack itemStack(String itemId) {
        if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        } catch (RuntimeException error) {
            return ItemStack.EMPTY;
        }
    }

    /** Resolves the Statistics panel and every table column from the available vanilla GUI viewport. */
    static StatsLayout statsLayout(int screenWidth, int screenHeight) {
        int panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(1, screenWidth - SAFE_SCREEN_MARGIN * 2));
        int panelHeight = Math.min(MAX_PANEL_HEIGHT, Math.max(1, screenHeight - SAFE_SCREEN_MARGIN * 2));
        int left = (screenWidth - panelWidth) / 2;
        int top = (screenHeight - panelHeight) / 2;

        int contentLeft = PANEL_CONTENT_MARGIN;
        int contentRight = Math.max(contentLeft, panelWidth - PANEL_CONTENT_MARGIN);
        int contentWidth = Math.max(0, contentRight - contentLeft);
        int sortWidth = Math.min(MAX_SORT_WIDTH, Math.max(0, (contentWidth - TITLE_SORT_GAP) / 2));
        int sortX = contentRight - sortWidth;
        int titleWidth = Math.max(0, sortX - TITLE_SORT_GAP - contentLeft);

        int tabWidth = Math.min(MAX_TAB_WIDTH, Math.max(1, (contentWidth - TAB_GAP * 2) / 3));
        int bodyX = BODY_MARGIN;
        int bodyY = BODY_TOP;
        int bodyWidth = Math.max(0, panelWidth - BODY_MARGIN * 2);
        int bodyHeight = Math.max(0, panelHeight - BODY_TOP - BODY_BOTTOM_MARGIN);
        int bodyInnerLeft = bodyX + BODY_INNER_PADDING;
        int bodyInnerRight = Math.max(bodyInnerLeft, bodyX + bodyWidth - BODY_INNER_PADDING);
        int bodyInnerWidth = Math.max(0, bodyInnerRight - bodyInnerLeft);

        int itemStatWidth = Math.min(48, Math.max(1, bodyInnerWidth / 9));
        int itemStatsWidth = Math.min(bodyInnerWidth, itemStatWidth * 6);
        int itemFirstStatX = bodyInnerRight - itemStatsWidth;
        int itemLabelX = Math.min(itemFirstStatX, bodyInnerLeft + 20);
        int itemLabelWidth = Math.max(0, itemFirstStatX - ITEM_ICON_TEXT_GAP - itemLabelX);

        int generalValueWidth = Math.min(160, Math.max(1, bodyInnerWidth / 3));
        int generalValueX = bodyInnerRight - generalValueWidth;
        int generalLabelWidth = Math.max(0, generalValueX - COLUMN_GAP - bodyInnerLeft);

        int mobStatWidth = Math.min(100, Math.max(1, bodyInnerWidth / 5));
        int mobStatsAndGaps = Math.min(bodyInnerWidth, mobStatWidth * 2 + COLUMN_GAP * 2);
        int mobNameWidth = Math.max(0, bodyInnerWidth - mobStatsAndGaps);
        int mobKilledX = bodyInnerLeft + mobNameWidth + COLUMN_GAP;
        int mobKilledByX = Math.min(bodyInnerRight, mobKilledX + mobStatWidth + COLUMN_GAP);

        int generalRowCapacity = Math.max(0, (bodyHeight - 8) / GENERAL_ROW_HEIGHT);
        int tableRowCapacity = Math.max(0,
                (bodyHeight - TABLE_HEADER_HEIGHT - TABLE_BOTTOM_PADDING) / TABLE_ROW_HEIGHT);
        return new StatsLayout(
                left, top, panelWidth, panelHeight, screenWidth, screenHeight,
                contentLeft, contentRight, titleWidth, sortX, sortWidth, tabWidth,
                bodyX, bodyY, bodyWidth, bodyHeight, bodyInnerLeft, bodyInnerRight,
                itemLabelX, itemLabelWidth, itemFirstStatX, itemStatWidth,
                generalLabelWidth, generalValueX, generalValueWidth,
                mobNameWidth, mobKilledX, mobKilledByX, mobStatWidth,
                generalRowCapacity, tableRowCapacity);
    }

    /** Clamps a pixel scroll offset to a complete page, never producing a partial final row. */
    static RowWindow rowWindow(int rowCount, double scrollAmount, int rowHeight, int rowCapacity) {
        int safeCount = Math.max(0, rowCount);
        int safeCapacity = Math.max(0, rowCapacity);
        int visibleRows = Math.min(safeCount, safeCapacity);
        int requestedStart;
        if (!Double.isFinite(scrollAmount) || scrollAmount <= 0.0D || rowHeight <= 0) {
            requestedStart = scrollAmount > 0.0D ? Integer.MAX_VALUE : 0;
        } else {
            requestedStart = (int) Math.min(Integer.MAX_VALUE, Math.floor(scrollAmount / rowHeight));
        }
        int maxStart = Math.max(0, safeCount - visibleRows);
        int firstRow = Math.min(Math.max(0, requestedStart), maxStart);
        return new RowWindow(firstRow, visibleRows, safeCapacity);
    }

    /** Fits semantic text to the measured Minecraft font width without splitting a Unicode code point. */
    static BoundedLabel boundedLabel(String text, int maxWidth, ToIntFunction<String> widthOf) {
        String safeText = text == null ? "" : text;
        int safeMaxWidth = Math.max(0, maxWidth);
        if (safeText.isEmpty() || safeMaxWidth == 0) return new BoundedLabel("", 0, safeMaxWidth);
        int fullWidth = widthOf.applyAsInt(safeText);
        if (fullWidth <= safeMaxWidth) return new BoundedLabel(safeText, fullWidth, safeMaxWidth);
        String ellipsis = "…";
        if (widthOf.applyAsInt(ellipsis) > safeMaxWidth) return new BoundedLabel("", 0, safeMaxWidth);
        int low = 0;
        int high = safeText.codePointCount(0, safeText.length());
        while (low < high) {
            int middle = (low + high + 1) / 2;
            int end = safeText.offsetByCodePoints(0, middle);
            if (widthOf.applyAsInt(safeText.substring(0, end) + ellipsis) <= safeMaxWidth) low = middle;
            else high = middle - 1;
        }
        String fitted = safeText.substring(0, safeText.offsetByCodePoints(0, low)) + ellipsis;
        return new BoundedLabel(fitted, widthOf.applyAsInt(fitted), safeMaxWidth);
    }

    static record BoundedLabel(String text, int textWidth, int maxWidth) {
        boolean fits() { return textWidth <= maxWidth; }
    }

    static record RowWindow(int firstRow, int visibleRows, int rowCapacity) {
        int lastExclusive() { return firstRow + visibleRows; }
    }

    static record StatsLayout(
            int left,
            int top,
            int panelWidth,
            int panelHeight,
            int screenWidth,
            int screenHeight,
            int contentLeft,
            int contentRight,
            int titleWidth,
            int sortX,
            int sortWidth,
            int tabWidth,
            int bodyX,
            int bodyY,
            int bodyWidth,
            int bodyHeight,
            int bodyInnerLeft,
            int bodyInnerRight,
            int itemLabelX,
            int itemLabelWidth,
            int itemFirstStatX,
            int itemStatWidth,
            int generalLabelWidth,
            int generalValueX,
            int generalValueWidth,
            int mobNameWidth,
            int mobKilledX,
            int mobKilledByX,
            int mobStatWidth,
            int generalRowCapacity,
            int tableRowCapacity
    ) {
        int right() { return left + panelWidth; }
        int bottom() { return top + panelHeight; }
        int bodyRight() { return bodyX + bodyWidth; }
        int bodyBottom() { return bodyY + bodyHeight; }
        int tabX(int index) { return contentLeft + index * (tabWidth + TAB_GAP); }
        int tabRight(int index) { return tabX(index) + tabWidth; }
        int itemStatX(int index) { return itemFirstStatX + index * itemStatWidth; }
        int itemStatRight(int index) { return itemStatX(index) + itemStatWidth; }
        int generalLabelRight() { return bodyInnerLeft + generalLabelWidth; }
        int generalValueRight() { return generalValueX + generalValueWidth; }
        int mobNameRight() { return bodyInnerLeft + mobNameWidth; }
        int mobKilledRight() { return mobKilledX + mobStatWidth; }
        int mobKilledByRight() { return mobKilledByX + mobStatWidth; }
        boolean fits() {
            return left >= SAFE_SCREEN_MARGIN
                    && top >= SAFE_SCREEN_MARGIN
                    && screenWidth - right() >= SAFE_SCREEN_MARGIN
                    && screenHeight - bottom() >= SAFE_SCREEN_MARGIN
                    && contentLeft + titleWidth + TITLE_SORT_GAP <= sortX
                    && sortX + sortWidth <= contentRight
                    && tabRight(2) <= contentRight
                    && TAB_Y + TAB_HEIGHT <= bodyY
                    && bodyRight() <= panelWidth - BODY_MARGIN
                    && bodyBottom() <= panelHeight - BODY_BOTTOM_MARGIN
                    && itemLabelX + itemLabelWidth + ITEM_ICON_TEXT_GAP <= itemFirstStatX
                    && itemStatRight(5) <= bodyInnerRight
                    && generalLabelRight() + COLUMN_GAP <= generalValueX
                    && generalValueRight() <= bodyInnerRight
                    && mobNameRight() + COLUMN_GAP <= mobKilledX
                    && mobKilledRight() + COLUMN_GAP <= mobKilledByX
                    && mobKilledByRight() <= bodyInnerRight;
        }
    }

    private static final class NativeStatsMirrorScreen extends Screen {
        private NativeStatsMirrorScreen() { super(Component.literal("Observer Statistics")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) ClientPlayNetworking.send(new ObserverPayloads.Stop());
            super.onClose();
        }

        @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, width, height, 0xA0000000);
            StatsLayout layout = statsLayout(width, height);
            int pw = layout.panelWidth();
            int ph = layout.panelHeight();
            int left = layout.left();
            int top = layout.top();
            g.fill(left, top, left + pw, top + ph, 0xFFC6C6C6);
            g.outline(left, top, pw, ph, 0xFF303030);
            BoundedLabel title = boundedLabel(remoteTitle.isBlank() ? "Statistics" : remoteTitle,
                    layout.titleWidth(), font::width);
            g.text(font, title.text(), left + layout.contentLeft(), top + TITLE_Y, 0xFF303030, false);
            drawSort(g, layout, left, top);
            drawTabs(g, layout, left, top);
            int bodyLeft = left + layout.bodyX();
            int bodyTop = top + layout.bodyY();
            int bodyBottom = bodyTop + layout.bodyHeight();
            g.fill(bodyLeft, bodyTop, bodyLeft + layout.bodyWidth(), bodyBottom, 0xFFF0F0F0);
            g.outline(bodyLeft, bodyTop, layout.bodyWidth(), layout.bodyHeight(), 0xFF808080);
            if (remoteLoading) {
                BoundedLabel loading = boundedLabel("Loading statistics…",
                        layout.bodyInnerRight() - layout.bodyInnerLeft(), font::width);
                g.text(font, loading.text(), left + layout.bodyInnerLeft(), bodyTop + 16, 0xFF555555, false);
            } else {
                switch (remoteActiveTab) {
                    case "items" -> drawItems(g, layout, left, top);
                    case "mobs" -> drawMobs(g, layout, left, top);
                    default -> drawGeneral(g, layout, left, top);
                }
            }
            extractedFrames++;
        }

        private void drawSort(GuiGraphicsExtractor g, StatsLayout layout, int left, int top) {
            if (!"items".equals(remoteActiveTab) || remoteItemSortColumn.isEmpty()) return;
            String arrow = remoteItemSortOrder < 0 ? "↓" : remoteItemSortOrder > 0 ? "↑" : "";
            String column = remoteItemSortColumn.replace('_', ' ');
            BoundedLabel sort = boundedLabel("Sort: " + column + (arrow.isEmpty() ? "" : " " + arrow),
                    layout.sortWidth(), font::width);
            g.text(font, sort.text(), left + layout.sortX() + layout.sortWidth() - sort.textWidth(),
                    top + TITLE_Y, 0xFF555555, false);
        }

        private void drawTabs(GuiGraphicsExtractor g, StatsLayout layout, int left, int top) {
            String[] tabs = {"general", "items", "mobs"};
            for (int i = 0; i < tabs.length; i++) {
                int x = left + layout.tabX(i);
                boolean selected = tabs[i].equals(remoteActiveTab);
                g.fill(x, top + TAB_Y, x + layout.tabWidth(), top + TAB_Y + TAB_HEIGHT,
                        selected ? 0xFF8C8C8C : 0xFFE0E0E0);
                g.outline(x, top + TAB_Y, layout.tabWidth(), TAB_HEIGHT, 0xFF707070);
                String rawLabel = switch (tabs[i]) {
                    case "items" -> "Items";
                    case "mobs" -> "Mobs";
                    default -> "General";
                };
                BoundedLabel label = boundedLabel(rawLabel, Math.max(0, layout.tabWidth() - 8), font::width);
                int labelX = x + (layout.tabWidth() - label.textWidth()) / 2;
                g.text(font, label.text(), labelX, top + TAB_Y + 6,
                        selected ? 0xFFFFFFFF : 0xFF404040, false);
            }
        }

        private void drawGeneral(GuiGraphicsExtractor g, StatsLayout layout, int left, int top) {
            RowWindow window = rowWindow(remoteGeneralRows.size(), remoteScrollAmount,
                    GENERAL_ROW_HEIGHT, layout.generalRowCapacity());
            int rowY = top + layout.bodyY() + 4;
            for (int i = window.firstRow(); i < window.lastExclusive(); i++) {
                var row = remoteGeneralRows.get(i);
                if ((i & 1) == 0) {
                    g.fill(left + layout.bodyX() + 2, rowY,
                            left + layout.bodyRight() - 2, rowY + GENERAL_ROW_HEIGHT, 0xFFE5E5E5);
                }
                BoundedLabel label = boundedLabel(row.label(), layout.generalLabelWidth(), font::width);
                BoundedLabel value = boundedLabel(row.formattedValue(), layout.generalValueWidth(), font::width);
                g.text(font, label.text(), left + layout.bodyInnerLeft(), rowY + 3, 0xFF333333, false);
                g.text(font, value.text(), left + layout.generalValueX() + layout.generalValueWidth() - value.textWidth(),
                        rowY + 3, 0xFF555555, false);
                rowY += GENERAL_ROW_HEIGHT;
            }
        }

        private void drawItems(GuiGraphicsExtractor g, StatsLayout layout, int left, int top) {
            int headerY = top + layout.bodyY() + 5;
            BoundedLabel itemHeader = boundedLabel("Item",
                    layout.itemFirstStatX() - layout.bodyInnerLeft() - ITEM_ICON_TEXT_GAP, font::width);
            g.text(font, itemHeader.text(), left + layout.bodyInnerLeft(), headerY, 0xFF555555, false);
            String[] headers = {"Mine", "Break", "Craft", "Use", "Pick", "Drop"};
            for (int i = 0; i < headers.length; i++) {
                BoundedLabel header = boundedLabel(headers[i], Math.max(0, layout.itemStatWidth() - 4), font::width);
                g.text(font, header.text(), left + layout.itemStatX(i)
                                + (layout.itemStatWidth() - header.textWidth()) / 2,
                        headerY, 0xFF555555, false);
            }
            RowWindow window = rowWindow(remoteItemRows.size(), remoteScrollAmount,
                    TABLE_ROW_HEIGHT, layout.tableRowCapacity());
            int rowY = top + layout.bodyY() + TABLE_HEADER_HEIGHT;
            for (int i = window.firstRow(); i < window.lastExclusive(); i++) {
                var row = remoteItemRows.get(i);
                if ((i & 1) == 0) {
                    g.fill(left + layout.bodyX() + 2, rowY,
                            left + layout.bodyRight() - 2, rowY + TABLE_ROW_HEIGHT, 0xFFE5E5E5);
                }
                ItemStack stack = itemStack(row.itemId());
                if (!stack.isEmpty()) g.item(stack, left + layout.bodyInnerLeft(), rowY + 2);
                String shortId = row.itemId().startsWith("minecraft:") ? row.itemId().substring(10) : row.itemId();
                BoundedLabel itemName = boundedLabel(shortId, layout.itemLabelWidth(), font::width);
                g.text(font, itemName.text(), left + layout.itemLabelX(), rowY + 6, 0xFF333333, false);
                int[] values = {row.mined(), row.broken(), row.crafted(), row.used(), row.pickedUp(), row.dropped()};
                for (int c = 0; c < values.length; c++) {
                    BoundedLabel value = boundedLabel(Integer.toString(values[c]),
                            Math.max(0, layout.itemStatWidth() - 4), font::width);
                    g.text(font, value.text(), left + layout.itemStatRight(c) - 2 - value.textWidth(),
                            rowY + 6, 0xFF555555, false);
                }
                rowY += TABLE_ROW_HEIGHT;
            }
        }

        private void drawMobs(GuiGraphicsExtractor g, StatsLayout layout, int left, int top) {
            int headerY = top + layout.bodyY() + 5;
            BoundedLabel mobHeader = boundedLabel("Mob", layout.mobNameWidth(), font::width);
            BoundedLabel killedHeader = boundedLabel("Killed", layout.mobStatWidth(), font::width);
            BoundedLabel killedByHeader = boundedLabel("Killed by", layout.mobStatWidth(), font::width);
            g.text(font, mobHeader.text(), left + layout.bodyInnerLeft(), headerY, 0xFF555555, false);
            g.text(font, killedHeader.text(), left + layout.mobKilledX()
                            + (layout.mobStatWidth() - killedHeader.textWidth()) / 2,
                    headerY, 0xFF555555, false);
            g.text(font, killedByHeader.text(), left + layout.mobKilledByX()
                            + (layout.mobStatWidth() - killedByHeader.textWidth()) / 2,
                    headerY, 0xFF555555, false);
            RowWindow window = rowWindow(remoteMobRows.size(), remoteScrollAmount,
                    TABLE_ROW_HEIGHT, layout.tableRowCapacity());
            int rowY = top + layout.bodyY() + TABLE_HEADER_HEIGHT;
            for (int i = window.firstRow(); i < window.lastExclusive(); i++) {
                var row = remoteMobRows.get(i);
                if ((i & 1) == 0) {
                    g.fill(left + layout.bodyX() + 2, rowY,
                            left + layout.bodyRight() - 2, rowY + TABLE_ROW_HEIGHT, 0xFFE5E5E5);
                }
                BoundedLabel name = boundedLabel(row.name(), layout.mobNameWidth(), font::width);
                BoundedLabel killed = boundedLabel(Integer.toString(row.killed()),
                        Math.max(0, layout.mobStatWidth() - 4), font::width);
                BoundedLabel killedBy = boundedLabel(Integer.toString(row.killedBy()),
                        Math.max(0, layout.mobStatWidth() - 4), font::width);
                g.text(font, name.text(), left + layout.bodyInnerLeft(), rowY + 6, 0xFF333333, false);
                g.text(font, killed.text(), left + layout.mobKilledRight() - 2 - killed.textWidth(),
                        rowY + 6, 0xFF555555, false);
                g.text(font, killedBy.text(), left + layout.mobKilledByRight() - 2 - killedBy.textWidth(),
                        rowY + 6, 0xFF555555, false);
                rowY += TABLE_ROW_HEIGHT;
            }
        }
    }
}
