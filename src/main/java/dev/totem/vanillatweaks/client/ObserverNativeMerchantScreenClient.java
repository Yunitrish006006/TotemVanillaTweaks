package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
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

    private static boolean suppressMirrorStop;
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

    static boolean isNativeMirrorScreen(Screen screen) {
        return screen instanceof NativeMerchantMirrorScreen;
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
        if (remoteOpen) {
            ensureMirror();
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
            closeMirror();
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
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) {
            return;
        }
        if (!(minecraft.gui.screen() instanceof NativeMerchantMirrorScreen)) {
            suppressMirrorStop = true;
            try {
                minecraft.setScreenAndShow(new NativeMerchantMirrorScreen());
            } finally {
                suppressMirrorStop = false;
            }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeMerchantMirrorScreen)) {
            return;
        }
        suppressMirrorStop = true;
        try {
            minecraft.setScreenAndShow(null);
        } finally {
            suppressMirrorStop = false;
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

    private static final class NativeMerchantMirrorScreen extends ObserverMirrorScreen {
        private NativeMerchantMirrorScreen() {
            super(Component.literal("Observer Merchant"));
        }

        @Override
        public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) {
                ClientPlayNetworking.send(new ObserverPayloads.Stop());
            }
            super.onClose();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0x90000000);
            int panelWidth = Math.min(292, Math.max(220, width - 28));
            int panelHeight = Math.min(184, Math.max(150, height - 28));
            int left = (width - panelWidth) / 2;
            int top = (height - panelHeight) / 2;
            graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xEE202020);
            graphics.fill(left + 4, top + 4, left + panelWidth - 4, top + 24, 0xFF303030);

            String title = remoteTitle.isBlank() ? remoteScreenClass : remoteTitle;
            graphics.text(this.minecraft.font, title, left + 10, top + 10, 0xFFFFFFFF, true);
            String status = "Lv " + remoteTraderLevel + "  XP " + remoteTraderXp + "/" + remoteFutureTraderXp;
            graphics.text(this.minecraft.font, status,
                    left + panelWidth - 10 - this.minecraft.font.width(status), top + 10, 0xFFBDBDBD, false);

            if (remoteShowProgressBar) {
                int barLeft = left + 10;
                int barTop = top + 29;
                int barWidth = panelWidth - 20;
                graphics.fill(barLeft, barTop, barLeft + barWidth, barTop + 4, 0xFF4A4A4A);
                int denominator = Math.max(1, remoteFutureTraderXp);
                int progress = Math.max(0, Math.min(barWidth, barWidth * remoteTraderXp / denominator));
                graphics.fill(barLeft, barTop, barLeft + progress, barTop + 4, 0xFFB5D36A);
            }

            int first = Math.max(0, Math.min(remoteSelectedOffer - 3,
                    Math.max(0, remoteOffers.size() - MAX_VISIBLE_OFFERS)));
            int end = Math.min(remoteOffers.size(), first + MAX_VISIBLE_OFFERS);
            int rowY = top + 39;
            for (int i = first; i < end; i++) {
                ObserverMerchantScreenPayloads.OfferState offer = remoteOffers.get(i);
                boolean selected = offer.index() == remoteSelectedOffer;
                int rowTop = rowY + (i - first) * 19;
                graphics.fill(left + 8, rowTop, left + panelWidth - 8, rowTop + 18,
                        selected ? 0xFF4B5E75 : 0xFF2B2B2B);

                drawItem(graphics, offer.costA(), left + 14, rowTop + 1);
                if (!offer.costB().itemId().isBlank()) {
                    graphics.text(this.minecraft.font, "+", left + 36, rowTop + 6, 0xFFAAAAAA, false);
                    drawItem(graphics, offer.costB(), left + 46, rowTop + 1);
                }
                graphics.text(this.minecraft.font, "→", left + 72, rowTop + 5,
                        offer.outOfStock() ? 0xFFFF6B6B : 0xFFFFFFFF, false);
                drawItem(graphics, offer.result(), left + 90, rowTop + 1);

                String usage = offer.outOfStock() ? "OUT" : offer.uses() + "/" + offer.maxUses();
                String xp = "+" + offer.xp() + "xp";
                int usageX = left + panelWidth - 12 - this.minecraft.font.width(usage);
                graphics.text(this.minecraft.font, usage, usageX, rowTop + 3,
                        offer.outOfStock() ? 0xFFFF6B6B : 0xFFBDBDBD, false);
                graphics.text(this.minecraft.font, xp,
                        usageX - 8 - this.minecraft.font.width(xp), rowTop + 3, 0xFF9E9E9E, false);
            }

            String footer = remoteOffers.size() + " offers"
                    + (remoteCanRestock ? " / restock" : "")
                    + " / " + remoteVariant;
            graphics.text(this.minecraft.font, footer, left + 10, top + panelHeight - 15, 0xFF9E9E9E, false);
            extractedFrames++;
        }

        private void drawItem(GuiGraphicsExtractor graphics, ObserverMerchantScreenPayloads.ItemState state,
                              int x, int y) {
            ItemStack stack = itemStack(state);
            if (stack.isEmpty()) {
                return;
            }
            graphics.item(stack, x, y);
            graphics.itemDecorations(this.minecraft.font, stack, x, y);
        }
    }
}
