package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.mixin.client.BeaconScreenAccessor;
import dev.totem.vanillatweaks.network.ObserverBeaconScreenPayloads;
import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** Semantic adapter and local reconstruction for the vanilla Beacon screen. */
public final class ObserverBeaconScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    static final int PANEL_WIDTH = 230;
    static final int PANEL_HEIGHT = 219;
    static final int SAFE_SCREEN_MARGIN = 8;
    static final int CONTENT_X = 8;
    static final int CONTENT_RIGHT = 222;
    static final int CONTENT_WIDTH = CONTENT_RIGHT - CONTENT_X;
    static final int TEXT_HEIGHT = 9;
    static final int TITLE_Y = 7;
    static final int TIER_Y = 22;
    static final int PRIMARY_Y = 38;
    static final int SECONDARY_Y = 51;
    static final int PAYMENT_Y = 64;
    static final int CONFIRM_Y = 77;
    static final int EFFECT_1_Y = 90;
    static final int EFFECT_2_Y = 101;
    static final int EFFECT_3_Y = 112;
    static final int EFFECT_4_Y = 123;
    static final int PAYMENT_SLOT_BORDER_LEFT = 135;
    static final int PAYMENT_SLOT_BORDER_TOP = 109;
    static final int PAYMENT_SLOT_BORDER_BOTTOM = 127;
    static final int EFFECT_SLOT_GUTTER = 7;
    static final int EFFECT_TEXT_MAX_WIDTH = PAYMENT_SLOT_BORDER_LEFT - EFFECT_SLOT_GUTTER - CONTENT_X;
    static final int INVENTORY_SLOT_BORDER_TOP = 136;
    static final int HOTBAR_SLOT_BORDER_BOTTOM = 213;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static int remoteLevels;
    private static String remotePrimaryEffectId = "";
    private static String remoteSecondaryEffectId = "";
    private static boolean remotePaymentPresent;
    private static boolean remoteCanConfirm;
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();
    private static boolean suppressObserverScreenStop;
    private static long extractedFrames;

    private ObserverBeaconScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ObserverBeaconScreenPayloads.BeaconRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverBeaconScreenClient::tick);
    }

    public static boolean isBeaconScreen(Screen screen) {
        return screen instanceof BeaconScreen;
    }

    private static void tick(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null) closeTarget(false);
        else tickTarget(minecraft);
        if (!ObserverNativeClient.observerSessionActive()) { clearRemote(); closeObserverScreen(); }
        else if (remoteOpen) ensureObserverScreen();
    }

    private static void tickTarget(Minecraft minecraft) {
        boolean supported = ObserverNativeClient.targetSupportsScreen(ObserverBeaconScreenPayloads.CAPABILITY);
        Screen current = minecraft.gui.screen();
        if (!supported || !(current instanceof BeaconScreen screen)) {
            closeTarget(supported);
            return;
        }
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        targetOpen = true;
        lastSnapshotNanos = now;

        ClientPlayNetworking.send(captureTargetState(screen, ++nextTargetSequence));
    }

    static ObserverBeaconScreenPayloads.BeaconState captureTargetState(
            BeaconScreen screen,
            long sequence
    ) {
        BeaconMenu menu = screen.getMenu();
        List<ObserverNativeScreenPayloads.SlotState> slots = captureSlots(menu);
        BeaconScreenAccessor accessor = (BeaconScreenAccessor) screen;
        String primary = effectId(accessor.totem$getPrimary());
        String secondary = effectId(accessor.totem$getSecondary());
        int levels = menu.getLevels();
        boolean payment = menu.hasPayment();
        boolean canConfirm = levels > 0 && payment && !primary.isBlank();
        return new ObserverBeaconScreenPayloads.BeaconState(
                ObserverBeaconScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverBeaconScreenPayloads.FAMILY_ID, screen.getClass().getName(),
                screen.getTitle() == null ? "" : screen.getTitle().getString(),
                levels, primary, secondary, payment, canConfirm, slots);
    }

    private static String effectId(Holder<MobEffect> effect) {
        if (effect == null) return "";
        return effect.unwrapKey().map(key -> key.identifier().toString()).orElseGet(() -> {
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect.value());
            return id == null ? "" : id.toString();
        });
    }

    private static void closeTarget(boolean canSend) {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (canSend) ClientPlayNetworking.send(ObserverBeaconScreenPayloads.closed(++nextTargetSequence));
    }

    private static List<ObserverNativeScreenPayloads.SlotState> captureSlots(BeaconMenu menu) {
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>();
        int limit = Math.min(menu.slots.size(), ObserverNativeScreenPayloads.MAX_SLOTS);
        for (int i = 0; i < limit; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            slots.add(new ObserverNativeScreenPayloads.SlotState(i, slot.x, slot.y,
                    stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    stack.isEmpty() ? 0 : stack.getCount(), stack.isEmpty() ? 0 : stack.getDamageValue()));
        }
        return List.copyOf(slots);
    }

    private static void acceptRelay(ObserverBeaconScreenPayloads.BeaconRelay payload) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverBeaconScreenPayloads.CAPABILITY)
                || targetId == null || !targetId.equals(payload.targetId())
                || payload.protocolVersion() != ObserverBeaconScreenPayloads.PROTOCOL_VERSION
                || !ObserverBeaconScreenPayloads.FAMILY_ID.equals(payload.familyId())
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverBeaconScreenPayloads.FAMILY_ID,
                        payload.targetId(), payload.sequence())) return;
        if (!payload.open()) { clearRemote(); closeObserverScreen(); return; }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = payload.title();
        remoteLevels = payload.levels();
        remotePrimaryEffectId = payload.primaryEffectId();
        remoteSecondaryEffectId = payload.secondaryEffectId();
        remotePaymentPresent = payload.paymentPresent();
        remoteCanConfirm = payload.canConfirm();
        remoteSlots = List.copyOf(payload.slots());
        ensureObserverScreen();
    }

    private static void ensureObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof ObserverBeaconScreen)) {
            suppressObserverScreenStop = true;
            try {
                var inventory = ObserverVanillaScreenSupport.detachedInventory();
                minecraft.setScreenAndShow(new ObserverBeaconScreen(new BeaconMenu(-1, inventory), inventory,
                        Component.literal(remoteTitle.isBlank() ? "Beacon" : remoteTitle)));
            }
            finally { suppressObserverScreenStop = false; }
        }
        if (minecraft.gui.screen() instanceof ObserverBeaconScreen screen) {
            ObserverVanillaScreenSupport.applyMenu(screen.getMenu(), remoteSlots);
            screen.getMenu().setData(0, remoteLevels);
            screen.getMenu().setData(1, encodeEffect(remotePrimaryEffectId));
            screen.getMenu().setData(2, encodeEffect(remoteSecondaryEffectId));
        }
    }

    private static void closeObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ObserverBeaconScreen)) return;
        suppressObserverScreenStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressObserverScreenStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
        remoteLevels = 0;
        remotePrimaryEffectId = "";
        remoteSecondaryEffectId = "";
        remotePaymentPresent = false;
        remoteCanConfirm = false;
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
        } catch (RuntimeException error) { return ItemStack.EMPTY; }
    }

    private static String shortEffect(String id) {
        if (id == null || id.isBlank()) return "None";
        int colon = id.indexOf(':');
        String value = colon >= 0 ? id.substring(colon + 1) : id;
        return value.replace('_', ' ');
    }

    private static int encodeEffect(String id) {
        if (id == null || id.isBlank()) return 0;
        try {
            var effect = BuiltInRegistries.MOB_EFFECT.getValue(Identifier.parse(id));
            return effect == null ? 0 : BeaconMenu.encodeEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
        } catch (RuntimeException ignored) { return 0; }
    }

    /** Binds every semantic label to its own row and its available vanilla pixel budget. */
    static BeaconTextLayout beaconTextLayout(
            String title,
            int levels,
            String primaryEffect,
            String secondaryEffect,
            boolean paymentPresent,
            boolean canConfirm,
            ToIntFunction<String> widthOf
    ) {
        String safeTitle = title == null || title.isBlank() ? "Beacon" : title;
        BoundedLabel effect1 = chooseLabel(EFFECT_TEXT_MAX_WIDTH, widthOf,
                "L1 Speed / Haste", "L1 Speed/Haste");
        BoundedLabel effect2 = chooseLabel(EFFECT_TEXT_MAX_WIDTH, widthOf,
                "L2 Resistance / Jump", "L2 Resist / Jump", "L2 Resist/Jump");
        BoundedLabel effect3 = chooseLabel(EFFECT_TEXT_MAX_WIDTH, widthOf,
                "L3 Strength");
        BoundedLabel effect4 = chooseLabel(EFFECT_TEXT_MAX_WIDTH, widthOf,
                "L4 Regeneration / Upgrade", "L4 Regen / Upgrade", "L4 Regen/Upgrade");
        return new BeaconTextLayout(
                boundedLabel(safeTitle, CONTENT_WIDTH, widthOf),
                boundedLabel("Tier: " + levels + "/4", CONTENT_WIDTH, widthOf),
                boundedLabel("Primary: " + shortEffect(primaryEffect), CONTENT_WIDTH, widthOf),
                boundedLabel("Secondary: " + shortEffect(secondaryEffect), CONTENT_WIDTH, widthOf),
                boundedLabel(paymentPresent ? "Payment ready" : "Payment required", CONTENT_WIDTH, widthOf),
                boundedLabel(canConfirm ? "Confirm available" : "Confirm unavailable", CONTENT_WIDTH, widthOf),
                effect1, effect2, effect3, effect4);
    }

    private static BoundedLabel chooseLabel(
            int maxWidth,
            ToIntFunction<String> widthOf,
            String... candidates
    ) {
        for (String candidate : candidates) {
            if (widthOf.applyAsInt(candidate) <= maxWidth) {
                return new BoundedLabel(candidate, widthOf.applyAsInt(candidate), maxWidth);
            }
        }
        return boundedLabel(candidates[candidates.length - 1], maxWidth, widthOf);
    }

    private static BoundedLabel boundedLabel(
            String text,
            int maxWidth,
            ToIntFunction<String> widthOf
    ) {
        String fitted = fitText(text, Math.max(0, maxWidth), widthOf);
        return new BoundedLabel(fitted, widthOf.applyAsInt(fitted), Math.max(0, maxWidth));
    }

    private static String fitText(String text, int maxWidth, ToIntFunction<String> widthOf) {
        if (text.isEmpty() || maxWidth <= 0) return "";
        if (widthOf.applyAsInt(text) <= maxWidth) return text;
        String ellipsis = "…";
        if (widthOf.applyAsInt(ellipsis) > maxWidth) return "";
        int low = 0;
        int high = text.codePointCount(0, text.length());
        while (low < high) {
            int middle = (low + high + 1) / 2;
            int end = text.offsetByCodePoints(0, middle);
            if (widthOf.applyAsInt(text.substring(0, end) + ellipsis) <= maxWidth) low = middle;
            else high = middle - 1;
        }
        return text.substring(0, text.offsetByCodePoints(0, low)) + ellipsis;
    }

    static record BoundedLabel(String text, int textWidth, int maxWidth) {
        boolean fits() { return textWidth <= maxWidth; }
    }

    static record BeaconTextLayout(
            BoundedLabel title,
            BoundedLabel tier,
            BoundedLabel primary,
            BoundedLabel secondary,
            BoundedLabel payment,
            BoundedLabel confirm,
            BoundedLabel effect1,
            BoundedLabel effect2,
            BoundedLabel effect3,
            BoundedLabel effect4
    ) {
        boolean fits() {
            return title.fits() && tier.fits() && primary.fits() && secondary.fits()
                    && payment.fits() && confirm.fits() && effect1.fits() && effect2.fits()
                    && effect3.fits() && effect4.fits();
        }
    }


    private static final class ObserverBeaconScreen extends BeaconScreen implements ObserverReadOnlyScreen {
        private ObserverBeaconScreen(BeaconMenu menu, net.minecraft.world.entity.player.Inventory inventory,
                                     Component title) { super(menu, inventory, title); }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void onClose() { if (!suppressObserverScreenStop) ObserverVanillaScreenSupport.stopObserving(); }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick){
            super.extractRenderState(graphics,x,y,tick); extractedFrames++;
        }
    }
}
