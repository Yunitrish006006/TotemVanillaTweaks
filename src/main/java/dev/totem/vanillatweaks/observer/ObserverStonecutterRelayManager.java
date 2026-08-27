package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStonecutterScreenPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Relay and validation for Stonecutter selector semantics. */
public final class ObserverStonecutterRelayManager {
    private static final int VANILLA_SLOT_COUNT = 38;
    private static final Map<UUID, Long> LAST_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Field TARGET_BY_OBSERVER = staticField("TARGET_BY_OBSERVER");
    private static final Field SCREEN_CAPABILITIES_BY_OBSERVER = staticField("SCREEN_CAPABILITIES_BY_OBSERVER");
    private ObserverStonecutterRelayManager() {}

    public static void acceptState(ServerPlayer target, ObserverStonecutterScreenPayloads.StonecutterState payload) {
        if (!valid(payload)) return;
        UUID targetId = target.getUUID();
        if (!hasCapableObserver(targetId)) return;
        long last = LAST_SEQUENCE_BY_TARGET.getOrDefault(targetId, -1L);
        if (payload.sequence() <= last) return;
        LAST_SEQUENCE_BY_TARGET.put(targetId, payload.sequence());
        MinecraftServer server = target.level().getServer();
        var relay = ObserverStonecutterScreenPayloads.relay(targetId, payload);
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (!targetId.equals(entry.getValue())) continue;
            UUID observerId = entry.getKey();
            if (!ObserverNativeScreenPayloads.supports(capabilitiesByObserver().getOrDefault(observerId, 0L),
                    ObserverStonecutterScreenPayloads.CAPABILITY)) continue;
            ServerPlayer observer = server.getPlayerList().getPlayer(observerId);
            if (observer != null && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverStonecutterScreenPayloads.StonecutterRelay.TYPE)) {
                ServerPlayNetworking.send(observer, relay);
            }
        }
    }

    public static void clearTarget(UUID targetId) { LAST_SEQUENCE_BY_TARGET.remove(targetId); }

    private static boolean hasCapableObserver(UUID targetId) {
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (targetId.equals(entry.getValue()) && ObserverNativeScreenPayloads.supports(
                    capabilitiesByObserver().getOrDefault(entry.getKey(), 0L), ObserverStonecutterScreenPayloads.CAPABILITY)) return true;
        }
        return false;
    }

    private static boolean valid(ObserverStonecutterScreenPayloads.StonecutterState p) {
        if (p.protocolVersion() != ObserverStonecutterScreenPayloads.PROTOCOL_VERSION || p.sequence() < 0L
                || !ObserverStonecutterScreenPayloads.FAMILY_ID.equals(p.familyId())) return false;
        if (!p.open()) return p.selectedRecipeIndex() == -1 && p.recipeCount() == 0 && !p.hasInputItem()
                && p.startIndex() == 0 && p.scrollOffset() == 0.0F && !p.displayRecipes()
                && !p.resultAvailable() && p.recipes().isEmpty() && p.slots().isEmpty();
        if (!ObserverStonecutterScreenPayloads.SCREEN_CLASS.equals(p.screenClass())
                || p.recipeCount() < 0 || p.recipeCount() > ObserverStonecutterScreenPayloads.MAX_RECIPES
                || p.recipes().size() != p.recipeCount()
                || !Float.isFinite(p.scrollOffset()) || p.scrollOffset() < 0.0F || p.scrollOffset() > 1.001F
                || p.displayRecipes() != p.hasInputItem() || p.slots().size() != VANILLA_SLOT_COUNT) return false;
        for (int i = 0; i < p.recipes().size(); i++) {
            var recipe = p.recipes().get(i);
            if (recipe == null || recipe.index() != i || recipe.recipeId() == null || recipe.recipeId().length() > 256
                    || (!recipe.recipeId().isBlank() && !validIdentifier(recipe.recipeId()))
                    || !validIdentifier(recipe.outputItemId()) || recipe.outputCount() <= 0
                    || recipe.outputCount() > 127 || recipe.outputDamage() < 0) return false;
        }
        int offscreenRows = Math.max(0, Math.ceilDiv(p.recipeCount(), 4) - 3);
        int expectedStart = offscreenRows == 0 ? 0 : (int) (p.scrollOffset() * offscreenRows + 0.5F) * 4;
        if (p.startIndex() != expectedStart || p.startIndex() < 0
                || p.startIndex() > Math.max(0, offscreenRows * 4)) return false;
        if (p.recipeCount() == 0) {
            if (p.selectedRecipeIndex() != -1 || p.resultAvailable()) return false;
        } else {
            if (p.selectedRecipeIndex() < -1 || p.selectedRecipeIndex() >= p.recipeCount()) return false;
            if (p.selectedRecipeIndex() == -1 && p.resultAvailable()) return false;
        }
        return p.resultAvailable() == slotPresentAtMenuOrdinal(p.slots(), 1) && validSlots(p.slots());
    }

    private static boolean slotPresentAtMenuOrdinal(
            List<ObserverNativeScreenPayloads.SlotState> slots, int ordinal) {
        if (ordinal < 0 || ordinal >= slots.size()) return false;
        var slot = slots.get(ordinal);
        return slot.count() > 0 && !slot.itemId().isBlank();
    }

    private static boolean validIdentifier(String value) {
        if (value == null || value.isBlank() || value.length() > 256) return false;
        try {
            net.minecraft.resources.Identifier.parse(value);
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean validSlots(List<ObserverNativeScreenPayloads.SlotState> slots) {
        if (slots.size() > ObserverNativeScreenPayloads.MAX_SLOTS) return false;
        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            if (slot.index() != i || slot.x() < -64 || slot.x() > 512 || slot.y() < -64 || slot.y() > 512
                    || slot.count() < 0 || slot.count() > 127 || slot.damage() < 0) return false;
        }
        return true;
    }

    private static Field staticField(String name) {
        try { Field field = ObserverNativeSessionManager.class.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }
    @SuppressWarnings("unchecked") private static Map<UUID, UUID> targetByObserver() {
        try { return (Map<UUID, UUID>) TARGET_BY_OBSERVER.get(null); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }
    @SuppressWarnings("unchecked") private static Map<UUID, Long> capabilitiesByObserver() {
        try { return (Map<UUID, Long>) SCREEN_CAPABILITIES_BY_OBSERVER.get(null); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }
}
