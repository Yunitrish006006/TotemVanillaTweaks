package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
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

    private static boolean remoteOpen;
    private static String remoteScreenClass = "";
    private static String remoteTitle = "";
    private static String remoteItemName = "";
    private static int remoteLevelCost;
    private static boolean remoteTooExpensive;
    private static boolean remoteResultAvailable;
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();

    private static boolean suppressObserverScreenStop;
    private static long extractedFrames;

    private ObserverNativeAnvilScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverAnvilScreenPayloads.AnvilRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload))
        );
        ClientTickEvents.END_CLIENT_TICK.register(ObserverNativeAnvilScreenClient::tick);
    }

    static boolean isNativeObserverScreen(Screen screen) {
        return screen instanceof ObserverAnvilScreen;
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
            closeObserverScreen();
            return;
        }
        if (remoteOpen) ensureObserverScreen();
    }

    private static void tickTarget(Minecraft minecraft) {
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_ANVIL);
        Screen screen = minecraft.gui.screen();
        if (!supported || !(screen instanceof AnvilScreen anvilScreen)) {
            closeTarget(supported);
            return;
        }
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        targetOpen = true;
        lastSnapshotNanos = now;

        ClientPlayNetworking.send(captureTargetState(minecraft, anvilScreen, ++nextTargetSequence));
    }

    static ObserverAnvilScreenPayloads.AnvilState captureTargetState(
            Minecraft minecraft,
            AnvilScreen anvilScreen,
            long sequence
    ) {
        AnvilMenu menu = anvilScreen.getMenu();
        // The rename box is an unsent local draft until the target takes the
        // output. Keep the native Anvil screen, but never put that draft on
        // the Observer transport.
        String itemName = "";
        int levelCost = Math.max(0, menu.getCost());
        boolean resultAvailable = !menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty();
        boolean tooExpensive = resultAvailable && levelCost >= 40 && !minecraft.player.getAbilities().instabuild;
        String title = anvilScreen.getTitle() == null ? "" : anvilScreen.getTitle().getString();

        return new ObserverAnvilScreenPayloads.AnvilState(
                ObserverAnvilScreenPayloads.PROTOCOL_VERSION,
                sequence,
                true,
                ObserverNativeScreenPayloads.FAMILY_ANVIL,
                anvilScreen.getClass().getName(),
                title,
                itemName,
                levelCost,
                tooExpensive,
                resultAvailable,
                captureSlots(menu)
        );
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
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverNativeScreenPayloads.FAMILY_ANVIL,
                        payload.targetId(), payload.sequence())) return;
        if (!payload.open()) {
            clearRemote();
            closeObserverScreen();
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
        ensureObserverScreen();
    }

    private static void ensureObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof ObserverAnvilScreen)) {
            suppressObserverScreenStop = true;
            try {
                var inventory = ObserverVanillaScreenSupport.detachedInventory();
                minecraft.setScreenAndShow(new ObserverAnvilScreen(new AnvilMenu(-1, inventory), inventory,
                        Component.literal(remoteTitle.isBlank() ? "Anvil" : remoteTitle)));
            }
            finally { suppressObserverScreenStop = false; }
        }
        if (minecraft.gui.screen() instanceof ObserverAnvilScreen screen) {
            ObserverVanillaScreenSupport.applyMenu(screen.getMenu(), remoteSlots);
            screen.getMenu().setData(0, remoteLevelCost);
            screen.getMenu().setItemName(remoteItemName);
            EditBox name = ((AnvilScreenAccessor) (Object) screen).totem$getNameField();
            if (name != null && !name.getValue().equals(remoteItemName)) name.setValue(remoteItemName);
        }
    }

    private static void closeObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ObserverAnvilScreen)) return;
        suppressObserverScreenStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressObserverScreenStop = false; }
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


    private static final class ObserverAnvilScreen extends AnvilScreen implements ObserverReadOnlyScreen {
        private ObserverAnvilScreen(AnvilMenu menu, net.minecraft.world.entity.player.Inventory inventory,
                                    Component title) { super(menu, inventory, title); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void onClose() { if (!suppressObserverScreenStop) ObserverVanillaScreenSupport.stopObserving(); }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick){
            super.extractRenderState(graphics,x,y,tick); extractedFrames++;
        }
    }
}
