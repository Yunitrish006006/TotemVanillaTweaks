package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverBookScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
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

    private ObserverNativeSessionManager() {
    }

    public static boolean supports(ServerPlayer observer, ServerPlayer target) {
        return ServerPlayNetworking.canSend(observer, ObserverNativePayloads.NativeSession.TYPE)
                && ServerPlayNetworking.canSend(observer, ObserverNativePayloads.NativeViewRelay.TYPE)
                && ServerPlayNetworking.canSend(observer, ObserverPayloads.ScreenRelay.TYPE)
                && ServerPlayNetworking.canSend(target, ObserverNativePayloads.NativeControl.TYPE);
    }

    public static boolean start(ServerPlayer observer, ServerPlayer target) {
        if (!supports(observer, target)) {
            return false;
        }
        long screenCapabilities = negotiatedScreenCapabilities(observer);
        TARGET_BY_OBSERVER.put(observer.getUUID(), target.getUUID());
        SCREEN_CAPABILITIES_BY_OBSERVER.put(observer.getUUID(), screenCapabilities);
        ServerPlayNetworking.send(observer, new ObserverNativePayloads.NativeSession(
                true,
                target.getUUID(),
                target.getGameProfile().name(),
                ObserverNativePayloads.PROTOCOL_VERSION,
                screenCapabilities
        ));
        updateTargetControl(target.level().getServer(), target.getUUID());
        return true;
    }

    public static boolean stop(ServerPlayer observer) {
        UUID observerId = observer.getUUID();
        UUID targetId = TARGET_BY_OBSERVER.remove(observerId);
        SCREEN_CAPABILITIES_BY_OBSERVER.remove(observerId);
        boolean wasNative = targetId != null;
        if (ServerPlayNetworking.canSend(observer, ObserverNativePayloads.NativeSession.TYPE)) {
            ServerPlayNetworking.send(observer, new ObserverNativePayloads.NativeSession(
                    false,
                    EMPTY_TARGET,
                    "",
                    ObserverNativePayloads.PROTOCOL_VERSION,
                    0L
            ));
        }
        if (targetId != null) {
            updateTargetControl(observer.level().getServer(), targetId);
        }
        return wasNative;
    }

    public static void removeOfflineObserver(MinecraftServer server, UUID observerId) {
        UUID targetId = TARGET_BY_OBSERVER.remove(observerId);
        SCREEN_CAPABILITIES_BY_OBSERVER.remove(observerId);
        if (targetId != null) {
            updateTargetControl(server, targetId);
        }
    }

    public static void refreshTargetControl(MinecraftServer server, UUID targetId) {
        updateTargetControl(server, targetId);
    }

    public static void acceptViewState(ServerPlayer target, ObserverNativePayloads.NativeViewState payload) {
        if (!valid(payload) || nativeObserverCount(target.getUUID()) == 0) {
            return;
        }
        long lastSequence = LAST_SEQUENCE_BY_TARGET.getOrDefault(target.getUUID(), -1L);
        if (payload.sequence() <= lastSequence) {
            return;
        }
        LAST_SEQUENCE_BY_TARGET.put(target.getUUID(), payload.sequence());

        ObserverNativePayloads.NativeViewRelay relay = new ObserverNativePayloads.NativeViewRelay(
                target.getUUID(),
                payload.protocolVersion(),
                payload.sequence(),
                payload.yaw(),
                payload.pitch(),
                payload.health(),
                payload.maxHealth(),
                payload.food(),
                payload.saturation(),
                payload.experienceProgress(),
                payload.experienceLevel(),
                payload.selectedHotbarSlot(),
                payload.sprinting(),
                payload.crouching(),
                payload.usingItem()
        );
        relayToNativeObservers(target, ObserverNativePayloads.NativeViewRelay.TYPE, relay, 0L);
    }

    public static void acceptContainerState(ServerPlayer target, ObserverNativeScreenPayloads.ContainerState payload) {
        long familyCapability = ObserverNativeScreenPayloads.capabilityForFamily(payload.familyId());
        if (!validContainer(payload)
                || familyCapability == 0L
                || !ObserverNativeScreenPayloads.supports(
                        screenCapabilitiesForTarget(target.getUUID()),
                        familyCapability
                )
                || nativeObserverCount(target.getUUID()) == 0
                || !acceptScreenSequence(target.getUUID(), payload.sequence())) {
            return;
        }

        ObserverNativeScreenPayloads.ContainerRelay relay = new ObserverNativeScreenPayloads.ContainerRelay(
                target.getUUID(),
                payload.protocolVersion(),
                payload.sequence(),
                payload.open(),
                payload.familyId(),
                payload.screenClass(),
                payload.title(),
                payload.contentWidth(),
                payload.contentHeight(),
                payload.mouseX(),
                payload.mouseY(),
                payload.slots()
        );
        relayToNativeObservers(
                target,
                ObserverNativeScreenPayloads.ContainerRelay.TYPE,
                relay,
                familyCapability
        );
    }

    public static void acceptFurnaceState(ServerPlayer target, ObserverNativeScreenPayloads.FurnaceState payload) {
        long familyCapability = ObserverNativeScreenPayloads.capabilityForFamily(payload.familyId());
        if (!validFurnace(payload)
                || familyCapability != ObserverNativeScreenPayloads.CAPABILITY_FURNACE
                || !ObserverNativeScreenPayloads.supports(
                        screenCapabilitiesForTarget(target.getUUID()),
                        familyCapability
                )
                || nativeObserverCount(target.getUUID()) == 0
                || !acceptScreenSequence(target.getUUID(), payload.sequence())) {
            return;
        }

        ObserverNativeScreenPayloads.FurnaceRelay relay = new ObserverNativeScreenPayloads.FurnaceRelay(
                target.getUUID(),
                payload.protocolVersion(),
                payload.sequence(),
                payload.open(),
                payload.familyId(),
                payload.screenClass(),
                payload.title(),
                payload.contentWidth(),
                payload.contentHeight(),
                payload.mouseX(),
                payload.mouseY(),
                payload.slots(),
                payload.cookProgress(),
                payload.fuelProgress(),
                payload.lit()
        );
        relayToNativeObservers(
                target,
                ObserverNativeScreenPayloads.FurnaceRelay.TYPE,
                relay,
                familyCapability
        );
    }

    public static void acceptBookState(ServerPlayer target, ObserverBookScreenPayloads.BookState payload) {
        long familyCapability = ObserverNativeScreenPayloads.capabilityForFamily(payload.familyId());
        if (!validBook(payload)
                || familyCapability != ObserverNativeScreenPayloads.CAPABILITY_BOOK
                || !ObserverNativeScreenPayloads.supports(
                        screenCapabilitiesForTarget(target.getUUID()),
                        familyCapability
                )
                || nativeObserverCount(target.getUUID()) == 0
                || !acceptBookSequence(target.getUUID(), payload.sequence())) {
            return;
        }

        ObserverBookScreenPayloads.BookRelay relay = new ObserverBookScreenPayloads.BookRelay(
                target.getUUID(),
                payload.protocolVersion(),
                payload.sequence(),
                payload.open(),
                payload.familyId(),
                payload.variant(),
                payload.screenClass(),
                payload.title(),
                payload.pageIndex(),
                payload.pageCount(),
                payload.pageText(),
                payload.bookTitle(),
                payload.author()
        );
        relayToNativeObservers(
                target,
                ObserverBookScreenPayloads.BookRelay.TYPE,
                relay,
                familyCapability
        );
    }

    private static boolean acceptScreenSequence(UUID targetId, long sequence) {
        long lastSequence = LAST_SCREEN_SEQUENCE_BY_TARGET.getOrDefault(targetId, -1L);
        if (sequence <= lastSequence) {
            return false;
        }
        LAST_SCREEN_SEQUENCE_BY_TARGET.put(targetId, sequence);
        return true;
    }

    private static boolean acceptBookSequence(UUID targetId, long sequence) {
        long lastSequence = LAST_BOOK_SEQUENCE_BY_TARGET.getOrDefault(targetId, -1L);
        if (sequence <= lastSequence) {
            return false;
        }
        LAST_BOOK_SEQUENCE_BY_TARGET.put(targetId, sequence);
        return true;
    }

    private static <T extends CustomPacketPayload> void relayToNativeObservers(
            ServerPlayer target,
            CustomPacketPayload.Type<T> type,
            T relay,
            long requiredScreenCapability
    ) {
        MinecraftServer server = target.level().getServer();
        for (Map.Entry<UUID, UUID> entry : TARGET_BY_OBSERVER.entrySet()) {
            if (!target.getUUID().equals(entry.getValue())) {
                continue;
            }
            UUID observerId = entry.getKey();
            if (requiredScreenCapability != 0L
                    && !ObserverNativeScreenPayloads.supports(
                            SCREEN_CAPABILITIES_BY_OBSERVER.getOrDefault(observerId, 0L),
                            requiredScreenCapability
                    )) {
                continue;
            }
            ServerPlayer observer = server.getPlayerList().getPlayer(observerId);
            if (observer != null && observer.isSpectator() && ServerPlayNetworking.canSend(observer, type)) {
                ServerPlayNetworking.send(observer, relay);
            }
        }
    }

    private static boolean valid(ObserverNativePayloads.NativeViewState payload) {
        return payload.protocolVersion() == ObserverNativePayloads.PROTOCOL_VERSION
                && payload.sequence() >= 0L
                && Float.isFinite(payload.yaw())
                && Float.isFinite(payload.pitch())
                && Float.isFinite(payload.health())
                && Float.isFinite(payload.maxHealth())
                && Float.isFinite(payload.saturation())
                && Float.isFinite(payload.experienceProgress())
                && payload.health() >= 0.0F
                && payload.maxHealth() > 0.0F
                && payload.health() <= payload.maxHealth() + 0.001F
                && payload.food() >= 0 && payload.food() <= 20
                && payload.saturation() >= 0.0F && payload.saturation() <= 20.0F
                && payload.experienceProgress() >= 0.0F && payload.experienceProgress() <= 1.001F
                && payload.experienceLevel() >= 0
                && payload.selectedHotbarSlot() >= 0 && payload.selectedHotbarSlot() < 9;
    }

    private static boolean validContainer(ObserverNativeScreenPayloads.ContainerState payload) {
        if (payload.protocolVersion() != ObserverNativeScreenPayloads.SCREEN_PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_CONTAINER_SLOTS.equals(payload.familyId())
                || payload.sequence() < 0L
                || !validSlots(payload.slots())) {
            return false;
        }
        return validScreenGeometry(
                payload.open(),
                payload.contentWidth(),
                payload.contentHeight(),
                payload.mouseX(),
                payload.mouseY(),
                payload.slots()
        );
    }

    private static boolean validFurnace(ObserverNativeScreenPayloads.FurnaceState payload) {
        if (payload.protocolVersion() != ObserverNativeScreenPayloads.FURNACE_PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_FURNACE.equals(payload.familyId())
                || payload.sequence() < 0L
                || !Float.isFinite(payload.cookProgress())
                || !Float.isFinite(payload.fuelProgress())
                || payload.cookProgress() < 0.0F || payload.cookProgress() > 1.001F
                || payload.fuelProgress() < 0.0F || payload.fuelProgress() > 1.001F
                || !validSlots(payload.slots())) {
            return false;
        }
        return validScreenGeometry(
                payload.open(),
                payload.contentWidth(),
                payload.contentHeight(),
                payload.mouseX(),
                payload.mouseY(),
                payload.slots()
        );
    }

    private static boolean validBook(ObserverBookScreenPayloads.BookState payload) {
        if (payload.protocolVersion() != ObserverBookScreenPayloads.PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_BOOK.equals(payload.familyId())
                || payload.sequence() < 0L) {
            return false;
        }
        if (!payload.open()) {
            return payload.pageIndex() == 0 && payload.pageCount() == 0;
        }
        if (!validBookVariant(payload.variant())
                || payload.pageCount() < 0
                || payload.pageCount() > ObserverBookScreenPayloads.MAX_PAGE_COUNT) {
            return false;
        }
        return payload.pageCount() == 0
                ? payload.pageIndex() == 0
                : payload.pageIndex() >= 0 && payload.pageIndex() < payload.pageCount();
    }

    private static boolean validBookVariant(String variant) {
        return ObserverBookScreenPayloads.VARIANT_WRITTEN.equals(variant)
                || ObserverBookScreenPayloads.VARIANT_WRITABLE.equals(variant)
                || ObserverBookScreenPayloads.VARIANT_LECTERN.equals(variant)
                || ObserverBookScreenPayloads.VARIANT_SIGNING.equals(variant);
    }

    private static boolean validScreenGeometry(
            boolean open,
            int contentWidth,
            int contentHeight,
            int mouseX,
            int mouseY,
            java.util.List<ObserverNativeScreenPayloads.SlotState> slots
    ) {
        if (!open) {
            return slots.isEmpty();
        }
        return contentWidth >= 64 && contentWidth <= 512
                && contentHeight >= 64 && contentHeight <= 512
                && mouseX >= -2048 && mouseX <= 2048
                && mouseY >= -2048 && mouseY <= 2048;
    }

    private static boolean validSlots(java.util.List<ObserverNativeScreenPayloads.SlotState> slots) {
        if (slots.size() > ObserverNativeScreenPayloads.MAX_SLOTS) {
            return false;
        }
        for (ObserverNativeScreenPayloads.SlotState slot : slots) {
            if (slot.index() < 0
                    || slot.x() < -64 || slot.x() > 512
                    || slot.y() < -64 || slot.y() > 512
                    || slot.count() < 0 || slot.count() > 127
                    || slot.damage() < 0) {
                return false;
            }
        }
        return true;
    }

    private static long negotiatedScreenCapabilities(ServerPlayer observer) {
        long capabilities = 0L;
        if (ServerPlayNetworking.canSend(observer, ObserverNativeScreenPayloads.ContainerRelay.TYPE)) {
            capabilities |= ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS;
        }
        if (ServerPlayNetworking.canSend(observer, ObserverNativeScreenPayloads.FurnaceRelay.TYPE)) {
            capabilities |= ObserverNativeScreenPayloads.CAPABILITY_FURNACE;
        }
        if (ServerPlayNetworking.canSend(observer, ObserverBookScreenPayloads.BookRelay.TYPE)) {
            capabilities |= ObserverNativeScreenPayloads.CAPABILITY_BOOK;
        }
        return ObserverNativeScreenPayloads.sanitizeCapabilities(capabilities);
    }

    private static long screenCapabilitiesForTarget(UUID targetId) {
        long capabilities = 0L;
        for (Map.Entry<UUID, UUID> entry : TARGET_BY_OBSERVER.entrySet()) {
            if (targetId.equals(entry.getValue())) {
                capabilities |= SCREEN_CAPABILITIES_BY_OBSERVER.getOrDefault(entry.getKey(), 0L);
            }
        }
        return ObserverNativeScreenPayloads.sanitizeCapabilities(capabilities);
    }

    private static int nativeObserverCount(UUID targetId) {
        int count = 0;
        for (UUID value : TARGET_BY_OBSERVER.values()) {
            if (targetId.equals(value)) {
                count++;
            }
        }
        return count;
    }

    private static void updateTargetControl(MinecraftServer server, UUID targetId) {
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null || !ServerPlayNetworking.canSend(target, ObserverNativePayloads.NativeControl.TYPE)) {
            LAST_SEQUENCE_BY_TARGET.remove(targetId);
            LAST_SCREEN_SEQUENCE_BY_TARGET.remove(targetId);
            LAST_BOOK_SEQUENCE_BY_TARGET.remove(targetId);
            return;
        }
        boolean enabled = nativeObserverCount(targetId) > 0;
        long screenCapabilities = enabled ? screenCapabilitiesForTarget(targetId) : 0L;
        if (!enabled) {
            LAST_SEQUENCE_BY_TARGET.remove(targetId);
            LAST_SCREEN_SEQUENCE_BY_TARGET.remove(targetId);
            LAST_BOOK_SEQUENCE_BY_TARGET.remove(targetId);
        }
        ServerPlayNetworking.send(target, new ObserverNativePayloads.NativeControl(
                enabled,
                ObserverNativePayloads.PROTOCOL_VERSION,
                enabled ? ObserverNativePayloads.TARGET_STATE_FPS : 0,
                screenCapabilities
        ));
    }
}
