package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.vanillatweaks.mixin.client.AnvilScreenAccessor;
import dev.totem.vanillatweaks.network.ObserverAnvilScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Target adapter and Observer-side reconstruction for vanilla anvils. */
public final class ObserverNativeAnvilScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;

    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static long lastRemoteSequence = -1L;
    private static boolean remoteOpen;
    private static String remoteScreenClass = "";
    private static String remoteTitle = "";
    private static String remoteItemName = "";
    private static int remoteLevelCost;
    private static boolean remoteTooExpensive;
    private static boolean remoteResultAvailable;
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();

    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverNativeAnvilScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverAnvilScreenPayloads.AnvilRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload))
        );
        ClientTickEvents.END_CLIENT_TICK.register(ObserverNativeAnvilScreenClient::tick);
    }

    static boolean isNativeMirrorScreen(Screen screen) {
        return screen instanceof NativeAnvilMirrorScreen;
    }

    static boolean hasStructuredRemoteScreen() {
        return remoteOpen && ObserverNativeClient.observerSessionActive();
    }

    static long extractedFrames() {
        return extractedFrames;
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
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_ANVIL);
        Screen screen = minecraft.gui.screen();
        if (!supported || !(screen instanceof AnvilScreen anvilScreen)
                || !(minecraft.player.containerMenu instanceof AnvilMenu menu)) {
            closeTarget(supported);
            return;
        }

        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        targetOpen = true;
        lastSnapshotNanos = now;

        EditBox nameField = ((AnvilScreenAccessor) anvilScreen).totem$getNameField();
        String itemName = nameField == null ? "" : nameField.getValue();
        int levelCost = Math.max(0, menu.getCost());
        boolean resultAvailable = !menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty();
        boolean tooExpensive = resultAvailable && levelCost >= 40 && !minecraft.player.getAbilities().instabuild;
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();

        ClientPlayNetworking.send(new ObserverAnvilScreenPayloads.AnvilState(
                ObserverAnvilScreenPayloads.PROTOCOL_VERSION,
                ++nextTargetSequence,
                true,
                ObserverNativeScreenPayloads.FAMILY_ANVIL,
                screen.getClass().getName(),
                title,
                itemName,
                levelCost,
                tooExpensive,
                resultAvailable,
                captureSlots(menu)
        ));
    }

    private static List<ObserverNativeScreenPayloads.SlotState> captureSlots(AnvilMenu menu) {
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>();
        int limit = Math.min(menu.slots.size(), ObserverNativeScreenPayloads.MAX_SLOTS);
        for (int i = 0; i < limit; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            slots.add(new ObserverNativeScreenPayloads.SlotState(
                    i,
                    slot.x,
                    slot.y,
                    stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    stack.isEmpty() ? 0 : stack.getCount(),
                    stack.isEmpty() ? 0 : stack.getDamageValue()
            ));
        }
        return List.copyOf(slots);
    }

    private static void closeTarget(boolean canSend) {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) {
            ClientPlayNetworking.send(new ObserverAnvilScreenPayloads.AnvilState(
                    ObserverAnvilScreenPayloads.PROTOCOL_VERSION, ++nextTargetSequence, false,
                    ObserverNativeScreenPayloads.FAMILY_ANVIL, "", "", "", 0, false, false, List.of()
            ));
        }
    }

    private static void acceptRelay(ObserverAnvilScreenPayloads.AnvilRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_ANVIL)
                || targetId == null || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverAnvilScreenPayloads.PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_ANVIL.equals(payload.familyId())
                || payload.sequence() <= lastRemoteSequence) return;
        lastRemoteSequence = payload.sequence();
        if (!payload.open()) {
            clearRemote();
            closeMirror();
            return;
        }

        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteScreenClass = payload.screenClass();
        remoteTitle = payload.title();
        remoteItemName = payload.itemName();
        remoteLevelCost = payload.levelCost();
        remoteTooExpensive = payload.tooExpensive();
        remoteResultAvailable = payload.resultAvailable();
        remoteSlots = List.copyOf(payload.slots());
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeAnvilMirrorScreen)) {
            suppressMirrorStop = true;
            try { minecraft.setScreenAndShow(new NativeAnvilMirrorScreen()); }
            finally { suppressMirrorStop = false; }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeAnvilMirrorScreen)) return;
        suppressMirrorStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressMirrorStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteScreenClass = "";
        remoteTitle = "";
        remoteItemName = "";
        remoteLevelCost = 0;
        remoteTooExpensive = false;
        remoteResultAvailable = false;
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
            TotemVanillaTweaks.LOGGER.debug("Ignoring invalid anvil Observer item id {}", slot.itemId());
            return ItemStack.EMPTY;
        }
    }

    private static final class NativeAnvilMirrorScreen extends Screen {
        private NativeAnvilMirrorScreen() { super(Component.literal("Observer Anvil")); }

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
            int panelWidth = Math.min(230, Math.max(200, width - 28));
            int panelHeight = 150;
            int left = (width - panelWidth) / 2;
            int top = (height - panelHeight) / 2;
            graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xEE202020);
            graphics.fill(left + 4, top + 4, left + panelWidth - 4, top + 24, 0xFF303030);
            String title = remoteTitle.isBlank() ? remoteScreenClass : remoteTitle;
            graphics.text(this.minecraft.font, title, left + 10, top + 10, 0xFFFFFFFF, true);

            graphics.fill(left + 10, top + 32, left + panelWidth - 10, top + 51, 0xFF111111);
            graphics.text(this.minecraft.font, remoteItemName.isBlank() ? " " : remoteItemName,
                    left + 15, top + 38, 0xFFFFFFFF, false);

            drawSlot(graphics, AnvilMenu.INPUT_SLOT, left + 28, top + 70);
            graphics.text(this.minecraft.font, "+", left + 56, top + 76, 0xFFBDBDBD, false);
            drawSlot(graphics, AnvilMenu.ADDITIONAL_SLOT, left + 72, top + 70);
            graphics.text(this.minecraft.font, "→", left + 103, top + 76, 0xFFFFFFFF, false);
            drawSlot(graphics, AnvilMenu.RESULT_SLOT, left + 123, top + 70);

            String cost;
            int costColor;
            if (!remoteResultAvailable) {
                cost = "No result";
                costColor = 0xFF9E9E9E;
            } else if (remoteTooExpensive) {
                cost = "Too Expensive!";
                costColor = 0xFFFF6B6B;
            } else {
                cost = "Cost: " + remoteLevelCost + " level" + (remoteLevelCost == 1 ? "" : "s");
                costColor = 0xFF80E27E;
            }
            graphics.text(this.minecraft.font, cost, left + 10, top + 108, costColor, false);
            graphics.text(this.minecraft.font, "anvil semantic / framebuffer-free", left + 10,
                    top + panelHeight - 16, 0xFF888888, false);
            extractedFrames++;
        }

        private void drawSlot(GuiGraphicsExtractor graphics, int slotIndex, int x, int y) {
            graphics.fill(x - 1, y - 1, x + 18, y + 18, 0xFF555555);
            for (ObserverNativeScreenPayloads.SlotState slot : remoteSlots) {
                if (slot.index() != slotIndex) continue;
                ItemStack stack = itemStack(slot);
                if (!stack.isEmpty()) {
                    graphics.item(stack, x, y);
                    graphics.itemDecorations(this.minecraft.font, stack, x, y);
                }
                return;
            }
        }
    }
}
