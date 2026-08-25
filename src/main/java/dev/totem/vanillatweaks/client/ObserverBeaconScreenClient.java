package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.mixin.client.BeaconScreenAccessor;
import dev.totem.vanillatweaks.network.ObserverBeaconScreenPayloads;
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

/** Semantic adapter and local reconstruction for the vanilla Beacon screen. */
public final class ObserverBeaconScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static long lastRemoteSequence = -1L;
    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static int remoteLevels;
    private static String remotePrimaryEffectId = "";
    private static String remoteSecondaryEffectId = "";
    private static boolean remotePaymentPresent;
    private static boolean remoteCanConfirm;
    private static List<ObserverNativeScreenPayloads.SlotState> remoteSlots = List.of();
    private static boolean suppressMirrorStop;
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
        if (!ObserverNativeClient.observerSessionActive()) { clearRemote(); closeMirror(); }
        else if (remoteOpen) ensureMirror();
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

        BeaconMenu menu = screen.getMenu();
        List<ObserverNativeScreenPayloads.SlotState> slots = captureSlots(menu);
        BeaconScreenAccessor accessor = (BeaconScreenAccessor) screen;
        String primary = effectId(accessor.totem$getPrimary());
        String secondary = effectId(accessor.totem$getSecondary());
        int levels = menu.getLevels();
        boolean payment = menu.hasPayment();
        boolean canConfirm = levels > 0 && payment && !primary.isBlank();
        ClientPlayNetworking.send(new ObserverBeaconScreenPayloads.BeaconState(
                ObserverBeaconScreenPayloads.PROTOCOL_VERSION, ++nextTargetSequence, true,
                ObserverBeaconScreenPayloads.FAMILY_ID, screen.getClass().getName(),
                screen.getTitle() == null ? "" : screen.getTitle().getString(),
                levels, primary, secondary, payment, canConfirm, slots));
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
            slots.add(new ObserverNativeScreenPayloads.SlotState(slot.index, slot.x, slot.y,
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
                || payload.sequence() <= lastRemoteSequence) return;
        lastRemoteSequence = payload.sequence();
        if (!payload.open()) { clearRemote(); closeMirror(); return; }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = payload.title();
        remoteLevels = payload.levels();
        remotePrimaryEffectId = payload.primaryEffectId();
        remoteSecondaryEffectId = payload.secondaryEffectId();
        remotePaymentPresent = payload.paymentPresent();
        remoteCanConfirm = payload.canConfirm();
        remoteSlots = List.copyOf(payload.slots());
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeBeaconMirrorScreen)) {
            suppressMirrorStop = true;
            try { minecraft.setScreenAndShow(new NativeBeaconMirrorScreen()); }
            finally { suppressMirrorStop = false; }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeBeaconMirrorScreen)) return;
        suppressMirrorStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressMirrorStop = false; }
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

    private static final class NativeBeaconMirrorScreen extends Screen {
        private NativeBeaconMirrorScreen() { super(Component.literal("Observer Beacon")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) ClientPlayNetworking.send(new ObserverPayloads.Stop());
            super.onClose();
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0x90000000);
            int pw = 230, ph = 180, left = (width - pw) / 2, top = (height - ph) / 2;
            graphics.fill(left, top, left + pw, top + ph, 0xFFE3E3E3);
            graphics.fill(left + 3, top + 3, left + pw - 3, top + ph - 3, 0xFFC6C6C6);
            graphics.text(font, remoteTitle.isBlank() ? "Beacon" : remoteTitle, left + 8, top + 7, 0xFF404040, false);
            graphics.text(font, "Tier: " + remoteLevels + "/4", left + 8, top + 24, remoteLevels > 0 ? 0xFF206020 : 0xFF804040, false);
            graphics.text(font, "Primary: " + shortEffect(remotePrimaryEffectId), left + 8, top + 43, 0xFF404040, false);
            graphics.text(font, "Secondary: " + shortEffect(remoteSecondaryEffectId), left + 8, top + 57, 0xFF404040, false);
            graphics.text(font, remotePaymentPresent ? "Payment ready" : "Payment required", left + 135, top + 43,
                    remotePaymentPresent ? 0xFF206020 : 0xFF804040, false);
            graphics.text(font, remoteCanConfirm ? "Confirm available" : "Confirm unavailable", left + 135, top + 57,
                    remoteCanConfirm ? 0xFF206020 : 0xFF555555, false);
            graphics.text(font, "L1 Speed / Haste", left + 8, top + 78, 0xFF555555, false);
            graphics.text(font, "L2 Resistance / Jump", left + 8, top + 91, 0xFF555555, false);
            graphics.text(font, "L3 Strength", left + 8, top + 104, 0xFF555555, false);
            graphics.text(font, "L4 Regeneration / Upgrade", left + 8, top + 117, 0xFF555555, false);
            for (ObserverNativeScreenPayloads.SlotState slot : remoteSlots) {
                int sx = left + slot.x(), sy = top + slot.y();
                graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF666666);
                graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF202020);
                ItemStack stack = itemStack(slot);
                if (!stack.isEmpty()) { graphics.item(stack, sx, sy); graphics.itemDecorations(font, stack, sx, sy); }
            }
            extractedFrames++;
        }
    }
}
