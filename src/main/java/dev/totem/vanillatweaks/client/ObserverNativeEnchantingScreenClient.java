package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.vanillatweaks.network.ObserverEnchantingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Target adapter and Observer-side reconstruction for the vanilla enchanting table. */
public final class ObserverNativeEnchantingScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;

    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static boolean remoteOpen;
    private static String remoteScreenClass = "";
    private static String remoteTitle = "";
    private static int remotePlayerLevel;
    private static int remoteLapisCount;
    private static List<ObserverEnchantingScreenPayloads.OptionState> remoteOptions = List.of();
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();
    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverNativeEnchantingScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverEnchantingScreenPayloads.EnchantingRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload))
        );
        ClientTickEvents.END_CLIENT_TICK.register(ObserverNativeEnchantingScreenClient::tick);
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
        if (remoteOpen) ensureMirror();
    }

    private static void tickTarget(Minecraft minecraft) {
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_ENCHANTING);
        Screen screen = minecraft.gui.screen();
        if (!supported || !(screen instanceof EnchantmentScreen enchantmentScreen)) {
            closeTarget(supported);
            return;
        }

        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        targetOpen = true;
        lastSnapshotNanos = now;

        ClientPlayNetworking.send(captureTargetState(minecraft, enchantmentScreen, ++nextTargetSequence));
    }

    static ObserverEnchantingScreenPayloads.EnchantingState captureTargetState(
            Minecraft minecraft,
            EnchantmentScreen enchantmentScreen,
            long sequence
    ) {
        EnchantmentMenu menu = enchantmentScreen.getMenu();
        List<ObserverEnchantingScreenPayloads.OptionState> options = new ArrayList<>(3);
        int lapis = menu.getSlot(1).getItem().getCount();
        int playerLevel = minecraft.player.experienceLevel;
        for (int i = 0; i < 3; i++) {
            int cost = menu.costs[i];
            int clue = menu.enchantClue[i];
            int clueLevel = menu.levelClue[i];
            boolean affordable = cost > 0 && playerLevel >= cost && lapis >= i + 1;
            options.add(new ObserverEnchantingScreenPayloads.OptionState(i, cost, clue, clueLevel, affordable));
        }

        return new ObserverEnchantingScreenPayloads.EnchantingState(
                ObserverEnchantingScreenPayloads.PROTOCOL_VERSION,
                sequence,
                true,
                ObserverNativeScreenPayloads.FAMILY_ENCHANTING,
                enchantmentScreen.getClass().getName(),
                enchantmentScreen.getTitle() == null ? "" : enchantmentScreen.getTitle().getString(),
                playerLevel,
                lapis,
                List.copyOf(options),
                captureSlots(menu)
        );
    }

    private static void closeTarget(boolean canSend) {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) {
            ClientPlayNetworking.send(new ObserverEnchantingScreenPayloads.EnchantingState(
                    ObserverEnchantingScreenPayloads.PROTOCOL_VERSION,
                    ++nextTargetSequence,
                    false,
                    ObserverNativeScreenPayloads.FAMILY_ENCHANTING,
                    "", "", 0, 0, List.of(), List.of()
            ));
        }
    }

    private static List<ObserverNativeScreenPayloads.SlotState> captureSlots(EnchantmentMenu menu) {
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>();
        int limit = Math.min(menu.slots.size(), ObserverNativeScreenPayloads.MAX_SLOTS);
        for (int i = 0; i < limit; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            String itemId = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            slots.add(new ObserverNativeScreenPayloads.SlotState(
                    i, slot.x, slot.y, itemId,
                    stack.isEmpty() ? 0 : stack.getCount(),
                    stack.isEmpty() ? 0 : stack.getDamageValue()
            ));
        }
        return List.copyOf(slots);
    }

    private static void acceptRelay(ObserverEnchantingScreenPayloads.EnchantingRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_ENCHANTING)
                || targetId == null
                || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverEnchantingScreenPayloads.PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_ENCHANTING.equals(payload.familyId())
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverNativeScreenPayloads.FAMILY_ENCHANTING,
                        payload.targetId(), payload.sequence())) return;
        if (!payload.open()) {
            clearRemote();
            closeMirror();
            return;
        }

        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteScreenClass = payload.screenClass();
        remoteTitle = payload.title();
        remotePlayerLevel = payload.playerLevel();
        remoteLapisCount = payload.lapisCount();
        remoteOptions = List.copyOf(payload.options());
        remoteSlots = List.copyOf(payload.slots());
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeEnchantingMirrorScreen)) {
            suppressMirrorStop = true;
            try { minecraft.setScreenAndShow(new NativeEnchantingMirrorScreen()); }
            finally { suppressMirrorStop = false; }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeEnchantingMirrorScreen)) return;
        suppressMirrorStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressMirrorStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteScreenClass = "";
        remoteTitle = "";
        remotePlayerLevel = 0;
        remoteLapisCount = 0;
        remoteOptions = List.of();
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
        } catch (RuntimeException error) {
            TotemVanillaTweaks.LOGGER.debug("Ignoring invalid enchanting Observer item id {}", slot.itemId());
            return ItemStack.EMPTY;
        }
    }

    private static final class NativeEnchantingMirrorScreen extends ObserverMirrorScreen {
        private NativeEnchantingMirrorScreen() { super(Component.literal("Observer Enchanting")); }

        @Override
        public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) {
                ClientPlayNetworking.send(new ObserverPayloads.Stop());
            }
            super.onClose();
        }

        @Override
        public boolean isPauseScreen() { return false; }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0x90000000);
            int panelWidth = 220;
            int panelHeight = 150;
            int left = (width - panelWidth) / 2;
            int top = (height - panelHeight) / 2;
            graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xEE202020);
            String title = remoteTitle.isBlank() ? remoteScreenClass : remoteTitle;
            graphics.text(font, title, left + 10, top + 9, 0xFFFFFFFF, true);
            graphics.text(font, "Level " + remotePlayerLevel + "  Lapis " + remoteLapisCount,
                    left + 10, top + 24, 0xFFBDBDBD, false);

            int optionTop = top + 42;
            for (ObserverEnchantingScreenPayloads.OptionState option : remoteOptions) {
                int y = optionTop + option.index() * 28;
                graphics.fill(left + 52, y, left + panelWidth - 10, y + 23,
                        option.affordable() ? 0xFF3A4A32 : 0xFF3A3030);
                String cost = option.cost() <= 0 ? "Unavailable" : "Cost " + option.cost() + "  Lapis " + (option.index() + 1);
                graphics.text(font, cost, left + 59, y + 4, 0xFFFFFFFF, false);
                String clue = option.enchantClue() < 0 ? "No clue" : "Clue #" + option.enchantClue() + " Lv " + option.levelClue();
                graphics.text(font, clue, left + 59, y + 13, 0xFFAAAAAA, false);
            }

            for (ObserverNativeScreenPayloads.SlotState slot : remoteSlots) {
                if (slot.index() > 1) continue;
                int x = left + 18;
                int y = top + 49 + slot.index() * 34;
                graphics.fill(x, y, x + 20, y + 20, 0xFF555555);
                graphics.fill(x + 1, y + 1, x + 19, y + 19, 0xFF171717);
                ItemStack stack = itemStack(slot);
                if (!stack.isEmpty()) {
                    graphics.item(stack, x + 2, y + 2);
                    graphics.itemDecorations(font, stack, x + 2, y + 2);
                }
            }
            extractedFrames++;
        }
    }
}
