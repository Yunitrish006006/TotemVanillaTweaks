package dev.totem.vanillatweaks.client;

import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import dev.totem.vanillatweaks.mixin.client.AdvancementTabAccessor;
import dev.totem.vanillatweaks.mixin.client.AdvancementsScreenAccessor;
import dev.totem.vanillatweaks.mixin.client.ClientAdvancementsAccessor;
import dev.totem.vanillatweaks.network.ObserverAdvancementsScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
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
import net.minecraft.client.telemetry.TelemetryEventSender;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Framebuffer-free semantic adapter and local reconstruction for vanilla Advancements. */
public final class ObserverAdvancementsScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static String remoteSelectedRootId = "";
    private static double remoteScrollX;
    private static double remoteScrollY;
    private static List<ObserverAdvancementsScreenPayloads.TabState> remoteTabs = List.of();
    private static List<ObserverAdvancementsScreenPayloads.NodeState> remoteNodes = List.of();
    private static boolean suppressObserverScreenStop;
    private static long extractedFrames;
    private static ClientAdvancements remoteAdvancements;

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
            if (remoteOpen || minecraft.gui.screen() instanceof ObserverAdvancementsScreen) {
                clearRemote();
                closeObserverScreen();
            }
        } else if (remoteOpen) {
            ensureObserverScreen();
        }
    }

    private static void tickTarget(Minecraft minecraft, AdvancementsScreen screen) {
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        ObserverAdvancementsScreenPayloads.AdvancementsState state =
                captureTargetState(minecraft, screen, ++nextTargetSequence);
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

    private static ObserverAdvancementsScreenPayloads.AdvancementsState captureTargetState(
            Minecraft minecraft, AdvancementsScreen screen, long sequence) {
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
                ObserverAdvancementsScreenPayloads.PROTOCOL_VERSION, sequence, true,
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
                    root.holder().id().toString(), info.getTitle().getString(), itemId(info.getIcon()),
                    info.getBackground().map(ClientAsset.ResourceTexture::id).map(Identifier::toString).orElse("")));
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
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverAdvancementsScreenPayloads.FAMILY_ID, p.targetId(), p.sequence())) return;
        if (!p.open()) { clearRemote(); closeObserverScreen(); return; }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = p.title();
        remoteSelectedRootId = p.selectedRootId();
        remoteScrollX = p.scrollX();
        remoteScrollY = p.scrollY();
        remoteTabs = List.copyOf(p.tabs());
        remoteNodes = List.copyOf(p.nodes());
        ensureObserverScreen();
    }

    private static void ensureObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (remoteAdvancements == null) {
            remoteAdvancements = new ClientAdvancements(minecraft, new WorldSessionTelemetryManager(
                    TelemetryEventSender.DISABLED, false, Duration.ZERO,
                    "totem-observer", UUID.randomUUID()));
        }
        applyRemoteAdvancements();
        if (!(minecraft.gui.screen() instanceof ObserverAdvancementsScreen)) {
            suppressObserverScreenStop = true;
            try { minecraft.setScreenAndShow(new ObserverAdvancementsScreen(remoteAdvancements)); }
            finally { suppressObserverScreenStop = false; }
        }
        applyRemoteViewport((ObserverAdvancementsScreen) minecraft.gui.screen());
    }

    private static void closeObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ObserverAdvancementsScreen)) return;
        suppressObserverScreenStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressObserverScreenStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
        remoteSelectedRootId = "";
        remoteScrollX = 0.0D;
        remoteScrollY = 0.0D;
        remoteTabs = List.of();
        remoteNodes = List.of();
        remoteAdvancements = null;
    }

    private static void applyRemoteAdvancements() {
        LinkedHashMap<Identifier, ObserverAdvancementsScreenPayloads.NodeState> nodesById = new LinkedHashMap<>();
        for (ObserverAdvancementsScreenPayloads.NodeState node : remoteNodes) {
            try { nodesById.put(Identifier.parse(node.id()), node); }
            catch (RuntimeException ignored) { }
        }

        LinkedHashMap<Identifier, AdvancementHolder> holders = new LinkedHashMap<>();
        LinkedHashMap<Identifier, AdvancementProgress> progress = new LinkedHashMap<>();
        for (ObserverAdvancementsScreenPayloads.TabState tab : remoteTabs) {
            Identifier id;
            try { id = Identifier.parse(tab.rootId()); }
            catch (RuntimeException ignored) { continue; }
            ObserverAdvancementsScreenPayloads.NodeState node = nodesById.remove(id);
            holders.put(id, holder(id, node, tab.title(), tab.iconItemId(), Optional.empty(),
                    resourceTexture(tab.backgroundTextureId())));
            if (node != null) progress.put(id, progress(node));
        }

        Set<Identifier> pending = new LinkedHashSet<>(nodesById.keySet());
        int guard = pending.size() + 1;
        while (!pending.isEmpty() && guard-- > 0) {
            boolean advanced = false;
            for (Identifier id : List.copyOf(pending)) {
                ObserverAdvancementsScreenPayloads.NodeState node = nodesById.get(id);
                Optional<Identifier> parent = identifier(node.parentId());
                if (parent.isPresent() && !holders.containsKey(parent.get()) && pending.contains(parent.get())) continue;
                holders.put(id, holder(id, node, node.title(), node.iconItemId(), parent, Optional.empty()));
                progress.put(id, progress(node));
                pending.remove(id);
                advanced = true;
            }
            if (!advanced) break;
        }
        for (Identifier id : pending) {
            ObserverAdvancementsScreenPayloads.NodeState node = nodesById.get(id);
            holders.put(id, holder(id, node, node.title(), node.iconItemId(), Optional.empty(), Optional.empty()));
            progress.put(id, progress(node));
        }

        remoteAdvancements.update(new ClientboundUpdateAdvancementsPacket(
                true, holders.values(), Set.of(), progress, false));
        identifier(remoteSelectedRootId).map(remoteAdvancements::get)
                .ifPresent(holder -> remoteAdvancements.setSelectedTab(holder, false));
    }

    private static AdvancementHolder holder(
            Identifier id,
            ObserverAdvancementsScreenPayloads.NodeState node,
            String fallbackTitle,
            String fallbackIcon,
            Optional<Identifier> parent,
            Optional<ClientAsset.ResourceTexture> background
    ) {
        ItemStack iconStack = itemStack(node == null ? fallbackIcon : node.iconItemId());
        Item icon = iconStack.isEmpty() ? Items.BOOK : iconStack.getItem();
        AdvancementType type = node == null ? AdvancementType.TASK : advancementType(node.type());
        DisplayInfo display = new DisplayInfo(
                new ItemStackTemplate(icon),
                Component.literal(node == null ? fallbackTitle : node.title()),
                Component.literal(node == null ? "" : node.description()),
                background, type, false, false, node != null && node.hidden());
        if (node != null) display.setLocation(node.x(), node.y());
        Advancement advancement = new Advancement(
                parent,
                Optional.of(display),
                AdvancementRewards.EMPTY,
                Map.of(),
                requirements(node),
                false
        );
        return new AdvancementHolder(id, advancement);
    }

    private static AdvancementRequirements requirements(ObserverAdvancementsScreenPayloads.NodeState node) {
        if (node == null) return AdvancementRequirements.EMPTY;
        List<String> names = new ArrayList<>(100);
        for (int index = 0; index < 100; index++) names.add("observer_" + index);
        return AdvancementRequirements.allOf(names);
    }

    private static AdvancementProgress progress(ObserverAdvancementsScreenPayloads.NodeState node) {
        AdvancementProgress progress = new AdvancementProgress();
        AdvancementRequirements requirements = requirements(node);
        progress.update(requirements);
        int completed = node.done() ? 100 : Math.max(0, Math.min(99, Math.round(node.progress() * 100.0F)));
        for (int index = 0; index < completed; index++) progress.grantProgress("observer_" + index);
        return progress;
    }

    private static AdvancementType advancementType(String type) {
        return switch (type) {
            case "challenge" -> AdvancementType.CHALLENGE;
            case "goal" -> AdvancementType.GOAL;
            default -> AdvancementType.TASK;
        };
    }

    private static Optional<Identifier> identifier(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try { return Optional.of(Identifier.parse(value)); }
        catch (RuntimeException ignored) { return Optional.empty(); }
    }

    private static Optional<ClientAsset.ResourceTexture> resourceTexture(String value) {
        return identifier(value).map(ClientAsset.ResourceTexture::new);
    }

    private static void applyRemoteViewport(ObserverAdvancementsScreen screen) {
        AdvancementTab selected = ((AdvancementsScreenAccessor) (Object) screen).totem$getSelectedTab();
        if (selected == null) return;
        AdvancementTabAccessor accessor = (AdvancementTabAccessor) (Object) selected;
        accessor.totem$setScrollX(remoteScrollX);
        accessor.totem$setScrollY(remoteScrollY);
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

    private static final class ObserverAdvancementsScreen extends AdvancementsScreen implements ObserverReadOnlyScreen {
        private ObserverAdvancementsScreen(ClientAdvancements advancements) { super(advancements); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() {
            if (!suppressObserverScreenStop) ObserverVanillaScreenSupport.stopObserving();
        }
        @Override public void removed() {
            ((AdvancementsScreenAccessor) (Object) this).totem$getAdvancements().setListener(null);
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            super.extractRenderState(g, mouseX, mouseY, partialTick);
            extractedFrames++;
        }
    }

}
