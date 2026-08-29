package dev.totem.vanillatweaks.client;

import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import dev.totem.core.api.v1.client.observer.ObserverRemoteCursor;
import dev.totem.core.api.v1.client.observer.ObserverScreenContext;
import dev.totem.core.api.v1.client.observer.ObserverScreenHandle;
import dev.totem.core.api.v1.client.observer.ObserverScreenProvider;
import dev.totem.core.api.v1.client.observer.ObserverScreenProviders;
import dev.totem.core.api.v1.client.observer.ObserverScreenSnapshot;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.Map;

/**
 * The only dispatch point for optional module-owned Observer screens. It never
 * reflectively copies a renderer and never falls back to a supported lookalike.
 */
public final class ObserverOwnedScreenCoordinator {
    private static Map<String, ObserverScreenProvider> providers;
    private static ObserverScreenHandle active;
    private static String activeFamily = "";
    private static String activeVariant = "";
    private static int activeProtocol;
    private static String dispatchedFamily = "";
    private static ObserverRemoteCursor remoteCursor;
    private static String remoteCursorFamily = "";
    private static String remoteCursorVariant = "";
    private static int remoteCursorProtocol;
    private static long renderGeneration;
    private static long activeSnapshotRenderBaseline;
    private static final java.util.Map<String, Long> INVALID_LOG_NANOS = new java.util.HashMap<>();

    private ObserverOwnedScreenCoordinator() { }

    public static boolean open(ObserverScreenSnapshot snapshot) {
        if (!dispatchedFamily.isEmpty() && !dispatchedFamily.equals(snapshot.familyId())) {
            close(dispatchedFamily);
        }
        if (providers == null) providers = ObserverScreenProviders.discover();
        ObserverScreenProvider provider;
        try {
            provider = ObserverScreenProviders.compatible(providers, snapshot).orElse(null);
        } catch (RuntimeException invalid) {
            invalidProviderState(snapshot, invalid);
            return false;
        }
        if (provider == null) {
            closeActiveScreen();
            dispatchedFamily = snapshot.familyId();
            ObserverNativeScreenClient.applyGenericScreenState(true,
                    "unsupported module provider: " + snapshot.familyId(), snapshot.title().getString());
            return false;
        }
        dispatchedFamily = snapshot.familyId();
        if (active != null && activeFamily.equals(snapshot.familyId())
                && activeVariant.equals(snapshot.variant()) && activeProtocol == snapshot.protocolVersion()
                && Minecraft.getInstance().gui.screen() == active.screen()) {
            try {
                active.applySnapshot(snapshot);
                activeSnapshotRenderBaseline = renderGeneration;
            } catch (RuntimeException invalid) {
                invalidProviderState(snapshot, invalid);
                return false;
            }
            return true;
        }
        var targetId = ObserverNativeClient.observerTargetId();
        if (targetId == null) return false;
        ObserverScreenContext context = new ObserverScreenContext(targetId, ObserverNativeClient.observerTargetName(), () -> {
            if (ObserverNativeClient.observerSessionActive()) ClientPlayNetworking.send(new ObserverPayloads.Stop());
        });
        try {
            active = provider.create(context, snapshot);
        } catch (RuntimeException invalid) {
            invalidProviderState(snapshot, invalid);
            return false;
        }
        activeFamily = snapshot.familyId();
        activeVariant = snapshot.variant();
        activeProtocol = snapshot.protocolVersion();
        activeSnapshotRenderBaseline = renderGeneration;
        Minecraft.getInstance().setScreenAndShow(active.screen());
        return true;
    }

    public static void applyCursor(String family, String variant, int protocol, ObserverRemoteCursor cursor) {
        remoteCursor = cursor;
        remoteCursorFamily = family;
        remoteCursorVariant = variant;
        remoteCursorProtocol = protocol;
        if (active != null && activeFamily.equals(family) && activeVariant.equals(variant)
                && activeProtocol == protocol) {
            try {
                active.applyCursor(cursor);
            } catch (RuntimeException invalid) {
                var snapshot = new ObserverScreenSnapshot(family, variant, protocol, cursor.sequence(),
                        net.minecraft.network.chat.Component.empty(), java.util.List.of(), new int[0],
                        java.util.Map.of(), new byte[0]);
                invalidProviderState(snapshot, invalid);
            }
        } else if (Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> container) {
            container.getMenu().setCarried(cursor.carriedStack());
        }
    }

    public static void close(String familyId) {
        if (!dispatchedFamily.equals(familyId)) return;
        closeActiveScreen();
        dispatchedFamily = "";
    }

    private static void closeActiveScreen() {
        if (active != null && Minecraft.getInstance().gui.screen() == active.screen()) {
            Minecraft.getInstance().setScreenAndShow(null);
        }
        active = null; activeFamily = ""; activeVariant = ""; activeProtocol = 0;
        remoteCursor = null;
        remoteCursorFamily = "";
        remoteCursorVariant = "";
        remoteCursorProtocol = 0;
        activeSnapshotRenderBaseline = renderGeneration;
    }

    public static int renderMouseX(int local) {
        if (!cursorMatchesCurrentScreen()) return local;
        var screen = Minecraft.getInstance().gui.screen();
        return (int)Math.round(remoteCursor.screenX(0, screen.width));
    }

    public static int renderMouseY(int local) {
        if (!cursorMatchesCurrentScreen()) return local;
        var screen = Minecraft.getInstance().gui.screen();
        return (int)Math.round(remoteCursor.screenY(0, screen.height));
    }

    public static boolean hasRemoteCursor() {
        return cursorMatchesCurrentScreen();
    }

    public static void recordRenderedFrame(Object screen) {
        if (isReadOnlyObserverScreen(screen)) renderGeneration++;
    }

    public static long renderGeneration() { return renderGeneration; }
    public static long activeSnapshotRenderBaseline() { return activeSnapshotRenderBaseline; }
    public static boolean hasRenderedActiveSnapshot() {
        return active != null && Minecraft.getInstance().gui.screen() == active.screen()
                && renderGeneration > activeSnapshotRenderBaseline;
    }

    private static boolean cursorMatchesCurrentScreen() {
        if (remoteCursor == null || remoteCursorFamily.isEmpty()) return false;
        var screen = Minecraft.getInstance().gui.screen();
        if (active != null && screen == active.screen()) return activeFamily.equals(remoteCursorFamily)
                && activeVariant.equals(remoteCursorVariant) && activeProtocol == remoteCursorProtocol;
        return ObserverVanillaScreenIdentity.classifyObserver(screen)
                .map(identity -> identity.family().equals(remoteCursorFamily)
                        && identity.variant().equals(remoteCursorVariant)
                        && identity.protocol() == remoteCursorProtocol).orElse(false);
    }

    public static boolean isActive(String familyId, String variant, int protocol) {
        return dispatchedFamily.equals(familyId) && activeFamily.equals(familyId)
                && activeVariant.equals(variant) && activeProtocol == protocol
                && ObserverNativeClient.observerSessionActive();
    }

    public static boolean isReadOnlyObserverScreen(Object screen) {
        return screen instanceof ObserverReadOnlyScreen marker && marker.totem$isObserverReadOnly();
    }

    static void replaceProvidersForTest(Map<String, ObserverScreenProvider> replacements) {
        closeActiveScreen();
        dispatchedFamily = "";
        providers = Map.copyOf(replacements);
    }

    static void reloadProvidersForTest() {
        closeActiveScreen();
        dispatchedFamily = "";
        providers = ObserverScreenProviders.discover();
    }

    private static void invalidProviderState(ObserverScreenSnapshot snapshot, RuntimeException invalid) {
        long now = System.nanoTime();
        long last = INVALID_LOG_NANOS.getOrDefault(snapshot.familyId(), Long.MIN_VALUE / 2);
        if (now - last >= 5_000_000_000L) {
            INVALID_LOG_NANOS.put(snapshot.familyId(), now);
            dev.totem.vanillatweaks.TotemVanillaTweaks.LOGGER.warn(
                    "Rejected invalid owner Observer snapshot {}:{} v{} seq {}",
                    snapshot.familyId(), snapshot.variant(), snapshot.protocolVersion(), snapshot.sequence(), invalid);
        }
        closeActiveScreen();
        dispatchedFamily = snapshot.familyId();
        ObserverNativeScreenClient.applyGenericScreenState(true,
                "invalid module provider payload: " + snapshot.familyId(), snapshot.title().getString());
    }
}
