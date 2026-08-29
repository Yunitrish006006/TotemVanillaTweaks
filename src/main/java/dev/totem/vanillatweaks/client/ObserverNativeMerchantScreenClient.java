package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import dev.totem.vanillatweaks.mixin.client.MerchantScreenAccessor;
import dev.totem.vanillatweaks.network.ObserverMerchantScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.ItemCost;
import java.util.Optional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Target adapter and Observer-side reconstruction for vanilla merchant screens. */
public final class ObserverNativeMerchantScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static final int MAX_VISIBLE_OFFERS = 7;

    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static boolean remoteOpen;
    private static String remoteVariant = "";
    private static String remoteScreenClass = "";
    private static String remoteTitle = "";
    private static int remoteSelectedOffer;
    private static int remoteTraderLevel;
    private static int remoteTraderXp;
    private static int remoteFutureTraderXp;
    private static boolean remoteShowProgressBar;
    private static boolean remoteCanRestock;
    private static List<ObserverMerchantScreenPayloads.OfferState> remoteOffers = List.of();

    private static boolean suppressObserverScreenStop;
    private static long extractedFrames;

    private ObserverNativeMerchantScreenClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverMerchantScreenPayloads.MerchantRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload))
        );
        ClientTickEvents.END_CLIENT_TICK.register(ObserverNativeMerchantScreenClient::tick);
    }

    static boolean isTargetMerchantScreen(Screen screen) {
        return screen instanceof MerchantScreen;
    }

    static boolean isNativeObserverScreen(Screen screen) {
        return screen instanceof ObserverMerchantScreen;
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
        if (remoteOpen) {
            ensureObserverScreen();
        }
    }

    private static void tickTarget(Minecraft minecraft) {
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_MERCHANT);
        Screen screen = minecraft.gui.screen();
        if (!supported || !(screen instanceof MerchantScreen merchantScreen)) {
            closeTarget(supported);
            return;
        }
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) {
            return;
        }
        targetOpen = true;
        lastSnapshotNanos = now;

        ClientPlayNetworking.send(captureTargetState(merchantScreen, ++nextTargetSequence));
    }

    static ObserverMerchantScreenPayloads.MerchantState captureTargetState(
            MerchantScreen merchantScreen,
            long sequence
    ) {
        MerchantMenu menu = merchantScreen.getMenu();
        List<ObserverMerchantScreenPayloads.OfferState> offers = new ArrayList<>();
        int offerCount = Math.min(menu.getOffers().size(), ObserverMerchantScreenPayloads.MAX_OFFERS);
        for (int i = 0; i < offerCount; i++) {
            MerchantOffer offer = menu.getOffers().get(i);
            offers.add(new ObserverMerchantScreenPayloads.OfferState(
                    i,
                    itemState(offer.getCostA()),
                    itemState(offer.getCostB()),
                    itemState(offer.getResult()),
                    offer.getUses(),
                    offer.getMaxUses(),
                    offer.getXp(),
                    offer.isOutOfStock()
            ));
        }

        String title = merchantScreen.getTitle() == null ? "" : merchantScreen.getTitle().getString();
        int selected = ((MerchantScreenAccessor) merchantScreen).totem$getSelectedOffer();
        return new ObserverMerchantScreenPayloads.MerchantState(
                ObserverMerchantScreenPayloads.PROTOCOL_VERSION,
                sequence,
                true,
                ObserverNativeScreenPayloads.FAMILY_MERCHANT,
                ObserverMerchantScreenPayloads.VARIANT_VANILLA,
                merchantScreen.getClass().getName(),
                title,
                selected,
                menu.getTraderLevel(),
                menu.getTraderXp(),
                menu.getFutureTraderXp(),
                menu.showProgressBar(),
                menu.canRestock(),
                List.copyOf(offers)
        );
    }

    private static void closeTarget(boolean canSend) {
        if (!targetOpen) {
            return;
        }
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) {
            ClientPlayNetworking.send(new ObserverMerchantScreenPayloads.MerchantState(
                    ObserverMerchantScreenPayloads.PROTOCOL_VERSION,
                    ++nextTargetSequence,
                    false,
                    ObserverNativeScreenPayloads.FAMILY_MERCHANT,
                    "",
                    "",
                    "",
                    0,
                    0,
                    0,
                    0,
                    false,
                    false,
                    List.of()
            ));
        }
    }

    private static ObserverMerchantScreenPayloads.ItemState itemState(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new ObserverMerchantScreenPayloads.ItemState("", 0, 0);
        }
        return new ObserverMerchantScreenPayloads.ItemState(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getCount(),
                stack.getDamageValue()
        );
    }

    private static void acceptRelay(ObserverMerchantScreenPayloads.MerchantRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_MERCHANT)
                || targetId == null
                || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverMerchantScreenPayloads.PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_MERCHANT.equals(payload.familyId())
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverNativeScreenPayloads.FAMILY_MERCHANT,
                        payload.targetId(), payload.sequence())) {
            return;
        }
        if (!payload.open()) {
            clearRemote();
            closeObserverScreen();
            return;
        }

        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteVariant = payload.variant();
        remoteScreenClass = payload.screenClass();
        remoteTitle = payload.title();
        remoteSelectedOffer = payload.selectedOffer();
        remoteTraderLevel = payload.traderLevel();
        remoteTraderXp = payload.traderXp();
        remoteFutureTraderXp = payload.futureTraderXp();
        remoteShowProgressBar = payload.showProgressBar();
        remoteCanRestock = payload.canRestock();
        remoteOffers = List.copyOf(payload.offers());
        ensureObserverScreen();
    }

    private static void ensureObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) {
            return;
        }
        if (!(minecraft.gui.screen() instanceof ObserverMerchantScreen)) {
            suppressObserverScreenStop = true;
            try {
                var inventory = ObserverVanillaScreenSupport.detachedInventory();
                minecraft.setScreenAndShow(new ObserverMerchantScreen(new MerchantMenu(-1, inventory), inventory,
                        Component.literal(remoteTitle.isBlank() ? "Merchant" : remoteTitle)));
            } finally {
                suppressObserverScreenStop = false;
            }
        }
        if (minecraft.gui.screen() instanceof ObserverMerchantScreen screen) applyMerchantMenu(screen.getMenu());
    }

    private static void closeObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ObserverMerchantScreen)) {
            return;
        }
        suppressObserverScreenStop = true;
        try {
            minecraft.setScreenAndShow(null);
        } finally {
            suppressObserverScreenStop = false;
        }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteVariant = "";
        remoteScreenClass = "";
        remoteTitle = "";
        remoteSelectedOffer = 0;
        remoteTraderLevel = 0;
        remoteTraderXp = 0;
        remoteFutureTraderXp = 0;
        remoteShowProgressBar = false;
        remoteCanRestock = false;
        remoteOffers = List.of();
    }

    private static ItemStack itemStack(ObserverMerchantScreenPayloads.ItemState itemState) {
        if (itemState.itemId().isBlank() || itemState.count() <= 0) {
            return ItemStack.EMPTY;
        }
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemState.itemId()));
            if (item == null) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(item, Math.max(1, itemState.count()));
            if (itemState.damage() > 0 && stack.isDamageableItem()) {
                stack.setDamageValue(itemState.damage());
            }
            return stack;
        } catch (RuntimeException error) {
            TotemVanillaTweaks.LOGGER.debug("Ignoring invalid merchant Observer item id {}", itemState.itemId());
            return ItemStack.EMPTY;
        }
    }

    private static void applyMerchantMenu(MerchantMenu menu) {
        MerchantOffers offers = new MerchantOffers();
        for (ObserverMerchantScreenPayloads.OfferState state : remoteOffers) {
            ItemStack first = itemStack(state.costA());
            ItemStack second = itemStack(state.costB());
            ItemStack result = itemStack(state.result());
            if (first.isEmpty() || result.isEmpty()) continue;
            ItemCost costA = new ItemCost(first.getItem(), first.getCount());
            Optional<ItemCost> costB = second.isEmpty() ? Optional.empty()
                    : Optional.of(new ItemCost(second.getItem(), second.getCount()));
            MerchantOffer offer = new MerchantOffer(costA, costB, result, state.uses(),
                    Math.max(state.uses(), state.maxUses()), state.xp(), 0.0F);
            if (state.outOfStock()) offer.setToOutOfStock();
            offers.add(offer);
        }
        menu.setOffers(offers);
        menu.setSelectionHint(Math.clamp(remoteSelectedOffer, 0, Math.max(0, offers.size() - 1)));
        menu.setMerchantLevel(remoteTraderLevel);
        menu.setXp(remoteTraderXp);
        menu.setShowProgressBar(remoteShowProgressBar);
        menu.setCanRestock(remoteCanRestock);
    }


    private static final class ObserverMerchantScreen extends MerchantScreen implements ObserverReadOnlyScreen {
        private ObserverMerchantScreen(MerchantMenu menu, net.minecraft.world.entity.player.Inventory inventory,
                                       Component title) { super(menu, inventory, title); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void onClose() { if (!suppressObserverScreenStop) ObserverVanillaScreenSupport.stopObserving(); }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick){
            super.extractRenderState(graphics,x,y,tick); extractedFrames++;
        }
    }
}
