package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.vanillatweaks.network.ObserverAutomataCopperGolemPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Optional semantic adapter for TotemAutomata's Copper Golem control screen. */
public final class ObserverAutomataCopperGolemScreenClient {
    private static final String SCREEN_CLASS = ObserverAutomataCopperGolemPayloads.SCREEN_CLASS;
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 222;

    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static boolean remoteRunning;
    private static String remoteMode = "";
    private static String remoteActivity = "";
    private static String remoteTab = "bindings";
    private static int remoteSelectedBinding = -1;
    private static int remoteBindingScroll;
    private static boolean remoteBindingDetailVisible;
    private static boolean remoteFilterTextEntryVisible;
    private static boolean remoteFilterTextEntryAllowed;
    private static boolean remoteCacheValueIsTag;
    private static boolean remoteTargetBlocksVisible;
    private static String remoteFuelItemId = "";
    private static int remoteFuelCount;
    private static int remoteFuelTicks;
    private static boolean remoteInfiniteFuel;
    private static String remoteToolItemId = "";
    private static int remoteToolCount;
    private static int remoteToolDamage;
    private static int remoteToolMaxDamage;
    private static String remoteStorageItemId = "";
    private static int remoteStorageCount;
    private static String remoteApiUrl = "";
    private static boolean remoteApiKeyConfigured;
    private static String remoteModel = "";
    private static int remoteLlmActiveCount;
    private static String remoteGatheringPrompt = "";
    private static String remoteBindingPrompt = "";
    private static String remoteCacheValueText = "";
    private static ObserverAutomataCopperGolemPayloads.BindingState remoteSourceContainer;
    private static ObserverAutomataCopperGolemPayloads.GatheringAreaState remoteGatheringArea;
    private static List<String> remoteManualTargets = List.of();
    private static boolean remoteGatheringLlmEnabled;
    private static int remoteGatheringCachedIds;
    private static int remoteGatheringCachedTags;
    private static List<String> remoteGatheringAllowedIds = List.of();
    private static List<String> remoteGatheringDeniedIds = List.of();
    private static List<String> remoteGatheringAllowedTags = List.of();
    private static List<String> remoteGatheringDeniedTags = List.of();
    private static List<ObserverAutomataCopperGolemPayloads.BindingState> remoteBindings = List.of();
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();
    private static long extractedFrames;
    private static boolean suppressMirrorStop;

    private ObserverAutomataCopperGolemScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverAutomataCopperGolemPayloads.CopperGolemRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverAutomataCopperGolemScreenClient::tick);
    }

    static boolean isTargetScreen(Screen screen) {
        return screen != null && SCREEN_CLASS.equals(screen.getClass().getName());
    }

    static boolean hasRemoteScreen() {
        return remoteOpen && ObserverNativeClient.observerSessionActive();
    }

    private static void tick(Minecraft minecraft) {
        if (ObserverNativeClient.targetStateEnabled() && minecraft.player != null && minecraft.level != null
                && ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_AUTOMATA_COPPER_GOLEM)
                && isTargetScreen(minecraft.gui.screen())) {
            tickTarget(minecraft, minecraft.gui.screen());
        } else {
            closeTargetIfNeeded();
        }

        if (!ObserverNativeClient.observerSessionActive()) {
            if (remoteOpen || isMirror(minecraft.gui.screen())) {
                clearRemote();
                closeMirror();
            }
        } else if (remoteOpen) {
            ensureMirror();
        }
    }

    private static void tickTarget(Minecraft minecraft, Screen screen) {
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        long sequence = nextTargetSequence + 1L;
        ObserverAutomataCopperGolemPayloads.CopperGolemState state = captureTargetState(screen, sequence);
        if (state == null) return;
        nextTargetSequence = sequence;
        targetOpen = true;
        lastSnapshotNanos = now;
        ClientPlayNetworking.send(state);
    }

    private static void closeTargetIfNeeded() {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (!ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_AUTOMATA_COPPER_GOLEM)) return;
        ClientPlayNetworking.send(closedTargetState(++nextTargetSequence));
    }

    /** Exact production extractor shared by the target tick and cross-module runtime gate. */
    public static ObserverAutomataCopperGolemPayloads.CopperGolemState captureTargetState(Screen screen, long sequence) {
        if (!isTargetScreen(screen)) throw new IllegalArgumentException("Expected TotemAutomata CopperGolemMenuScreen");
        Object snapshot = currentSnapshot(screen);
        if (snapshot == null) return null;
        Object ui = fieldValue(screen, "ui");
        String tab = lower(string(invoke(ui, "tab")));
        int selected = integer(invoke(ui, "selected"));
        int scroll = integer(invoke(ui, "scroll"));
        String apiUrl = editValue(screen, "apiUrlField");
        boolean apiKeyConfigured = !editValue(screen, "apiKeyField").isBlank()
                || !string(invoke(snapshot, "llmApiKey")).isBlank();
        String model = editValue(screen, "modelField");
        String gatheringPrompt = editValue(screen, "gatheringPromptField");
        String bindingPrompt = editValue(screen, "bindingPromptField");
        String cacheValue = editValue(screen, "cacheValueField");

        return new ObserverAutomataCopperGolemPayloads.CopperGolemState(
                ObserverAutomataCopperGolemPayloads.PROTOCOL_VERSION,
                sequence,
                true,
                ObserverNativeScreenPayloads.FAMILY_AUTOMATA_COPPER_GOLEM,
                SCREEN_CLASS,
                ObserverAutomataCopperGolemPayloads.SCREEN_TITLE,
                bool(invoke(snapshot, "running")),
                string(invoke(snapshot, "mode")),
                string(invoke(snapshot, "activity")),
                tab.isBlank() ? "bindings" : tab,
                selected,
                scroll,
                booleanField(screen, "bindingDetailVisible"),
                booleanField(screen, "filterTextEntryVisible"),
                booleanField(screen, "filterTextEntryAllowed"),
                booleanField(screen, "cacheValueIsTag"),
                booleanField(screen, "targetBlocksVisible"),
                string(invoke(snapshot, "fuelItemId")),
                integer(invoke(snapshot, "fuelCount")),
                integer(invoke(snapshot, "fuelTicks")),
                bool(invoke(snapshot, "infiniteFuel")),
                string(invoke(snapshot, "gatheringToolItemId")),
                integer(invoke(snapshot, "gatheringToolCount")),
                integer(invoke(snapshot, "gatheringToolDamage")),
                integer(invoke(snapshot, "gatheringToolMaxDamage")),
                string(invoke(snapshot, "gatheringStorageItemId")),
                integer(invoke(snapshot, "gatheringStorageCount")),
                ObserverAutomataCopperGolemPayloads.configuredToken(
                        apiUrl.isBlank() ? string(invoke(snapshot, "llmApiUrl")) : apiUrl),
                apiKeyConfigured,
                ObserverAutomataCopperGolemPayloads.configuredToken(
                        model.isBlank() ? string(invoke(snapshot, "llmModel")) : model),
                integer(invoke(snapshot, "llmActiveCount")),
                ObserverAutomataCopperGolemPayloads.configuredToken(
                        gatheringPrompt.isBlank() ? string(invoke(snapshot, "gatheringLlmPrompt")) : gatheringPrompt),
                ObserverAutomataCopperGolemPayloads.configuredToken(bindingPrompt),
                ObserverAutomataCopperGolemPayloads.validToken(cacheValue),
                binding(invoke(snapshot, "sourceContainer")),
                area(invoke(snapshot, "gatheringArea")),
                strings(invoke(snapshot, "gatheringManualTargets")),
                bool(invoke(snapshot, "gatheringLlmEnabled")),
                integer(invoke(snapshot, "gatheringLlmCachedBlockIds")),
                integer(invoke(snapshot, "gatheringLlmCachedTags")),
                strings(invoke(snapshot, "gatheringLlmAllowedBlockIds")),
                strings(invoke(snapshot, "gatheringLlmDeniedBlockIds")),
                strings(invoke(snapshot, "gatheringLlmAllowedTags")),
                strings(invoke(snapshot, "gatheringLlmDeniedTags")),
                bindings(invoke(snapshot, "bindings")),
                captureSlots(screen));
    }

    public static ObserverAutomataCopperGolemPayloads.CopperGolemState closedTargetState(long sequence) {
        return new ObserverAutomataCopperGolemPayloads.CopperGolemState(
                ObserverAutomataCopperGolemPayloads.PROTOCOL_VERSION, sequence, false,
                ObserverNativeScreenPayloads.FAMILY_AUTOMATA_COPPER_GOLEM, "", "", false, "", "", "bindings",
                -1, 0, false, false, false, false, false, "", 0, 0, false, "", 0, 0, 0, "", 0,
                "", false, "", 0, "", "", "", null, null, List.of(), false, 0, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static Object currentSnapshot(Screen screen) {
        try {
            Object lifecycle = fieldValue(screen, "lifecycle");
            Object session = invoke(lifecycle, "session");
            Object controller = invoke(session, "controller");
            Object value = invoke(controller, "snapshot");
            return value instanceof Optional<?> optional ? optional.orElse(null) : null;
        } catch (RuntimeException error) {
            TotemVanillaTweaks.LOGGER.debug("Unable to read Automata Copper Golem snapshot", error);
            return null;
        }
    }

    private static List<ObserverNativeScreenPayloads.SlotState> captureSlots(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return List.of();
        }
        List<ObserverNativeScreenPayloads.SlotState> result = new ArrayList<>();
        int limit = Math.min(container.getMenu().slots.size(), ObserverNativeScreenPayloads.MAX_SLOTS);
        for (int i = 0; i < limit; i++) {
            Slot slot = container.getMenu().slots.get(i);
            ItemStack stack = slot.getItem();
            result.add(new ObserverNativeScreenPayloads.SlotState(i, slot.x, slot.y,
                    stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    stack.isEmpty() ? 0 : stack.getCount(), stack.isEmpty() ? 0 : stack.getDamageValue()));
        }
        return List.copyOf(result);
    }

    private static ObserverAutomataCopperGolemPayloads.BindingState binding(Object value) {
        if (value == null) return null;
        return new ObserverAutomataCopperGolemPayloads.BindingState(
                string(invoke(value, "dimension")), integer(invoke(value, "x")), integer(invoke(value, "y")),
                integer(invoke(value, "z")), string(invoke(value, "blockId")), string(invoke(value, "itemId")),
                bool(invoke(value, "loaded")), bool(invoke(value, "available")), bool(invoke(value, "llmEnabled")),
                ObserverAutomataCopperGolemPayloads.configuredToken(string(invoke(value, "llmPrompt"))),
                integer(invoke(value, "llmCachedItemIds")),
                integer(invoke(value, "llmCachedTags")), strings(invoke(value, "llmAllowedItemIds")),
                strings(invoke(value, "llmDeniedItemIds")), strings(invoke(value, "llmAllowedTags")),
                strings(invoke(value, "llmDeniedTags")));
    }

    private static List<ObserverAutomataCopperGolemPayloads.BindingState> bindings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().limit(ObserverAutomataCopperGolemPayloads.MAX_BINDINGS)
                .map(ObserverAutomataCopperGolemScreenClient::binding).toList();
    }

    private static ObserverAutomataCopperGolemPayloads.GatheringAreaState area(Object value) {
        if (value == null) return null;
        return new ObserverAutomataCopperGolemPayloads.GatheringAreaState(
                string(invoke(value, "dimension")), bool(invoke(value, "hasCornerA")),
                integer(invoke(value, "cornerAX")), integer(invoke(value, "cornerAY")), integer(invoke(value, "cornerAZ")),
                bool(invoke(value, "hasCornerB")), integer(invoke(value, "cornerBX")), integer(invoke(value, "cornerBY")),
                integer(invoke(value, "cornerBZ")));
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(v -> v != null).map(String::valueOf).filter(v -> !v.isBlank())
                .limit(ObserverAutomataCopperGolemPayloads.MAX_VALUES).toList();
    }

    private static void acceptRelay(ObserverAutomataCopperGolemPayloads.CopperGolemRelay p) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_AUTOMATA_COPPER_GOLEM)
                || targetId == null || !targetId.equals(p.targetId())
                || p.protocolVersion() != ObserverAutomataCopperGolemPayloads.PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_AUTOMATA_COPPER_GOLEM.equals(p.familyId())
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverNativeScreenPayloads.FAMILY_AUTOMATA_COPPER_GOLEM,
                        p.targetId(), p.sequence())) return;
        if (!p.open()) {
            clearRemote();
            closeMirror();
            return;
        }
        remoteOpen = true;
        remoteTitle = p.title();
        remoteRunning = p.running(); remoteMode = p.mode(); remoteActivity = p.activity(); remoteTab = p.tab();
        remoteSelectedBinding = p.selectedBinding(); remoteBindingScroll = p.bindingScroll();
        remoteBindingDetailVisible = p.bindingDetailVisible(); remoteFilterTextEntryVisible = p.filterTextEntryVisible();
        remoteFilterTextEntryAllowed = p.filterTextEntryAllowed(); remoteCacheValueIsTag = p.cacheValueIsTag();
        remoteTargetBlocksVisible = p.targetBlocksVisible();
        remoteFuelItemId = p.fuelItemId(); remoteFuelCount = p.fuelCount(); remoteFuelTicks = p.fuelTicks(); remoteInfiniteFuel = p.infiniteFuel();
        remoteToolItemId = p.toolItemId(); remoteToolCount = p.toolCount(); remoteToolDamage = p.toolDamage(); remoteToolMaxDamage = p.toolMaxDamage();
        remoteStorageItemId = p.storageItemId(); remoteStorageCount = p.storageCount();
        remoteApiUrl = p.editorApiUrl(); remoteApiKeyConfigured = p.apiKeyConfigured(); remoteModel = p.editorModel(); remoteLlmActiveCount = p.llmActiveCount();
        remoteGatheringPrompt = p.editorGatheringPrompt(); remoteBindingPrompt = p.editorBindingPrompt(); remoteCacheValueText = p.cacheValueText();
        remoteSourceContainer = p.sourceContainer(); remoteGatheringArea = p.gatheringArea(); remoteManualTargets = List.copyOf(p.gatheringManualTargets());
        remoteGatheringLlmEnabled = p.gatheringLlmEnabled(); remoteGatheringCachedIds = p.gatheringLlmCachedBlockIds(); remoteGatheringCachedTags = p.gatheringLlmCachedTags();
        remoteGatheringAllowedIds = List.copyOf(p.gatheringLlmAllowedBlockIds()); remoteGatheringDeniedIds = List.copyOf(p.gatheringLlmDeniedBlockIds());
        remoteGatheringAllowedTags = List.copyOf(p.gatheringLlmAllowedTags()); remoteGatheringDeniedTags = List.copyOf(p.gatheringLlmDeniedTags());
        remoteBindings = List.copyOf(p.bindings()); remoteSlots = List.copyOf(p.slots());
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        ensureMirror();
    }

    private static void clearRemote() {
        remoteOpen = false; remoteTitle = ""; remoteRunning = false; remoteMode = ""; remoteActivity = ""; remoteTab = "bindings";
        remoteSelectedBinding = -1; remoteBindingScroll = 0; remoteBindingDetailVisible = false; remoteFilterTextEntryVisible = false;
        remoteFilterTextEntryAllowed = false; remoteCacheValueIsTag = false; remoteTargetBlocksVisible = false;
        remoteFuelItemId = ""; remoteFuelCount = 0; remoteFuelTicks = 0; remoteInfiniteFuel = false;
        remoteToolItemId = ""; remoteToolCount = 0; remoteToolDamage = 0; remoteToolMaxDamage = 0;
        remoteStorageItemId = ""; remoteStorageCount = 0; remoteApiUrl = ""; remoteApiKeyConfigured = false; remoteModel = ""; remoteLlmActiveCount = 0;
        remoteGatheringPrompt = ""; remoteBindingPrompt = ""; remoteCacheValueText = ""; remoteSourceContainer = null; remoteGatheringArea = null;
        remoteManualTargets = List.of(); remoteGatheringLlmEnabled = false; remoteGatheringCachedIds = 0; remoteGatheringCachedTags = 0;
        remoteGatheringAllowedIds = List.of(); remoteGatheringDeniedIds = List.of(); remoteGatheringAllowedTags = List.of(); remoteGatheringDeniedTags = List.of();
        remoteBindings = List.of(); remoteSlots = List.of();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeAutomataCopperGolemMirrorScreen)) replaceScreen(new NativeAutomataCopperGolemMirrorScreen());
    }

    private static boolean isMirror(Screen screen) { return screen instanceof NativeAutomataCopperGolemMirrorScreen; }
    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isMirror(minecraft.gui.screen())) return;
        replaceScreen(null);
    }
    private static void replaceScreen(Screen screen) {
        suppressMirrorStop = true;
        try { Minecraft.getInstance().setScreenAndShow(screen); }
        finally { suppressMirrorStop = false; }
    }

    private static Object fieldValue(Object owner, String name) {
        if (owner == null) return null;
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException error) {
                throw new RuntimeException(error);
            }
        }
        throw new IllegalStateException("Missing field " + name + " on " + owner.getClass().getName());
    }

    private static boolean booleanField(Object owner, String name) { Object v = fieldValue(owner, name); return v instanceof Boolean b && b; }
    private static String editValue(Object owner, String name) { Object value = fieldValue(owner, name); return value instanceof EditBox edit ? edit.getValue() : ""; }

    private static Object invoke(Object owner, String name) {
        if (owner == null) return null;
        try {
            Method method = owner.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(owner);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Missing method " + name + " on " + owner.getClass().getName(), error);
        }
    }
    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
    private static int integer(Object value) { return value instanceof Number number ? number.intValue() : 0; }
    private static boolean bool(Object value) { return value instanceof Boolean b && b; }

    private static ItemStack item(String id, int count, int damage) {
        if (id == null || id.isBlank() || count <= 0) return ItemStack.EMPTY;
        try {
            Item value = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
            if (value == null) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(value, count);
            if (damage > 0 && stack.isDamageableItem()) stack.setDamageValue(damage);
            return stack;
        } catch (RuntimeException ignored) { return ItemStack.EMPTY; }
    }

    private static void drawItemSummary(GuiGraphicsExtractor g, int x, int y, String label, String id, int count, int damage) {
        Minecraft mc = Minecraft.getInstance();
        g.text(mc.font, label, x, y, 0xFFBDBDBD, false);
        ItemStack stack = item(id, count, damage);
        if (!stack.isEmpty()) {
            g.item(stack, x + 48, y - 4);
            g.itemDecorations(mc.font, stack, x + 48, y - 4);
        }
    }

    private static void drawSlots(GuiGraphicsExtractor g, int left, int top) {
        Minecraft mc = Minecraft.getInstance();
        for (var slot : remoteSlots) {
            int x = left + slot.x(), y = top + slot.y();
            if (x < left - 32 || x > left + PANEL_WIDTH + 32 || y < top - 32 || y > top + PANEL_HEIGHT + 32) continue;
            g.fill(x, y, x + 18, y + 18, 0xFF555555);
            g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF171717);
            ItemStack stack = item(slot.itemId(), slot.count(), slot.damage());
            if (!stack.isEmpty()) { g.item(stack, x + 1, y + 1); g.itemDecorations(mc.font, stack, x + 1, y + 1); }
        }
    }

    private static final class NativeAutomataCopperGolemMirrorScreen extends ObserverMirrorScreen {
        private NativeAutomataCopperGolemMirrorScreen() { super(Component.literal("Observer Copper Golem")); }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, width, height, 0xA0000000);
            int left = (width - PANEL_WIDTH) / 2;
            int top = (height - PANEL_HEIGHT) / 2;
            g.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFFF1F1F1);
            g.fill(left, top, left + PANEL_WIDTH, top + 24, 0xFF3B3B3B);
            String title = remoteTitle.isBlank() ? "Copper Golem" : remoteTitle;
            g.text(minecraft.font, title, left + 7, top + 7, 0xFFFFFFFF, true);
            String state = (remoteRunning ? "RUN " : "STOP ") + remoteMode + " / " + remoteActivity;
            g.text(minecraft.font, state, left + 7, top + 27, remoteRunning ? 0xFF287A34 : 0xFF9B3030, false);
            g.text(minecraft.font, "Tab: " + remoteTab, left + 7, top + 39, 0xFF404040, false);

            if ("llm".equals(remoteTab)) drawLlm(g, left, top);
            else if ("gathering".equalsIgnoreCase(remoteMode)) drawGathering(g, left, top);
            else drawBindings(g, left, top);

            drawSlots(g, left, top);
            extractedFrames++;
        }

        private void drawLlm(GuiGraphicsExtractor g, int left, int top) {
            g.text(minecraft.font, "API: " + tokenLabel(remoteApiUrl), left + 7, top + 54, 0xFF404040, false);
            g.text(minecraft.font, "Key: " + (remoteApiKeyConfigured ? "configured" : "empty"), left + 7, top + 66, 0xFF404040, false);
            g.text(minecraft.font, "Model: " + tokenLabel(remoteModel), left + 7, top + 78, 0xFF404040, false);
            g.text(minecraft.font, "LLM active: " + remoteLlmActiveCount, left + 7, top + 90, 0xFF404040, false);
            g.text(minecraft.font, "Prompt: " + tokenLabel(remoteGatheringPrompt), left + 7, top + 104, 0xFF505050, false);
        }

        private void drawBindings(GuiGraphicsExtractor g, int left, int top) {
            String source = remoteSourceContainer == null ? "none" : trim(remoteSourceContainer.blockId(), 20);
            g.text(minecraft.font, "Source: " + source, left + 7, top + 54, 0xFF404040, false);
            g.text(minecraft.font, "Bindings " + remoteBindings.size() + "  scroll " + remoteBindingScroll,
                    left + 7, top + 66, 0xFF404040, false);
            int start = Math.min(Math.max(0, remoteBindingScroll), Math.max(0, remoteBindings.size() - 1));
            int shown = 0;
            for (int i = start; i < remoteBindings.size() && shown < 5; i++, shown++) {
                var binding = remoteBindings.get(i);
                String marker = i == remoteSelectedBinding ? ">" : " ";
                String status = binding.available() ? "ok" : binding.loaded() ? "blocked" : "unloaded";
                g.text(minecraft.font, marker + " " + trim(binding.blockId(), 16) + " " + status,
                        left + 9, top + 80 + shown * 12, i == remoteSelectedBinding ? 0xFF1565C0 : 0xFF505050, false);
            }
            if (remoteBindingDetailVisible && remoteSelectedBinding >= 0 && remoteSelectedBinding < remoteBindings.size()) {
                var selected = remoteBindings.get(remoteSelectedBinding);
                g.text(minecraft.font, "LLM " + (selected.llmEnabled() ? "on" : "off") + ": "
                                + tokenLabel(remoteBindingPrompt.isBlank() ? selected.llmPrompt() : remoteBindingPrompt),
                        left + 7, top + 145, 0xFF505050, false);
                if (remoteFilterTextEntryVisible) {
                    g.text(minecraft.font, (remoteFilterTextEntryAllowed ? "Allow " : "Deny ")
                                    + (remoteCacheValueIsTag ? "tag " : "item ") + tokenLabel(remoteCacheValueText),
                            left + 7, top + 157, 0xFF6A1B9A, false);
                }
            }
        }

        private void drawGathering(GuiGraphicsExtractor g, int left, int top) {
            String area = "not set";
            if (remoteGatheringArea != null) {
                area = remoteGatheringArea.hasCornerA() && remoteGatheringArea.hasCornerB()
                        ? "A+B set" : remoteGatheringArea.hasCornerA() ? "A set" : "not set";
            }
            g.text(minecraft.font, "Area: " + area, left + 7, top + 54, 0xFF404040, false);
            g.text(minecraft.font, "Targets: " + remoteManualTargets.size() + (remoteTargetBlocksVisible ? " (open)" : ""),
                    left + 7, top + 66, 0xFF404040, false);
            g.text(minecraft.font, "LLM: " + (remoteGatheringLlmEnabled ? "on" : "off") + " cache "
                    + remoteGatheringCachedIds + "+" + remoteGatheringCachedTags, left + 7, top + 78, 0xFF404040, false);
            g.text(minecraft.font, "Prompt: " + tokenLabel(remoteGatheringPrompt), left + 7, top + 90, 0xFF505050, false);
            drawItemSummary(g, left + 7, top + 110, "Tool", remoteToolItemId, remoteToolCount, remoteToolDamage);
            drawItemSummary(g, left + 7, top + 130, "Store", remoteStorageItemId, remoteStorageCount, 0);
            g.text(minecraft.font, "Fuel: " + (remoteInfiniteFuel ? "infinite" : remoteFuelCount + " / " + remoteFuelTicks + "t"),
                    left + 7, top + 151, 0xFF505050, false);
        }

        private String trim(String value, int max) {
            if (value == null) return "";
            return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
        }

        private String tokenLabel(String value) {
            if (ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED.equals(value)
                    || ObserverAutomataCopperGolemPayloads.TOKEN_VALID.equals(value)) return value;
            return "empty";
        }

        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) ClientPlayNetworking.send(new ObserverPayloads.Stop());
            super.onClose();
        }
    }
}
