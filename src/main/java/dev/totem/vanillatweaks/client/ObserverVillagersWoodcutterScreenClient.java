package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import dev.totem.vanillatweaks.network.ObserverVillagersWoodcutterPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Optional reflective adapter for TotemVillagers' WoodcutterScreen. */
public final class ObserverVillagersWoodcutterScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static long lastRemoteSequence = -1L;
    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static int remoteSelectedRecipeIndex = -1;
    private static int remoteRecipeCount;
    private static int remoteRequiredInputCount;
    private static boolean remoteHasInputItem;
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();
    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverVillagersWoodcutterScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ObserverVillagersWoodcutterPayloads.WoodcutterRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverVillagersWoodcutterScreenClient::tick);
    }

    public static boolean isWoodcutterScreen(Screen screen) {
        return screen != null && ObserverVillagersWoodcutterPayloads.SCREEN_CLASS.equals(screen.getClass().getName());
    }

    private static void tick(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) {
            closeTarget(false);
        } else {
            tickTarget(minecraft);
        }
        if (!ObserverNativeClient.observerSessionActive()) {
            clearRemote();
            closeMirror();
        } else if (remoteOpen) {
            ensureMirror();
        }
    }

    private static void tickTarget(Minecraft minecraft) {
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverVillagersWoodcutterPayloads.CAPABILITY);
        Screen screen = minecraft.gui.screen();
        if (!supported || !isWoodcutterScreen(screen) || !(screen instanceof AbstractContainerScreen<?> container)) {
            closeTarget(supported);
            return;
        }
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        targetOpen = true;
        lastSnapshotNanos = now;

        AbstractContainerMenu menu = container.getMenu();
        ClientPlayNetworking.send(new ObserverVillagersWoodcutterPayloads.WoodcutterState(
                ObserverVillagersWoodcutterPayloads.PROTOCOL_VERSION,
                ++nextTargetSequence,
                true,
                ObserverVillagersWoodcutterPayloads.FAMILY_ID,
                screen.getClass().getName(),
                screen.getTitle() == null ? "" : screen.getTitle().getString(),
                invokeInt(menu, "selectedRecipeIndex", -1),
                invokeInt(menu, "recipeCount", 0),
                invokeInt(menu, "requiredInputCount", 0),
                invokeBoolean(menu, "hasInputItem"),
                captureSlots(menu)
        ));
    }

    private static void closeTarget(boolean canSend) {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) ClientPlayNetworking.send(ObserverVillagersWoodcutterPayloads.closed(++nextTargetSequence));
    }

    private static List<ObserverNativeScreenPayloads.SlotState> captureSlots(AbstractContainerMenu menu) {
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>();
        int limit = Math.min(menu.slots.size(), ObserverNativeScreenPayloads.MAX_SLOTS);
        for (int i = 0; i < limit; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            slots.add(new ObserverNativeScreenPayloads.SlotState(slot.index, slot.x, slot.y,
                    stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    stack.isEmpty() ? 0 : stack.getCount(), stack.isEmpty() ? 0 : stack.getDamageValue()));
        }
        return List.copyOf(slots);
    }

    private static int invokeInt(Object owner, String name, int fallback) {
        Object value = invoke(owner, name);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static boolean invokeBoolean(Object owner, String name) {
        Object value = invoke(owner, name);
        return value instanceof Boolean bool && bool;
    }

    private static Object invoke(Object owner, String name) {
        try {
            Method method = owner.getClass().getMethod(name);
            return method.invoke(owner);
        } catch (ReflectiveOperationException error) {
            TotemVanillaTweaks.LOGGER.debug("Woodcutter semantic method {} unavailable on {}", name, owner.getClass().getName());
            return null;
        }
    }

    private static void acceptRelay(ObserverVillagersWoodcutterPayloads.WoodcutterRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverVillagersWoodcutterPayloads.CAPABILITY)
                || targetId == null || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverVillagersWoodcutterPayloads.PROTOCOL_VERSION
                || !ObserverVillagersWoodcutterPayloads.FAMILY_ID.equals(payload.familyId())
                || payload.sequence() <= lastRemoteSequence) return;
        lastRemoteSequence = payload.sequence();
        if (!payload.open()) {
            clearRemote();
            closeMirror();
            return;
        }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = payload.title();
        remoteSelectedRecipeIndex = payload.selectedRecipeIndex();
        remoteRecipeCount = payload.recipeCount();
        remoteRequiredInputCount = payload.requiredInputCount();
        remoteHasInputItem = payload.hasInputItem();
        remoteSlots = List.copyOf(payload.slots());
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeVillagersWoodcutterMirrorScreen)) {
            suppressMirrorStop = true;
            try { minecraft.setScreenAndShow(new NativeVillagersWoodcutterMirrorScreen()); }
            finally { suppressMirrorStop = false; }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeVillagersWoodcutterMirrorScreen)) return;
        suppressMirrorStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressMirrorStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
        remoteSelectedRecipeIndex = -1;
        remoteRecipeCount = 0;
        remoteRequiredInputCount = 0;
        remoteHasInputItem = false;
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
            return ItemStack.EMPTY;
        }
    }

    private static final class NativeVillagersWoodcutterMirrorScreen extends Screen {
        private NativeVillagersWoodcutterMirrorScreen() {
            super(Component.literal("Observer Woodcutter"));
        }

        @Override public boolean isPauseScreen() { return false; }

        @Override
        public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) {
                ClientPlayNetworking.send(new ObserverPayloads.Stop());
            }
            super.onClose();
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0x90000000);
            int panelWidth = 176;
            int panelHeight = 166;
            int left = (width - panelWidth) / 2;
            int top = (height - panelHeight) / 2;
            graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xFFB9A47A);
            graphics.fill(left + 3, top + 3, left + panelWidth - 3, top + panelHeight - 3, 0xFFE1D5B4);
            graphics.fill(left + 45, top + 17, left + 132, top + 62, 0xFFC3AF83);
            graphics.text(font, remoteTitle.isBlank() ? "Woodcutter" : remoteTitle, left + 8, top + 6, 0xFF493825, false);

            if (remoteRecipeCount <= 0) {
                graphics.text(font, remoteHasInputItem ? "No matching recipe" : "Insert wood",
                        left + 51, top + 50, 0xFF6B5140, false);
            } else {
                String status = "Recipe " + (remoteSelectedRecipeIndex + 1) + "/" + remoteRecipeCount
                        + "  Cost " + remoteRequiredInputCount;
                graphics.centeredText(font, status, left + 88, top + 18, 0xFF493825);
                drawButton(graphics, left + 56, top + 27, remoteSelectedRecipeIndex > 0, "‹");
                drawButton(graphics, left + 105, top + 27,
                        remoteSelectedRecipeIndex + 1 < remoteRecipeCount, "›");
            }

            for (ObserverNativeScreenPayloads.SlotState slot : remoteSlots) {
                int sx = left + slot.x();
                int sy = top + slot.y();
                graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF715D41);
                graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF4E4230);
                ItemStack stack = itemStack(slot);
                if (!stack.isEmpty()) {
                    graphics.item(stack, sx, sy);
                    graphics.itemDecorations(font, stack, sx, sy);
                }
            }
            extractedFrames++;
        }

        private void drawButton(GuiGraphicsExtractor graphics, int x, int y, boolean enabled, String label) {
            graphics.fill(x, y, x + 16, y + 16, enabled ? 0xFF806643 : 0xFF8F8067);
            graphics.fill(x + 1, y + 1, x + 15, y + 15, enabled ? 0xFF765E3F : 0xFF9A8C72);
            graphics.centeredText(font, label, x + 8, y + 4, enabled ? 0xFFFFFFFF : 0xFFB3A995);
        }
    }
}
