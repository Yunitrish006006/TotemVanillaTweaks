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

/** Framebuffer-free semantic adapter and local reconstruction for vanilla Statistics. */
public final class ObserverStatsScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
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

    private static final class NativeStatsMirrorScreen extends Screen {
        private NativeStatsMirrorScreen() { super(Component.literal("Observer Statistics")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) ClientPlayNetworking.send(new ObserverPayloads.Stop());
            super.onClose();
        }

        @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, width, height, 0xA0000000);
            int pw = Math.min(620, Math.max(340, width - 28));
            int ph = Math.min(390, Math.max(240, height - 28));
            int left = (width - pw) / 2;
            int top = (height - ph) / 2;
            g.fill(left, top, left + pw, top + ph, 0xFFC6C6C6);
            g.outline(left, top, pw, ph, 0xFF303030);
            g.text(font, remoteTitle.isBlank() ? "Statistics" : remoteTitle, left + 10, top + 9, 0xFF303030, false);
            drawTabs(g, left, top, pw);
            int bodyTop = top + 48;
            int bodyBottom = top + ph - 12;
            g.fill(left + 8, bodyTop, left + pw - 8, bodyBottom, 0xFFF0F0F0);
            g.outline(left + 8, bodyTop, pw - 16, bodyBottom - bodyTop, 0xFF808080);
            if (remoteLoading) {
                g.text(font, "Loading statistics…", left + 20, bodyTop + 16, 0xFF555555, false);
            } else {
                switch (remoteActiveTab) {
                    case "items" -> drawItems(g, left + 8, bodyTop, pw - 16, bodyBottom - bodyTop);
                    case "mobs" -> drawMobs(g, left + 8, bodyTop, pw - 16, bodyBottom - bodyTop);
                    default -> drawGeneral(g, left + 8, bodyTop, pw - 16, bodyBottom - bodyTop);
                }
            }
            extractedFrames++;
        }

        private void drawTabs(GuiGraphicsExtractor g, int left, int top, int pw) {
            String[] tabs = {"general", "items", "mobs"};
            for (int i = 0; i < tabs.length; i++) {
                int x = left + 10 + i * 92;
                boolean selected = tabs[i].equals(remoteActiveTab);
                g.fill(x, top + 25, x + 84, top + 44, selected ? 0xFF8C8C8C : 0xFFE0E0E0);
                g.outline(x, top + 25, 84, 19, 0xFF707070);
                g.text(font, switch (tabs[i]) { case "items" -> "Items"; case "mobs" -> "Mobs"; default -> "General"; },
                        x + 8, top + 31, selected ? 0xFFFFFFFF : 0xFF404040, false);
            }
            if ("items".equals(remoteActiveTab) && !remoteItemSortColumn.isEmpty()) {
                String arrow = remoteItemSortOrder < 0 ? "↓" : remoteItemSortOrder > 0 ? "↑" : "";
                g.text(font, "Sort: " + remoteItemSortColumn + " " + arrow, left + pw - 150, top + 31, 0xFF555555, false);
            }
        }

        private void drawGeneral(GuiGraphicsExtractor g, int x, int y, int w, int h) {
            int rowHeight = 16;
            int start = Math.max(0, Math.min(remoteGeneralRows.size(), (int) (remoteScrollAmount / rowHeight)));
            int visible = Math.max(1, (h - 8) / rowHeight);
            int rowY = y + 5;
            for (int i = start; i < remoteGeneralRows.size() && i < start + visible; i++) {
                var row = remoteGeneralRows.get(i);
                if ((i & 1) == 0) g.fill(x + 2, rowY - 2, x + w - 2, rowY + rowHeight - 2, 0xFFE5E5E5);
                g.text(font, row.label(), x + 8, rowY + 2, 0xFF333333, false);
                int valueWidth = font.width(row.formattedValue());
                g.text(font, row.formattedValue(), x + w - 10 - valueWidth, rowY + 2, 0xFF555555, false);
                rowY += rowHeight;
            }
        }

        private void drawItems(GuiGraphicsExtractor g, int x, int y, int w, int h) {
            int rowHeight = 20;
            int start = Math.max(0, Math.min(remoteItemRows.size(), (int) (remoteScrollAmount / rowHeight)));
            int visible = Math.max(1, (h - 22) / rowHeight);
            String[] headers = {"Mine", "Break", "Craft", "Use", "Pick", "Drop"};
            int firstColumn = x + Math.max(130, w - 300);
            for (int i = 0; i < headers.length; i++) g.text(font, headers[i], firstColumn + i * 48, y + 5, 0xFF555555, false);
            int rowY = y + 20;
            for (int i = start; i < remoteItemRows.size() && i < start + visible; i++) {
                var row = remoteItemRows.get(i);
                if ((i & 1) == 0) g.fill(x + 2, rowY - 2, x + w - 2, rowY + rowHeight - 2, 0xFFE5E5E5);
                ItemStack stack = itemStack(row.itemId());
                if (!stack.isEmpty()) g.item(stack, x + 6, rowY);
                String shortId = row.itemId().startsWith("minecraft:") ? row.itemId().substring(10) : row.itemId();
                g.text(font, shortId, x + 26, rowY + 5, 0xFF333333, false);
                int[] values = {row.mined(), row.broken(), row.crafted(), row.used(), row.pickedUp(), row.dropped()};
                for (int c = 0; c < values.length; c++) {
                    String text = Integer.toString(values[c]);
                    g.text(font, text, firstColumn + c * 48, rowY + 5, 0xFF555555, false);
                }
                rowY += rowHeight;
            }
        }

        private void drawMobs(GuiGraphicsExtractor g, int x, int y, int w, int h) {
            int rowHeight = 20;
            int start = Math.max(0, Math.min(remoteMobRows.size(), (int) (remoteScrollAmount / rowHeight)));
            int visible = Math.max(1, (h - 22) / rowHeight);
            g.text(font, "Mob", x + 8, y + 5, 0xFF555555, false);
            g.text(font, "Killed", x + w - 150, y + 5, 0xFF555555, false);
            g.text(font, "Killed by", x + w - 80, y + 5, 0xFF555555, false);
            int rowY = y + 20;
            for (int i = start; i < remoteMobRows.size() && i < start + visible; i++) {
                var row = remoteMobRows.get(i);
                if ((i & 1) == 0) g.fill(x + 2, rowY - 2, x + w - 2, rowY + rowHeight - 2, 0xFFE5E5E5);
                g.text(font, row.name(), x + 8, rowY + 5, 0xFF333333, false);
                g.text(font, Integer.toString(row.killed()), x + w - 150, rowY + 5, 0xFF555555, false);
                g.text(font, Integer.toString(row.killedBy()), x + w - 80, rowY + 5, 0xFF555555, false);
                rowY += rowHeight;
            }
        }
    }
}
