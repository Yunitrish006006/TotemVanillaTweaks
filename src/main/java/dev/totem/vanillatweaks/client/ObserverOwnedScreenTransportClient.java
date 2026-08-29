package dev.totem.vanillatweaks.client;

import dev.totem.core.api.v1.client.observer.ObserverScreenProvider;
import dev.totem.core.api.v1.client.observer.ObserverScreenProviders;
import dev.totem.core.api.v1.client.observer.ObserverScreenSnapshot;
import dev.totem.vanillatweaks.network.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import dev.totem.core.api.v1.client.observer.ObserverRemoteCursor;

import java.util.Map;
import java.util.Comparator;
import java.util.UUID;
import java.util.HashMap;

/** Generic target capture and observer dispatch for owning-module providers. */
public final class ObserverOwnedScreenTransportClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static Map<String, ObserverScreenProvider> providers = Map.of();
    private static long sequence;
    private static long lastSnapshotNanos;
    private static String targetFamily = "";
    private static String targetVariant = "";
    private static int targetProtocol;
    private static String observerFamily = "";
    private static long cursorSequence;
    private static long lastCursorNanos;
    private static float lastCursorX = Float.NaN, lastCursorY = Float.NaN;
    private static ItemStack lastCarried = ItemStack.EMPTY;
    private static boolean providersAdvertised;
    private static final Map<String, Long> CAPTURE_ERROR_LOG_NANOS = new HashMap<>();

    private ObserverOwnedScreenTransportClient() { }

    public static void register() {
        providers = ObserverScreenProviders.discover();
        ClientPlayNetworking.registerGlobalReceiver(ObserverOwnedScreenPayloads.Relay.TYPE,
                (payload, context) -> context.client().execute(() -> accept(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ObserverRemoteCursorPayloads.Relay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptCursor(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverOwnedScreenTransportClient::tick);
    }

    private static void tick(Minecraft minecraft) {
        advertiseProviders();
        tickTarget(minecraft);
        tickCursor(minecraft);
        if (!ObserverNativeClient.observerSessionActive() && !observerFamily.isEmpty()) {
            ObserverOwnedScreenCoordinator.close(observerFamily); observerFamily = "";
        }
    }

    private static void advertiseProviders() {
        if (!ClientPlayNetworking.canSend(ObserverOwnedScreenPayloads.ProviderSet.TYPE)) {
            providersAdvertised = false;
            return;
        }
        if (providersAdvertised) return;
        var identities = providers.values().stream()
                .filter(provider -> capability(provider.familyId()) != 0L)
                .map(provider -> new ObserverOwnedScreenPayloads.ProviderIdentity(
                        provider.familyId(), provider.protocolVersion()))
                .sorted(Comparator.comparing(ObserverOwnedScreenPayloads.ProviderIdentity::familyId))
                .toList();
        ClientPlayNetworking.send(new ObserverOwnedScreenPayloads.ProviderSet(
                ObserverOwnedScreenPayloads.PROTOCOL_VERSION, identities));
        providersAdvertised = true;
    }

    private static void tickCursor(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.gui.screen() == null
                || ObserverOwnedScreenCoordinator.isReadOnlyObserverScreen(minecraft.gui.screen())
                || !ObserverNativeClient.targetSupportsScreen(ObserverRemoteCursorPayloads.CAPABILITY)
                || !ClientPlayNetworking.canSend(ObserverRemoteCursorPayloads.State.TYPE)) return;
        ObserverVanillaScreenIdentity.Identity identity = targetFamily.isEmpty()
                ? ObserverVanillaScreenIdentity.classify(minecraft.gui.screen()).orElse(null)
                : new ObserverVanillaScreenIdentity.Identity(targetFamily, targetVariant, targetProtocol);
        if (identity == null || !ObserverNativeClient.targetSupportsScreen(capability(identity.family()))) return;
        long now = System.nanoTime();
        if (now - lastCursorNanos < 1_000_000_000L / ObserverRemoteCursorPayloads.MAX_UPDATES_PER_SECOND) return;
        int width = Math.max(1, minecraft.gui.screen().width), height = Math.max(1, minecraft.gui.screen().height);
        float x = (float)Math.clamp(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()), 0.0, width - 1.0);
        float y = (float)Math.clamp(minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()), 0.0, height - 1.0);
        ItemStack carried = minecraft.gui.screen() instanceof AbstractContainerScreen<?> container
                ? container.getMenu().getCarried().copy() : ItemStack.EMPTY;
        if (x == lastCursorX && y == lastCursorY && ItemStack.matches(carried, lastCarried)) return;
        lastCursorNanos = now; lastCursorX = x; lastCursorY = y; lastCarried = carried.copy();
        ClientPlayNetworking.send(new ObserverRemoteCursorPayloads.State(
                ObserverRemoteCursorPayloads.PROTOCOL_VERSION, ++cursorSequence, identity.family(), identity.variant(),
                identity.protocol(), x, y, width, height, carried));
    }

    private static void tickTarget(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null
                || ObserverOwnedScreenCoordinator.isReadOnlyObserverScreen(minecraft.gui.screen())) {
            closeTarget(); return;
        }
        long now = System.nanoTime();
        if (now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        Screen screen = minecraft.gui.screen();
        for (ObserverScreenProvider provider : providers.values()) {
            long capability = capability(provider.familyId());
            if (capability == 0 || !ObserverNativeClient.targetSupportsScreen(capability)) continue;
            java.util.Optional<ObserverScreenSnapshot> captured;
            try {
                captured = provider.capture(screen, sequence + 1);
            } catch (RuntimeException invalid) {
                long lastLog = CAPTURE_ERROR_LOG_NANOS.getOrDefault(provider.familyId(), Long.MIN_VALUE / 2);
                if (now - lastLog >= 5_000_000_000L) {
                    CAPTURE_ERROR_LOG_NANOS.put(provider.familyId(), now);
                    dev.totem.vanillatweaks.TotemVanillaTweaks.LOGGER.warn(
                            "Owner Observer capture failed for {}; closing its relay state",
                            provider.familyId(), invalid);
                }
                if (targetFamily.equals(provider.familyId())) closeTarget();
                continue;
            }
            if (captured.isEmpty()) continue;
            ObserverScreenSnapshot snapshot = captured.orElseThrow();
            try {
                if (!provider.supports(snapshot)) throw new IllegalArgumentException("Provider captured incompatible snapshot");
            } catch (RuntimeException invalid) {
                long lastLog = CAPTURE_ERROR_LOG_NANOS.getOrDefault(provider.familyId(), Long.MIN_VALUE / 2);
                if (now - lastLog >= 5_000_000_000L) {
                    CAPTURE_ERROR_LOG_NANOS.put(provider.familyId(), now);
                    dev.totem.vanillatweaks.TotemVanillaTweaks.LOGGER.warn(
                            "Owner Observer capture validation failed for {}; closing its relay state",
                            provider.familyId(), invalid);
                }
                if (targetFamily.equals(provider.familyId())) closeTarget();
                continue;
            }
            if (!ClientPlayNetworking.canSend(ObserverOwnedScreenPayloads.State.TYPE)) return;
            try {
                ClientPlayNetworking.send(new ObserverOwnedScreenPayloads.State(true, snapshot));
            } catch (RuntimeException encodeFailure) {
                logCaptureFailure(provider.familyId(), now,
                        "Owner Observer encode/send failed for {}; skipping its relay state", encodeFailure);
                if (targetFamily.equals(provider.familyId())) closeTarget();
                continue;
            }
            sequence = snapshot.sequence(); lastSnapshotNanos = now;
            targetFamily = snapshot.familyId(); targetVariant = snapshot.variant(); targetProtocol = snapshot.protocolVersion();
            return;
        }
        closeTarget();
    }

    private static void closeTarget() {
        if (targetFamily.isEmpty()) return;
        if (ClientPlayNetworking.canSend(ObserverOwnedScreenPayloads.State.TYPE)) {
            ClientPlayNetworking.send(new ObserverOwnedScreenPayloads.State(false,
                    ObserverOwnedScreenPayloads.closed(targetFamily, targetVariant, targetProtocol, ++sequence)));
        }
        targetFamily = ""; targetVariant = ""; targetProtocol = 0; lastSnapshotNanos = 0;
        lastCursorX = Float.NaN; lastCursorY = Float.NaN; lastCarried = ItemStack.EMPTY;
    }

    private static void logCaptureFailure(String family, long now, String message, RuntimeException failure) {
        long lastLog = CAPTURE_ERROR_LOG_NANOS.getOrDefault(family, Long.MIN_VALUE / 2);
        if (now - lastLog < 5_000_000_000L) return;
        CAPTURE_ERROR_LOG_NANOS.put(family, now);
        dev.totem.vanillatweaks.TotemVanillaTweaks.LOGGER.warn(message, family, failure);
    }

    private static void acceptCursor(ObserverRemoteCursorPayloads.Relay payload) {
        UUID target = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive() || target == null || !target.equals(payload.targetId())
                || payload.protocolVersion() != ObserverRemoteCursorPayloads.PROTOCOL_VERSION
                || !ObserverNativeClient.observerSupportsScreen(ObserverRemoteCursorPayloads.CAPABILITY)
                || !cursorIdentityIsActive(payload.familyId(), payload.variant(), payload.screenProtocol())
                || !ObserverRemoteCursorPayloads.valid(payload.sequence(), payload.x(), payload.y(),
                payload.contentWidth(), payload.contentHeight())
                || !ObserverRemoteSequenceTracker.accept("cursor:" + payload.familyId(), payload.targetId(), payload.sequence())) return;
        ObserverOwnedScreenCoordinator.applyCursor(payload.familyId(), payload.variant(), payload.screenProtocol(), new ObserverRemoteCursor(payload.sequence(), payload.x(), payload.y(),
                payload.contentWidth(), payload.contentHeight(), payload.carried()));
    }

    private static boolean cursorIdentityIsActive(String family, String variant, int protocol) {
        if (ObserverOwnedScreenCoordinator.isActive(family, variant, protocol)) return true;
        Screen screen = Minecraft.getInstance().gui.screen();
        return ObserverVanillaScreenIdentity.classifyObserver(screen)
                .map(identity -> identity.family().equals(family) && identity.variant().equals(variant)
                        && identity.protocol() == protocol).orElse(false);
    }

    private static void accept(ObserverOwnedScreenPayloads.Relay payload) {
        UUID target = ObserverNativeClient.observerTargetId();
        ObserverScreenSnapshot snapshot = payload.snapshot();
        if (!ObserverNativeClient.observerSessionActive() || target == null || !target.equals(payload.targetId())
                || !ObserverOwnedScreenProtocols.accepts(snapshot.familyId(), snapshot.protocolVersion())
                || !ObserverNativeClient.observerSupportsScreen(capability(snapshot.familyId()))
                || !ObserverRemoteSequenceTracker.accept(snapshot.familyId(), payload.targetId(), snapshot.sequence())) return;
        if (!payload.open()) {
            ObserverOwnedScreenCoordinator.close(snapshot.familyId());
            if (observerFamily.equals(snapshot.familyId())) observerFamily = "";
            return;
        }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        if (ObserverOwnedScreenCoordinator.open(snapshot)) observerFamily = snapshot.familyId();
    }

    private static long capability(String family) {
        long builtIn = ObserverNativeScreenPayloads.capabilityForFamily(family);
        if (builtIn != 0) return builtIn;
        return switch (family) {
            case "villagers_woodcutter" -> ObserverVillagersWoodcutterPayloads.CAPABILITY;
            case "nexus_death_node_admin" -> ObserverNexusDeathNodeAdminPayloads.CAPABILITY;
            case "locksmith_management" -> ObserverLocksmithManagementPayloads.CAPABILITY;
            default -> 0L;
        };
    }
}
