package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
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
    private static boolean suppressObserverScreenStop;
    private static long extractedFrames;

    private ObserverNativeEnchantingScreenClient() {}

    /** Render hook used by the genuine EnchantmentScreen instead of Observer-local XP. */
    public static int playerLevelFor(Screen screen, int localPlayerLevel) {
        return screen instanceof ObserverEnchantmentScreen && remoteOpen
                ? remotePlayerLevel : localPlayerLevel;
    }

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
            closeObserverScreen();
            return;
        }
        if (remoteOpen) ensureObserverScreen();
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
            closeObserverScreen();
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
        ensureObserverScreen();
    }

    private static void ensureObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof ObserverEnchantmentScreen)) {
            suppressObserverScreenStop = true;
            try {
                var inventory = ObserverVanillaScreenSupport.detachedInventory();
                minecraft.setScreenAndShow(new ObserverEnchantmentScreen(new EnchantmentMenu(-1, inventory), inventory,
                        Component.literal(remoteTitle.isBlank() ? "Enchanting" : remoteTitle)));
            }
            finally { suppressObserverScreenStop = false; }
        }
        if (minecraft.gui.screen() instanceof ObserverEnchantmentScreen screen) {
            ObserverVanillaScreenSupport.applyMenu(screen.getMenu(), remoteSlots);
            for (var option : remoteOptions) {
                if (option.index() < 0 || option.index() >= 3) continue;
                screen.getMenu().costs[option.index()] = option.cost();
                screen.getMenu().enchantClue[option.index()] = option.enchantClue();
                screen.getMenu().levelClue[option.index()] = option.levelClue();
            }
        }
    }

    private static void closeObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ObserverEnchantmentScreen)) return;
        suppressObserverScreenStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressObserverScreenStop = false; }
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


    private static final class ObserverEnchantmentScreen extends EnchantmentScreen implements ObserverReadOnlyScreen {
        private ObserverEnchantmentScreen(EnchantmentMenu menu, net.minecraft.world.entity.player.Inventory inventory,
                                          Component title) { super(menu, inventory, title); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void onClose() { if (!suppressObserverScreenStop) ObserverVanillaScreenSupport.stopObserving(); }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick){
            super.extractRenderState(graphics,x,y,tick); extractedFrames++;
        }
    }
}
