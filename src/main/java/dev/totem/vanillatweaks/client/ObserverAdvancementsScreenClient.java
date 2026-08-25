package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.mixin.client.AdvancementTabAccessor;
import dev.totem.vanillatweaks.mixin.client.AdvancementsScreenAccessor;
import dev.totem.vanillatweaks.mixin.client.ClientAdvancementsAccessor;
import dev.totem.vanillatweaks.network.ObserverAdvancementsScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Framebuffer-free semantic adapter and local reconstruction for vanilla Advancements. */
public final class ObserverAdvancementsScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static long lastRemoteSequence = -1L;
    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static String remoteSelectedRootId = "";
    private static double remoteScrollX;
    private static double remoteScrollY;
    private static List<ObserverAdvancementsScreenPayloads.TabState> remoteTabs = List.of();
    private static List<ObserverAdvancementsScreenPayloads.NodeState> remoteNodes = List.of();
    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverAdvancementsScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ObserverAdvancementsScreenPayloads.AdvancementsRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverAdvancementsScreenClient::tick);
    }

    public static boolean isTargetScreen(Screen screen) {
        return screen instanceof AdvancementsScreen;
    }

    public static boolean hasRemoteScreen() {
        return remoteOpen && ObserverNativeClient.observerSessionActive();
    }

    private static void tick(Minecraft minecraft) {
        Screen screen = minecraft.gui.screen();
        if (ObserverNativeClient.targetStateEnabled() && minecraft.player != null && minecraft.level != null
                && minecraft.getConnection() != null
                && ObserverNativeClient.targetSupportsScreen(ObserverAdvancementsScreenPayloads.CAPABILITY)
                && screen instanceof AdvancementsScreen advancementsScreen) {
            tickTarget(minecraft, advancementsScreen);
        } else {
            closeTargetIfNeeded();
        }

        if (!ObserverNativeClient.observerSessionActive()) {
            if (remoteOpen || minecraft.gui.screen() instanceof NativeAdvancementsMirrorScreen) {
                clearRemote();
                closeMirror();
            }
        } else if (remoteOpen) {
            ensureMirror();
        }
    }

    private static void tickTarget(Minecraft minecraft, AdvancementsScreen screen) {
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        ObserverAdvancementsScreenPayloads.AdvancementsState state = capture(minecraft, screen);
        if (state == null) return;
        targetOpen = true;
        lastSnapshotNanos = now;
        ClientPlayNetworking.send(state);
    }

    private static void closeTargetIfNeeded() {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (ObserverNativeClient.targetSupportsScreen(ObserverAdvancementsScreenPayloads.CAPABILITY)) {
            ClientPlayNetworking.send(ObserverAdvancementsScreenPayloads.closed(++nextTargetSequence));
        }
    }

    private static ObserverAdvancementsScreenPayloads.AdvancementsState capture(Minecraft minecraft, AdvancementsScreen screen) {
        ClientAdvancements manager = minecraft.getConnection().getAdvancements();
        AdvancementTree tree = manager.getTree();
        Map<AdvancementHolder, AdvancementProgress> progress =
                ((ClientAdvancementsAccessor) (Object) manager).totem$getProgress();

        AdvancementTab selectedTab = ((AdvancementsScreenAccessor) (Object) screen).totem$getSelectedTab();
        String selectedRootId = selectedTab == null ? "" : selectedTab.getRootNode().holder().id().toString();
        double scrollX = selectedTab == null ? 0.0D : ((AdvancementTabAccessor) (Object) selectedTab).totem$getScrollX();
        double scrollY = selectedTab == null ? 0.0D : ((AdvancementTabAccessor) (Object) selectedTab).totem$getScrollY();

        List<ObserverAdvancementsScreenPayloads.TabState> tabs = captureTabs(tree);
        if (selectedRootId.isEmpty() && !tabs.isEmpty()) selectedRootId = tabs.getFirst().rootId();
        List<ObserverAdvancementsScreenPayloads.NodeState> nodes = captureNodes(tree, progress, selectedRootId);

        return new ObserverAdvancementsScreenPayloads.AdvancementsState(
                ObserverAdvancementsScreenPayloads.PROTOCOL_VERSION, ++nextTargetSequence, true,
                ObserverAdvancementsScreenPayloads.FAMILY_ID, screen.getClass().getName(),
                screen.getTitle() == null ? "" : screen.getTitle().getString(), selectedRootId,
                scrollX, scrollY, tabs, nodes);
    }

    private static List<ObserverAdvancementsScreenPayloads.TabState> captureTabs(AdvancementTree tree) {
        List<ObserverAdvancementsScreenPayloads.TabState> result = new ArrayList<>();
        for (AdvancementNode root : tree.roots()) {
            if (result.size() >= ObserverAdvancementsScreenPayloads.MAX_TABS) break;
            var display = root.advancement().display();
            if (display.isEmpty()) continue;
            DisplayInfo info = display.get();
            result.add(new ObserverAdvancementsScreenPayloads.TabState(
                    root.holder().id().toString(), info.getTitle().getString(), itemId(info.getIcon())));
        }
        result.sort(Comparator.comparing(ObserverAdvancementsScreenPayloads.TabState::rootId));
        return List.copyOf(result);
    }

    private static List<ObserverAdvancementsScreenPayloads.NodeState> captureNodes(
            AdvancementTree tree, Map<AdvancementHolder, AdvancementProgress> progress, String selectedRootId) {
        if (selectedRootId.isEmpty()) return List.of();
        List<ObserverAdvancementsScreenPayloads.NodeState> result = new ArrayList<>();
        for (AdvancementNode node : tree.nodes()) {
            if (result.size() >= ObserverAdvancementsScreenPayloads.MAX_NODES) break;
            if (!selectedRootId.equals(node.root().holder().id().toString())) continue;
            var display = node.advancement().display();
            if (display.isEmpty()) continue;
            DisplayInfo info = display.get();
            AdvancementProgress nodeProgress = progress.get(node.holder());
            float percent = nodeProgress == null ? 0.0F : Math.max(0.0F, Math.min(1.0F, nodeProgress.getPercent()));
            boolean done = nodeProgress != null && nodeProgress.isDone();
            AdvancementNode visibleParent = visibleParent(node);
            result.add(new ObserverAdvancementsScreenPayloads.NodeState(
                    node.holder().id().toString(), selectedRootId,
                    visibleParent == null ? "" : visibleParent.holder().id().toString(),
                    info.getTitle().getString(), info.getDescription().getString(), itemId(info.getIcon()),
                    info.getType().getSerializedName(), info.getX(), info.getY(), percent, done, info.isHidden()));
        }
        return List.copyOf(result);
    }

    private static AdvancementNode visibleParent(AdvancementNode node) {
        AdvancementNode parent = node.parent();
        while (parent != null && parent.advancement().display().isEmpty()) parent = parent.parent();
        return parent;
    }

    private static String itemId(ItemStackTemplate stack) {
        if (stack == null || stack.item() == null) return "";
        return BuiltInRegistries.ITEM.getKey(stack.item().value()).toString();
    }

    private static void acceptRelay(ObserverAdvancementsScreenPayloads.AdvancementsRelay p) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverAdvancementsScreenPayloads.CAPABILITY)
                || targetId == null || !targetId.equals(p.targetId())
                || p.protocolVersion() != ObserverAdvancementsScreenPayloads.PROTOCOL_VERSION
                || !ObserverAdvancementsScreenPayloads.FAMILY_ID.equals(p.familyId())
                || p.sequence() <= lastRemoteSequence) return;
        lastRemoteSequence = p.sequence();
        if (!p.open()) { clearRemote(); closeMirror(); return; }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = p.title();
        remoteSelectedRootId = p.selectedRootId();
        remoteScrollX = p.scrollX();
        remoteScrollY = p.scrollY();
        remoteTabs = List.copyOf(p.tabs());
        remoteNodes = List.copyOf(p.nodes());
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeAdvancementsMirrorScreen)) {
            suppressMirrorStop = true;
            try { minecraft.setScreenAndShow(new NativeAdvancementsMirrorScreen()); }
            finally { suppressMirrorStop = false; }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeAdvancementsMirrorScreen)) return;
        suppressMirrorStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressMirrorStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
        remoteSelectedRootId = "";
        remoteScrollX = 0.0D;
        remoteScrollY = 0.0D;
        remoteTabs = List.of();
        remoteNodes = List.of();
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

    private static final class NativeAdvancementsMirrorScreen extends Screen {
        private NativeAdvancementsMirrorScreen() { super(Component.literal("Observer Advancements")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) ClientPlayNetworking.send(new ObserverPayloads.Stop());
            super.onClose();
        }

        @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, width, height, 0xA0000000);
            int pw = Math.min(560, Math.max(300, width - 28));
            int ph = Math.min(360, Math.max(230, height - 28));
            int left = (width - pw) / 2;
            int top = (height - ph) / 2;
            int tabH = 28;
            int contentTop = top + 34;
            int contentBottom = top + ph - 12;

            g.fill(left, top, left + pw, top + ph, 0xFF252525);
            g.fill(left + 3, top + 3, left + pw - 3, top + ph - 3, 0xFFC6C6C6);
            g.outline(left, top, pw, ph, 0xFF000000);
            g.text(font, remoteTitle.isBlank() ? "Advancements" : remoteTitle, left + 10, top + 10, 0xFF404040, false);

            drawTabs(g, left, top, pw, tabH);
            g.fill(left + 8, contentTop, left + pw - 8, contentBottom, 0xFF101010);
            g.outline(left + 8, contentTop, pw - 16, contentBottom - contentTop, 0xFF606060);
            drawTree(g, left + 8, contentTop, pw - 16, contentBottom - contentTop);
            extractedFrames++;
        }

        private void drawTabs(GuiGraphicsExtractor g, int left, int top, int pw, int tabH) {
            if (remoteTabs.isEmpty()) return;
            int maxVisible = Math.max(1, (pw - 140) / 30);
            int count = Math.min(remoteTabs.size(), maxVisible);
            int startX = left + pw - count * 30 - 8;
            for (int i = 0; i < count; i++) {
                var tab = remoteTabs.get(i);
                int x = startX + i * 30;
                boolean selected = tab.rootId().equals(remoteSelectedRootId);
                g.fill(x, top + 4, x + 27, top + tabH, selected ? 0xFF8B8B8B : 0xFF4A4A4A);
                g.outline(x, top + 4, 27, tabH - 4, selected ? 0xFFFFFFFF : 0xFF777777);
                ItemStack icon = itemStack(tab.iconItemId());
                if (!icon.isEmpty()) g.item(icon, x + 5, top + 9);
            }
        }

        private void drawTree(GuiGraphicsExtractor g, int x, int y, int w, int h) {
            if (remoteNodes.isEmpty()) {
                g.text(font, "No visible advancements in this tab", x + 12, y + 12, 0xFFB8B8B8, false);
                return;
            }
            Map<String, ObserverAdvancementsScreenPayloads.NodeState> byId = new HashMap<>();
            for (var node : remoteNodes) byId.put(node.id(), node);

            int originX = x + w / 2;
            int originY = y + h / 2;
            for (var node : remoteNodes) {
                if (node.parentId().isEmpty()) continue;
                var parent = byId.get(node.parentId());
                if (parent == null) continue;
                int px = screenX(parent, originX), py = screenY(parent, originY);
                int nx = screenX(node, originX), ny = screenY(node, originY);
                g.fill(Math.min(px, nx) + 8, py + 7, Math.max(px, nx) + 8, py + 9, 0xFF707070);
                g.fill(nx + 7, Math.min(py, ny) + 8, nx + 9, Math.max(py, ny) + 8, 0xFF707070);
            }

            for (var node : remoteNodes) {
                int nx = screenX(node, originX), ny = screenY(node, originY);
                if (nx < x - 20 || nx > x + w || ny < y - 20 || ny > y + h) continue;
                int border = node.done() ? 0xFFFFFFFF : node.progress() > 0.0F ? 0xFFFFC857 : 0xFF696969;
                int fill = node.hidden() && !node.done() ? 0xFF252525 : 0xFF4A4A4A;
                g.fill(nx, ny, nx + 22, ny + 22, fill);
                g.outline(nx, ny, 22, 22, border);
                ItemStack icon = itemStack(node.iconItemId());
                if (!icon.isEmpty()) g.item(icon, nx + 3, ny + 3);
                if (!node.done() && node.progress() > 0.0F) {
                    int fillWidth = Math.max(1, Math.round(20.0F * node.progress()));
                    g.fill(nx + 1, ny + 20, nx + 1 + fillWidth, ny + 21, 0xFFFFC857);
                }
            }

            var root = remoteNodes.stream().filter(n -> n.parentId().isEmpty()).findFirst().orElse(remoteNodes.getFirst());
            String label = root.title() + (root.done() ? " — complete" : "");
            g.text(font, label, x + 8, y + h - 14, 0xFFE0E0E0, false);
        }

        private int screenX(ObserverAdvancementsScreenPayloads.NodeState node, int originX) {
            return originX + (int) Math.round(node.x() * 28.0D + remoteScrollX);
        }

        private int screenY(ObserverAdvancementsScreenPayloads.NodeState node, int originY) {
            return originY + (int) Math.round(node.y() * 27.0D + remoteScrollY);
        }
    }
}
