package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverAnvilScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverBookScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverCraftingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverEnchantingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverMerchantScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative structured-state and semantic-screen relay for Observer View. */
public final class ObserverNativeSessionManager {
    private static final UUID EMPTY_TARGET = new UUID(0L, 0L);
    private static final Map<UUID, UUID> TARGET_BY_OBSERVER = new HashMap<>();
    private static final Map<UUID, Long> SCREEN_CAPABILITIES_BY_OBSERVER = new HashMap<>();
    private static final Map<UUID, Long> LAST_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Map<UUID, Long> LAST_SCREEN_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Map<UUID, Long> LAST_BOOK_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Map<UUID, Long> LAST_CRAFTING_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Map<UUID, Long> LAST_MERCHANT_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Map<UUID, Long> LAST_ANVIL_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Map<UUID, Long> LAST_ENCHANTING_SEQUENCE_BY_TARGET = new HashMap<>();

    private ObserverNativeSessionManager() {}

    public static boolean supports(ServerPlayer observer, ServerPlayer target) {
        return ServerPlayNetworking.canSend(observer, ObserverNativePayloads.NativeSession.TYPE)
                && ServerPlayNetworking.canSend(observer, ObserverNativePayloads.NativeViewRelay.TYPE)
                && ServerPlayNetworking.canSend(observer, ObserverPayloads.ScreenRelay.TYPE)
                && ServerPlayNetworking.canSend(target, ObserverNativePayloads.NativeControl.TYPE);
    }

    public static boolean start(ServerPlayer observer, ServerPlayer target) {
        if (!supports(observer, target)) return false;
        long screenCapabilities = negotiatedScreenCapabilities(observer);
        TARGET_BY_OBSERVER.put(observer.getUUID(), target.getUUID());
        SCREEN_CAPABILITIES_BY_OBSERVER.put(observer.getUUID(), screenCapabilities);
        ServerPlayNetworking.send(observer, new ObserverNativePayloads.NativeSession(true, target.getUUID(),
                target.getGameProfile().name(), ObserverNativePayloads.PROTOCOL_VERSION, screenCapabilities));
        updateTargetControl(target.level().getServer(), target.getUUID());
        return true;
    }

    public static boolean stop(ServerPlayer observer) {
        UUID observerId = observer.getUUID();
        UUID targetId = TARGET_BY_OBSERVER.remove(observerId);
        SCREEN_CAPABILITIES_BY_OBSERVER.remove(observerId);
        if (ServerPlayNetworking.canSend(observer, ObserverNativePayloads.NativeSession.TYPE)) {
            ServerPlayNetworking.send(observer, new ObserverNativePayloads.NativeSession(false, EMPTY_TARGET, "",
                    ObserverNativePayloads.PROTOCOL_VERSION, 0L));
        }
        if (targetId != null) updateTargetControl(observer.level().getServer(), targetId);
        return targetId != null;
    }

    public static void removeOfflineObserver(MinecraftServer server, UUID observerId) {
        UUID targetId = TARGET_BY_OBSERVER.remove(observerId);
        SCREEN_CAPABILITIES_BY_OBSERVER.remove(observerId);
        if (targetId != null) updateTargetControl(server, targetId);
    }

    public static void refreshTargetControl(MinecraftServer server, UUID targetId) {
        updateTargetControl(server, targetId);
    }

    public static void acceptViewState(ServerPlayer target, ObserverNativePayloads.NativeViewState payload) {
        if (!valid(payload) || nativeObserverCount(target.getUUID()) == 0) return;
        long last = LAST_SEQUENCE_BY_TARGET.getOrDefault(target.getUUID(), -1L);
        if (payload.sequence() <= last) return;
        LAST_SEQUENCE_BY_TARGET.put(target.getUUID(), payload.sequence());
        relayToNativeObservers(target, ObserverNativePayloads.NativeViewRelay.TYPE,
                new ObserverNativePayloads.NativeViewRelay(target.getUUID(), payload.protocolVersion(), payload.sequence(),
                        payload.yaw(), payload.pitch(), payload.health(), payload.maxHealth(), payload.food(),
                        payload.saturation(), payload.experienceProgress(), payload.experienceLevel(),
                        payload.selectedHotbarSlot(), payload.sprinting(), payload.crouching(), payload.usingItem()), 0L);
    }

    public static void acceptContainerState(ServerPlayer target, ObserverNativeScreenPayloads.ContainerState payload) {
        long capability = ObserverNativeScreenPayloads.capabilityForFamily(payload.familyId());
        if (!validContainer(payload) || capability == 0L || !targetSupports(target, capability)
                || nativeObserverCount(target.getUUID()) == 0 || !acceptScreenSequence(target.getUUID(), payload.sequence())) return;
        relayToNativeObservers(target, ObserverNativeScreenPayloads.ContainerRelay.TYPE,
                new ObserverNativeScreenPayloads.ContainerRelay(target.getUUID(), payload.protocolVersion(), payload.sequence(),
                        payload.open(), payload.familyId(), payload.screenClass(), payload.title(), payload.contentWidth(),
                        payload.contentHeight(), payload.mouseX(), payload.mouseY(), payload.slots()), capability);
    }

    public static void acceptFurnaceState(ServerPlayer target, ObserverNativeScreenPayloads.FurnaceState payload) {
        long capability = ObserverNativeScreenPayloads.capabilityForFamily(payload.familyId());
        if (!validFurnace(payload) || capability != ObserverNativeScreenPayloads.CAPABILITY_FURNACE
                || !targetSupports(target, capability) || nativeObserverCount(target.getUUID()) == 0
                || !acceptScreenSequence(target.getUUID(), payload.sequence())) return;
        relayToNativeObservers(target, ObserverNativeScreenPayloads.FurnaceRelay.TYPE,
                new ObserverNativeScreenPayloads.FurnaceRelay(target.getUUID(), payload.protocolVersion(), payload.sequence(),
                        payload.open(), payload.familyId(), payload.screenClass(), payload.title(), payload.contentWidth(),
                        payload.contentHeight(), payload.mouseX(), payload.mouseY(), payload.slots(), payload.cookProgress(),
                        payload.fuelProgress(), payload.lit()), capability);
    }

    public static void acceptBookState(ServerPlayer target, ObserverBookScreenPayloads.BookState payload) {
        long capability = ObserverNativeScreenPayloads.capabilityForFamily(payload.familyId());
        if (!validBook(payload) || capability != ObserverNativeScreenPayloads.CAPABILITY_BOOK
                || !targetSupports(target, capability) || nativeObserverCount(target.getUUID()) == 0
                || !acceptSequence(LAST_BOOK_SEQUENCE_BY_TARGET, target.getUUID(), payload.sequence())) return;
        relayToNativeObservers(target, ObserverBookScreenPayloads.BookRelay.TYPE,
                new ObserverBookScreenPayloads.BookRelay(target.getUUID(), payload.protocolVersion(), payload.sequence(),
                        payload.open(), payload.familyId(), payload.variant(), payload.screenClass(), payload.title(),
                        payload.pageIndex(), payload.pageCount(), payload.pageText(), payload.bookTitle(), payload.author()), capability);
    }

    public static void acceptCraftingState(ServerPlayer target, ObserverCraftingScreenPayloads.CraftingState payload) {
        long capability = ObserverNativeScreenPayloads.capabilityForFamily(payload.familyId());
        if (!validCrafting(payload) || capability != ObserverNativeScreenPayloads.CAPABILITY_CRAFTING
                || !targetSupports(target, capability) || nativeObserverCount(target.getUUID()) == 0
                || !acceptSequence(LAST_CRAFTING_SEQUENCE_BY_TARGET, target.getUUID(), payload.sequence())) return;
        relayToNativeObservers(target, ObserverCraftingScreenPayloads.CraftingRelay.TYPE,
                new ObserverCraftingScreenPayloads.CraftingRelay(target.getUUID(), payload.protocolVersion(), payload.sequence(),
                        payload.open(), payload.familyId(), payload.variant(), payload.screenClass(), payload.title(),
                        payload.contentWidth(), payload.contentHeight(), payload.mouseX(), payload.mouseY(),
                        payload.gridWidth(), payload.gridHeight(), payload.resultSlotIndex(), payload.slots()), capability);
    }

    public static void acceptMerchantState(ServerPlayer target, ObserverMerchantScreenPayloads.MerchantState payload) {
        long capability = ObserverNativeScreenPayloads.capabilityForFamily(payload.familyId());
        if (!validMerchant(payload) || capability != ObserverNativeScreenPayloads.CAPABILITY_MERCHANT
                || !targetSupports(target, capability) || nativeObserverCount(target.getUUID()) == 0
                || !acceptSequence(LAST_MERCHANT_SEQUENCE_BY_TARGET, target.getUUID(), payload.sequence())) return;
        relayToNativeObservers(target, ObserverMerchantScreenPayloads.MerchantRelay.TYPE,
                new ObserverMerchantScreenPayloads.MerchantRelay(target.getUUID(), payload.protocolVersion(), payload.sequence(),
                        payload.open(), payload.familyId(), payload.variant(), payload.screenClass(), payload.title(),
                        payload.selectedOffer(), payload.traderLevel(), payload.traderXp(), payload.futureTraderXp(),
                        payload.showProgressBar(), payload.canRestock(), payload.offers()), capability);
    }

    public static void acceptAnvilState(ServerPlayer target, ObserverAnvilScreenPayloads.AnvilState payload) {
        long capability = ObserverNativeScreenPayloads.capabilityForFamily(payload.familyId());
        if (!validAnvil(payload) || capability != ObserverNativeScreenPayloads.CAPABILITY_ANVIL
                || !targetSupports(target, capability) || nativeObserverCount(target.getUUID()) == 0
                || !acceptSequence(LAST_ANVIL_SEQUENCE_BY_TARGET, target.getUUID(), payload.sequence())) return;
        relayToNativeObservers(target, ObserverAnvilScreenPayloads.AnvilRelay.TYPE,
                new ObserverAnvilScreenPayloads.AnvilRelay(target.getUUID(), payload.protocolVersion(), payload.sequence(),
                        payload.open(), payload.familyId(), payload.screenClass(), payload.title(), payload.itemName(),
                        payload.levelCost(), payload.tooExpensive(), payload.resultAvailable(), payload.slots()), capability);
    }

    public static void acceptEnchantingState(ServerPlayer target, ObserverEnchantingScreenPayloads.EnchantingState payload) {
        long capability = ObserverNativeScreenPayloads.capabilityForFamily(payload.familyId());
        if (!validEnchanting(payload) || capability != ObserverNativeScreenPayloads.CAPABILITY_ENCHANTING
                || !targetSupports(target, capability) || nativeObserverCount(target.getUUID()) == 0
                || !acceptSequence(LAST_ENCHANTING_SEQUENCE_BY_TARGET, target.getUUID(), payload.sequence())) return;
        relayToNativeObservers(target, ObserverEnchantingScreenPayloads.EnchantingRelay.TYPE,
                new ObserverEnchantingScreenPayloads.EnchantingRelay(target.getUUID(), payload.protocolVersion(), payload.sequence(),
                        payload.open(), payload.familyId(), payload.screenClass(), payload.title(), payload.playerLevel(),
                        payload.lapisCount(), payload.options(), payload.slots()), capability);
    }

    private static boolean targetSupports(ServerPlayer target, long capability) {
        return ObserverNativeScreenPayloads.supports(screenCapabilitiesForTarget(target.getUUID()), capability);
    }

    private static boolean acceptScreenSequence(UUID targetId, long sequence) {
        return acceptSequence(LAST_SCREEN_SEQUENCE_BY_TARGET, targetId, sequence);
    }

    private static boolean acceptSequence(Map<UUID, Long> state, UUID targetId, long sequence) {
        long last = state.getOrDefault(targetId, -1L);
        if (sequence <= last) return false;
        state.put(targetId, sequence);
        return true;
    }

    private static <T extends CustomPacketPayload> void relayToNativeObservers(ServerPlayer target,
            CustomPacketPayload.Type<T> type, T relay, long requiredCapability) {
        MinecraftServer server = target.level().getServer();
        for (Map.Entry<UUID, UUID> entry : TARGET_BY_OBSERVER.entrySet()) {
            if (!target.getUUID().equals(entry.getValue())) continue;
            UUID observerId = entry.getKey();
            if (requiredCapability != 0L && !ObserverNativeScreenPayloads.supports(
                    SCREEN_CAPABILITIES_BY_OBSERVER.getOrDefault(observerId, 0L), requiredCapability)) continue;
            ServerPlayer observer = server.getPlayerList().getPlayer(observerId);
            if (observer != null && observer.isSpectator() && ServerPlayNetworking.canSend(observer, type)) {
                ServerPlayNetworking.send(observer, relay);
            }
        }
    }

    private static boolean valid(ObserverNativePayloads.NativeViewState p) {
        return p.protocolVersion() == ObserverNativePayloads.PROTOCOL_VERSION && p.sequence() >= 0L
                && Float.isFinite(p.yaw()) && Float.isFinite(p.pitch()) && Float.isFinite(p.health())
                && Float.isFinite(p.maxHealth()) && Float.isFinite(p.saturation())
                && Float.isFinite(p.experienceProgress()) && p.health() >= 0.0F && p.maxHealth() > 0.0F
                && p.health() <= p.maxHealth() + 0.001F && p.food() >= 0 && p.food() <= 20
                && p.saturation() >= 0.0F && p.saturation() <= 20.0F
                && p.experienceProgress() >= 0.0F && p.experienceProgress() <= 1.001F
                && p.experienceLevel() >= 0 && p.selectedHotbarSlot() >= 0 && p.selectedHotbarSlot() < 9;
    }

    private static boolean validContainer(ObserverNativeScreenPayloads.ContainerState p) {
        return p.protocolVersion() == ObserverNativeScreenPayloads.SCREEN_PROTOCOL_VERSION
                && ObserverNativeScreenPayloads.FAMILY_CONTAINER_SLOTS.equals(p.familyId()) && p.sequence() >= 0L
                && validSlots(p.slots()) && validScreenGeometry(p.open(), p.contentWidth(), p.contentHeight(), p.mouseX(), p.mouseY(), p.slots());
    }

    private static boolean validFurnace(ObserverNativeScreenPayloads.FurnaceState p) {
        return p.protocolVersion() == ObserverNativeScreenPayloads.FURNACE_PROTOCOL_VERSION
                && ObserverNativeScreenPayloads.FAMILY_FURNACE.equals(p.familyId()) && p.sequence() >= 0L
                && Float.isFinite(p.cookProgress()) && Float.isFinite(p.fuelProgress())
                && p.cookProgress() >= 0.0F && p.cookProgress() <= 1.001F
                && p.fuelProgress() >= 0.0F && p.fuelProgress() <= 1.001F && validSlots(p.slots())
                && validScreenGeometry(p.open(), p.contentWidth(), p.contentHeight(), p.mouseX(), p.mouseY(), p.slots());
    }

    private static boolean validBook(ObserverBookScreenPayloads.BookState p) {
        if (p.protocolVersion() != ObserverBookScreenPayloads.PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_BOOK.equals(p.familyId()) || p.sequence() < 0L) return false;
        if (!p.open()) return p.pageIndex() == 0 && p.pageCount() == 0;
        if (!(ObserverBookScreenPayloads.VARIANT_WRITTEN.equals(p.variant())
                || ObserverBookScreenPayloads.VARIANT_WRITABLE.equals(p.variant())
                || ObserverBookScreenPayloads.VARIANT_LECTERN.equals(p.variant())
                || ObserverBookScreenPayloads.VARIANT_SIGNING.equals(p.variant()))) return false;
        if (p.pageCount() < 0 || p.pageCount() > ObserverBookScreenPayloads.MAX_PAGE_COUNT) return false;
        return p.pageCount() == 0 ? p.pageIndex() == 0 : p.pageIndex() >= 0 && p.pageIndex() < p.pageCount();
    }

    private static boolean validCrafting(ObserverCraftingScreenPayloads.CraftingState p) {
        if (p.protocolVersion() != ObserverCraftingScreenPayloads.PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_CRAFTING.equals(p.familyId()) || p.sequence() < 0L || !validSlots(p.slots())) return false;
        if (!p.open()) return p.slots().isEmpty() && p.gridWidth() == 0 && p.gridHeight() == 0;
        boolean player = ObserverCraftingScreenPayloads.VARIANT_PLAYER_2X2.equals(p.variant()) && p.gridWidth() == 2 && p.gridHeight() == 2;
        boolean table = ObserverCraftingScreenPayloads.VARIANT_TABLE_3X3.equals(p.variant()) && p.gridWidth() == 3 && p.gridHeight() == 3;
        return (player || table) && p.resultSlotIndex() >= 0 && p.resultSlotIndex() < ObserverNativeScreenPayloads.MAX_SLOTS
                && validScreenGeometry(true, p.contentWidth(), p.contentHeight(), p.mouseX(), p.mouseY(), p.slots());
    }

    private static boolean validMerchant(ObserverMerchantScreenPayloads.MerchantState p) {
        if (p.protocolVersion() != ObserverMerchantScreenPayloads.PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_MERCHANT.equals(p.familyId()) || p.sequence() < 0L) return false;
        if (!p.open()) return p.offers().isEmpty() && p.traderLevel() == 0 && p.traderXp() == 0 && p.futureTraderXp() == 0;
        if (!ObserverMerchantScreenPayloads.VARIANT_VANILLA.equals(p.variant())
                || p.offers().size() > ObserverMerchantScreenPayloads.MAX_OFFERS
                || p.traderLevel() < 0 || p.traderLevel() > 5 || p.traderXp() < 0 || p.futureTraderXp() < 0
                || p.selectedOffer() < 0 || (!p.offers().isEmpty() && p.selectedOffer() >= p.offers().size())) return false;
        for (ObserverMerchantScreenPayloads.OfferState offer : p.offers()) {
            if (offer.index() < 0 || offer.index() >= ObserverMerchantScreenPayloads.MAX_OFFERS
                    || offer.uses() < 0 || offer.maxUses() < 0 || offer.uses() > offer.maxUses() || offer.xp() < 0
                    || !validMerchantItem(offer.costA()) || !validMerchantItem(offer.costB()) || !validMerchantItem(offer.result())) return false;
        }
        return true;
    }

    private static boolean validAnvil(ObserverAnvilScreenPayloads.AnvilState p) {
        if (p.protocolVersion() != ObserverAnvilScreenPayloads.PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_ANVIL.equals(p.familyId()) || p.sequence() < 0L
                || p.levelCost() < 0 || p.levelCost() > 32767 || !validSlots(p.slots())) return false;
        if (!p.open()) return p.slots().isEmpty() && p.levelCost() == 0 && !p.tooExpensive() && !p.resultAvailable();
        if (p.slots().size() < 3 || p.itemName().length() > 50) return false;
        return !p.tooExpensive() || p.resultAvailable();
    }

    private static boolean validEnchanting(ObserverEnchantingScreenPayloads.EnchantingState p) {
        if (p.protocolVersion() != ObserverEnchantingScreenPayloads.PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_ENCHANTING.equals(p.familyId()) || p.sequence() < 0L
                || p.playerLevel() < 0 || p.playerLevel() > 32767 || p.lapisCount() < 0 || p.lapisCount() > 127
                || !validSlots(p.slots())) return false;
        if (!p.open()) return p.options().isEmpty() && p.slots().isEmpty() && p.playerLevel() == 0 && p.lapisCount() == 0;
        if (p.options().size() != ObserverEnchantingScreenPayloads.OPTION_COUNT || p.slots().size() < 2) return false;
        boolean[] seen = new boolean[ObserverEnchantingScreenPayloads.OPTION_COUNT];
        for (ObserverEnchantingScreenPayloads.OptionState option : p.options()) {
            if (option.index() < 0 || option.index() >= seen.length || seen[option.index()] || option.cost() < 0
                    || option.cost() > 32767 || option.enchantClue() < -1 || option.levelClue() < -1
                    || option.levelClue() > 255) return false;
            seen[option.index()] = true;
        }
        return true;
    }

    private static boolean validMerchantItem(ObserverMerchantScreenPayloads.ItemState item) {
        return item != null && item.count() >= 0 && item.count() <= 127 && item.damage() >= 0
                && (item.count() > 0 || item.itemId().isEmpty());
    }

    private static boolean validScreenGeometry(boolean open, int width, int height, int mouseX, int mouseY,
                                                List<ObserverNativeScreenPayloads.SlotState> slots) {
        if (!open) return slots.isEmpty();
        return width >= 64 && width <= 512 && height >= 64 && height <= 512
                && mouseX >= -2048 && mouseX <= 2048 && mouseY >= -2048 && mouseY <= 2048;
    }

    private static boolean validSlots(List<ObserverNativeScreenPayloads.SlotState> slots) {
        if (slots.size() > ObserverNativeScreenPayloads.MAX_SLOTS) return false;
        for (ObserverNativeScreenPayloads.SlotState slot : slots) {
            if (slot.index() < 0 || slot.x() < -64 || slot.x() > 512 || slot.y() < -64 || slot.y() > 512
                    || slot.count() < 0 || slot.count() > 127 || slot.damage() < 0) return false;
        }
        return true;
    }

    private static long negotiatedScreenCapabilities(ServerPlayer observer) {
        long capabilities = 0L;
        if (ServerPlayNetworking.canSend(observer, ObserverNativeScreenPayloads.ContainerRelay.TYPE)) capabilities |= ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS;
        if (ServerPlayNetworking.canSend(observer, ObserverNativeScreenPayloads.FurnaceRelay.TYPE)) capabilities |= ObserverNativeScreenPayloads.CAPABILITY_FURNACE;
        if (ServerPlayNetworking.canSend(observer, ObserverBookScreenPayloads.BookRelay.TYPE)) capabilities |= ObserverNativeScreenPayloads.CAPABILITY_BOOK;
        if (ServerPlayNetworking.canSend(observer, ObserverCraftingScreenPayloads.CraftingRelay.TYPE)) capabilities |= ObserverNativeScreenPayloads.CAPABILITY_CRAFTING;
        if (ServerPlayNetworking.canSend(observer, ObserverMerchantScreenPayloads.MerchantRelay.TYPE)) capabilities |= ObserverNativeScreenPayloads.CAPABILITY_MERCHANT;
        if (ServerPlayNetworking.canSend(observer, ObserverAnvilScreenPayloads.AnvilRelay.TYPE)) capabilities |= ObserverNativeScreenPayloads.CAPABILITY_ANVIL;
        if (ServerPlayNetworking.canSend(observer, ObserverEnchantingScreenPayloads.EnchantingRelay.TYPE)) capabilities |= ObserverNativeScreenPayloads.CAPABILITY_ENCHANTING;
        return ObserverNativeScreenPayloads.sanitizeCapabilities(capabilities);
    }

    private static long screenCapabilitiesForTarget(UUID targetId) {
        long capabilities = 0L;
        for (Map.Entry<UUID, UUID> entry : TARGET_BY_OBSERVER.entrySet()) {
            if (targetId.equals(entry.getValue())) capabilities |= SCREEN_CAPABILITIES_BY_OBSERVER.getOrDefault(entry.getKey(), 0L);
        }
        return ObserverNativeScreenPayloads.sanitizeCapabilities(capabilities);
    }

    private static int nativeObserverCount(UUID targetId) {
        int count = 0;
        for (UUID value : TARGET_BY_OBSERVER.values()) if (targetId.equals(value)) count++;
        return count;
    }

    private static void updateTargetControl(MinecraftServer server, UUID targetId) {
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null || !ServerPlayNetworking.canSend(target, ObserverNativePayloads.NativeControl.TYPE)) {
            clearTargetSequences(targetId);
            return;
        }
        boolean enabled = nativeObserverCount(targetId) > 0;
        long capabilities = enabled ? screenCapabilitiesForTarget(targetId) : 0L;
        if (!enabled) clearTargetSequences(targetId);
        ServerPlayNetworking.send(target, new ObserverNativePayloads.NativeControl(enabled,
                ObserverNativePayloads.PROTOCOL_VERSION, enabled ? ObserverNativePayloads.TARGET_STATE_FPS : 0, capabilities));
    }

    private static void clearTargetSequences(UUID targetId) {
        LAST_SEQUENCE_BY_TARGET.remove(targetId);
        LAST_SCREEN_SEQUENCE_BY_TARGET.remove(targetId);
        LAST_BOOK_SEQUENCE_BY_TARGET.remove(targetId);
        LAST_CRAFTING_SEQUENCE_BY_TARGET.remove(targetId);
        LAST_MERCHANT_SEQUENCE_BY_TARGET.remove(targetId);
        LAST_ANVIL_SEQUENCE_BY_TARGET.remove(targetId);
        LAST_ENCHANTING_SEQUENCE_BY_TARGET.remove(targetId);
    }
}
