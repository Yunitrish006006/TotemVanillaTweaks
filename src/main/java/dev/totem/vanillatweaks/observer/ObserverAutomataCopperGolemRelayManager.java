package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverAutomataCopperGolemPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Optional-module relay for TotemAutomata Copper Golem screen semantics. */
public final class ObserverAutomataCopperGolemRelayManager {
    private static final Map<UUID, Long> LAST_SEQUENCE_BY_TARGET = new HashMap<>();
    private static final Field TARGET_BY_OBSERVER = staticField("TARGET_BY_OBSERVER");
    private static final Field SCREEN_CAPABILITIES_BY_OBSERVER = staticField("SCREEN_CAPABILITIES_BY_OBSERVER");

    private ObserverAutomataCopperGolemRelayManager() {}

    public static void acceptState(ServerPlayer target, ObserverAutomataCopperGolemPayloads.CopperGolemState payload) {
        if (!valid(payload)) return;
        UUID targetId = target.getUUID();
        if (!hasCapableObserver(targetId)) return;
        long last = LAST_SEQUENCE_BY_TARGET.getOrDefault(targetId, -1L);
        if (payload.sequence() <= last) return;
        LAST_SEQUENCE_BY_TARGET.put(targetId, payload.sequence());

        MinecraftServer server = target.level().getServer();
        var relay = ObserverAutomataCopperGolemPayloads.relay(targetId, payload);
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (!targetId.equals(entry.getValue())) continue;
            UUID observerId = entry.getKey();
            long capabilities = capabilitiesByObserver().getOrDefault(observerId, 0L);
            if (!ObserverNativeScreenPayloads.supports(
                    capabilities, ObserverNativeScreenPayloads.CAPABILITY_AUTOMATA_COPPER_GOLEM)) continue;
            ServerPlayer observer = server.getPlayerList().getPlayer(observerId);
            if (observer != null && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverAutomataCopperGolemPayloads.CopperGolemRelay.TYPE)) {
                ServerPlayNetworking.send(observer, relay);
            }
        }
    }

    public static void clearTarget(UUID targetId) {
        LAST_SEQUENCE_BY_TARGET.remove(targetId);
    }

    private static boolean hasCapableObserver(UUID targetId) {
        for (Map.Entry<UUID, UUID> entry : targetByObserver().entrySet()) {
            if (!targetId.equals(entry.getValue())) continue;
            long capabilities = capabilitiesByObserver().getOrDefault(entry.getKey(), 0L);
            if (ObserverNativeScreenPayloads.supports(
                    capabilities, ObserverNativeScreenPayloads.CAPABILITY_AUTOMATA_COPPER_GOLEM)) return true;
        }
        return false;
    }

    static boolean valid(ObserverAutomataCopperGolemPayloads.CopperGolemState p) {
        if (p.protocolVersion() != ObserverAutomataCopperGolemPayloads.PROTOCOL_VERSION
                || p.sequence() < 0L
                || !ObserverNativeScreenPayloads.FAMILY_AUTOMATA_COPPER_GOLEM.equals(p.familyId())) return false;
        if (p.open() && (!ObserverAutomataCopperGolemPayloads.SCREEN_CLASS.equals(p.screenClass())
                || !ObserverAutomataCopperGolemPayloads.SCREEN_TITLE.equals(p.title()))) return false;
        if (!validConfiguredToken(p.editorApiUrl()) || !validConfiguredToken(p.editorModel())
                || !validConfiguredToken(p.editorGatheringPrompt()) || !validConfiguredToken(p.editorBindingPrompt())
                || !validCacheToken(p.cacheValueText()) || !validBindingPrompt(p.sourceContainer())) return false;
        for (var binding : p.bindings()) if (!validBindingPrompt(binding)) return false;
        if (!p.open()) {
            return p.bindings().isEmpty() && p.slots().isEmpty()
                    && p.gatheringManualTargets().isEmpty()
                    && p.gatheringLlmAllowedBlockIds().isEmpty() && p.gatheringLlmDeniedBlockIds().isEmpty()
                    && p.gatheringLlmAllowedTags().isEmpty() && p.gatheringLlmDeniedTags().isEmpty();
        }
        if (!("sorting".equals(p.mode()) || "gathering".equals(p.mode()))) return false;
        if (!("bindings".equals(p.tab()) || "llm".equals(p.tab()))) return false;
        if (!validIdentifier(p.fuelItemId(), true) || !validIdentifier(p.toolItemId(), true)
                || !validIdentifier(p.storageItemId(), true)
                || !validIdentifiers(p.gatheringManualTargets())
                || !validIdentifiers(p.gatheringLlmAllowedBlockIds())
                || !validIdentifiers(p.gatheringLlmDeniedBlockIds())
                || !validIdentifiers(p.gatheringLlmAllowedTags())
                || !validIdentifiers(p.gatheringLlmDeniedTags())) return false;
        if (p.bindings().size() > ObserverAutomataCopperGolemPayloads.MAX_BINDINGS
                || p.gatheringManualTargets().size() > ObserverAutomataCopperGolemPayloads.MAX_VALUES
                || p.gatheringLlmAllowedBlockIds().size() > ObserverAutomataCopperGolemPayloads.MAX_VALUES
                || p.gatheringLlmDeniedBlockIds().size() > ObserverAutomataCopperGolemPayloads.MAX_VALUES
                || p.gatheringLlmAllowedTags().size() > ObserverAutomataCopperGolemPayloads.MAX_VALUES
                || p.gatheringLlmDeniedTags().size() > ObserverAutomataCopperGolemPayloads.MAX_VALUES) return false;
        if (p.selectedBinding() < -1
                || (p.selectedBinding() >= 0 && p.selectedBinding() >= p.bindings().size())
                || p.bindingScroll() < 0) return false;
        if (!validCount(p.fuelCount()) || p.fuelTicks() < 0
                || !validCount(p.toolCount()) || p.toolDamage() < 0 || p.toolMaxDamage() < 0
                || (p.toolMaxDamage() > 0 && p.toolDamage() > p.toolMaxDamage())
                || !validCount(p.storageCount()) || p.llmActiveCount() < 0
                || p.gatheringLlmCachedBlockIds() < 0 || p.gatheringLlmCachedTags() < 0) return false;
        if (!validBinding(p.sourceContainer()) || !validArea(p.gatheringArea())) return false;
        for (var binding : p.bindings()) if (!validBinding(binding)) return false;
        return validSlots(p.slots());
    }

    private static boolean validCount(int count) {
        return count >= 0 && count <= 127;
    }

    private static boolean validBinding(ObserverAutomataCopperGolemPayloads.BindingState b) {
        if (b == null) return true;
        if (b.llmCachedItemIds() < 0 || b.llmCachedTags() < 0) return false;
        return validIdentifier(b.dimension(), false)
                && validIdentifier(b.blockId(), false)
                && validIdentifier(b.itemId(), true)
                && b.llmAllowedItemIds().size() <= ObserverAutomataCopperGolemPayloads.MAX_VALUES
                && b.llmDeniedItemIds().size() <= ObserverAutomataCopperGolemPayloads.MAX_VALUES
                && b.llmAllowedTags().size() <= ObserverAutomataCopperGolemPayloads.MAX_VALUES
                && b.llmDeniedTags().size() <= ObserverAutomataCopperGolemPayloads.MAX_VALUES
                && validIdentifiers(b.llmAllowedItemIds())
                && validIdentifiers(b.llmDeniedItemIds())
                && validIdentifiers(b.llmAllowedTags())
                && validIdentifiers(b.llmDeniedTags());
    }

    private static boolean validBindingPrompt(ObserverAutomataCopperGolemPayloads.BindingState binding) {
        return binding == null || validConfiguredToken(binding.llmPrompt());
    }

    private static boolean validArea(ObserverAutomataCopperGolemPayloads.GatheringAreaState area) {
        return area == null || validIdentifier(area.dimension(), false);
    }

    private static boolean validConfiguredToken(String value) {
        return ObserverAutomataCopperGolemPayloads.isConfiguredToken(value);
    }

    private static boolean validCacheToken(String value) {
        return ObserverAutomataCopperGolemPayloads.isValidToken(value);
    }

    private static boolean validSlots(List<ObserverNativeScreenPayloads.SlotState> slots) {
        if (slots.size() > ObserverNativeScreenPayloads.MAX_SLOTS) return false;
        for (var slot : slots) {
            if (slot.index() < 0 || slot.x() < -1024 || slot.x() > 1024 || slot.y() < -1024 || slot.y() > 1024
                    || slot.count() < 0 || slot.count() > 127 || slot.damage() < 0
                    || !validIdentifier(slot.itemId(), true)) return false;
        }
        return true;
    }

    private static boolean validIdentifiers(List<String> values) {
        for (String value : values) if (!validIdentifier(value, false)) return false;
        return true;
    }

    private static boolean validIdentifier(String value, boolean allowBlank) {
        if (value == null || value.isBlank()) return allowBlank;
        return Identifier.tryParse(value) != null;
    }

    private static Field staticField(String name) {
        try {
            Field field = ObserverNativeSessionManager.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, UUID> targetByObserver() {
        try {
            return (Map<UUID, UUID>) TARGET_BY_OBSERVER.get(null);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Long> capabilitiesByObserver() {
        try {
            return (Map<UUID, Long>) SCREEN_CAPABILITIES_BY_OBSERVER.get(null);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }
    }
}
