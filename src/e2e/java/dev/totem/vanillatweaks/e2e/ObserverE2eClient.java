package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeHud;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.client.ObserverUiClient;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/** Test-only Target/Observer driver for the real three-process protocol-native Observer E2E. */
public final class ObserverE2eClient implements ClientModInitializer {
    private static final Class<?> UI_CLIENT = ObserverUiClient.class;
    private static final Class<?> NATIVE_CLIENT = ObserverNativeClient.class;
    private static final Class<?> NATIVE_HUD = ObserverNativeHud.class;
    private static final Class<?> NATIVE_SCREEN_CLIENT = ObserverNativeScreenClient.class;

    private static final int CLIENT_TIMEOUT_TICKS = 20 * 180;
    private static final int MAX_CONNECTION_ATTEMPTS = 5;
    private static final int RECONNECT_DELAY_TICKS = 20 * 2;
    private static final int INITIAL_CONNECTION_DELAY_TICKS = 20 * 10;
    private static final int TARGET_WORLD_SETTLE_TICKS = 40;
    private static final int NATIVE_WORLD_SETTLE_TICKS = 20;
    private static final ServerAddress E2E_SERVER = new ServerAddress("127.0.0.1", 25570);

    private static String role;
    private static int ticks;
    private static boolean connectionStarted;
    private static int connectionAttempts;
    private static int nextConnectionTick = INITIAL_CONNECTION_DELAY_TICKS;
    private static boolean finished;

    private static int targetWorldStableTicks;
    private static boolean targetReady;
    private static boolean targetNativeSeen;
    private static boolean targetContainerOpened;
    private static boolean targetContainerClosed;
    private static boolean targetGenericOpened;
    private static boolean targetGenericClosed;
    private static int targetGenericTicks;

    private static boolean observerNativeSeen;
    private static boolean observerHudSeen;
    private static int observerNativeWorldStableTicks;
    private static boolean observerWorldScreenshotRequested;
    private static volatile boolean observerWorldScreenshotSaved;
    private static boolean observerContainerRequested;
    private static boolean observerContainerSeen;
    private static boolean observerContainerScreenshotRequested;
    private static volatile boolean observerContainerScreenshotSaved;
    private static boolean observerGenericRequested;
    private static boolean observerGenericSeen;
    private static boolean observerGenericScreenshotRequested;
    private static volatile boolean observerGenericScreenshotSaved;
    private static boolean observerStopRequested;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("totem.observer.e2e.enabled")) {
            return;
        }
        role = System.getProperty("totem.observer.e2e.role", "").trim();
        if (!role.equals("target") && !role.equals("observer")) {
            ObserverE2eCommon.fail("client", "Unknown E2E role: " + role);
            return;
        }
        assertFramebufferSurfaceRemoved();
        ClientTickEvents.END_CLIENT_TICK.register(ObserverE2eClient::tick);
    }

    private static void tick(Minecraft minecraft) {
        if (finished) {
            return;
        }
        ticks++;
        try {
            if (ticks > CLIENT_TIMEOUT_TICKS) {
                failAndStop(minecraft, "Timed out after " + CLIENT_TIMEOUT_TICKS + " client ticks");
                return;
            }
            if (minecraft.player == null || minecraft.level == null) {
                handleConnectionState(minecraft);
                return;
            }
            if (role.equals("target")) {
                tickTarget(minecraft);
            } else {
                tickObserver(minecraft);
            }
        } catch (Throwable error) {
            failAndStop(minecraft, error.toString());
        }
    }

    private static void handleConnectionState(Minecraft minecraft) {
        Screen current = minecraft.gui.screen();
        boolean disconnected = current != null && current.getClass().getSimpleName().contains("Disconnected");
        if (connectionStarted && disconnected) {
            connectionStarted = false;
            nextConnectionTick = ticks + RECONNECT_DELAY_TICKS;
        }
        if (connectionStarted || ticks < nextConnectionTick) {
            return;
        }
        if (connectionAttempts >= MAX_CONNECTION_ATTEMPTS) {
            failAndStop(minecraft, "Failed to connect after " + MAX_CONNECTION_ATTEMPTS + " attempts");
            return;
        }
        connectionStarted = true;
        connectionAttempts++;
        ServerData serverData = new ServerData("Totem Observer E2E", E2E_SERVER.toString(), ServerData.Type.OTHER);
        ConnectScreen.startConnecting(minecraft.gui.screen(), minecraft, E2E_SERVER, serverData, false, null);
        ObserverE2eCommon.marker(role + "-connect-started.txt", "attempt=" + connectionAttempts + "\n");
    }

    private static void tickTarget(Minecraft minecraft) {
        if (nativeGetBoolean("observerSessionActive")) {
            throw new AssertionError("Target entered observer receiver state");
        }

        if (!targetReady) {
            if (minecraft.gui.screen() != null) {
                targetWorldStableTicks = 0;
                return;
            }
            if (++targetWorldStableTicks < TARGET_WORLD_SETTLE_TICKS) {
                return;
            }
            targetReady = true;
            ObserverE2eCommon.marker(
                    "target-world-ready.txt",
                    "Target stabilized in gameplay with no Screen open.\n"
            );
        }

        if (nativeGetBoolean("targetStateEnabled") && !targetNativeSeen) {
            if (nativeGetInt("targetProtocolVersion") != ObserverNativePayloads.PROTOCOL_VERSION) {
                throw new AssertionError("Unexpected Target protocol version " + nativeGetInt("targetProtocolVersion"));
            }
            targetNativeSeen = true;
            ObserverE2eCommon.marker(
                    "target-native-state-enabled.txt",
                    "Target negotiated framebuffer-free Observer protocol v" + ObserverNativePayloads.PROTOCOL_VERSION + ".\n"
            );
        }

        if (targetNativeSeen && nativeGetLong("nextTargetStateSequence") >= 3L
                && !markerExists("target-native-no-frame.txt")) {
            assertFramebufferSurfaceRemoved();
            ObserverE2eCommon.marker(
                    "target-native-no-frame.txt",
                    "Target sent structured state; framebuffer transport classes/fields are absent from production client.\n"
            );
        }

        if (!targetContainerOpened && markerExists("observer-ready-for-container.txt")) {
            if (minecraft.gui.screen() != null) {
                throw new AssertionError("Unexpected Target Screen before Inventory proof");
            }
            minecraft.setScreenAndShow(new InventoryScreen(minecraft.player));
            targetContainerOpened = true;
            ObserverE2eCommon.marker("target-native-container-opened.txt", "Target opened real InventoryScreen.\n");
            return;
        }

        if (targetContainerOpened && !targetContainerClosed) {
            if (!(minecraft.gui.screen() instanceof InventoryScreen)) {
                throw new AssertionError("InventoryScreen closed before Observer proof");
            }
            if (screenGetBoolean("targetContainerOpen") && screenGetLong("nextTargetSequence") > 0L) {
                ObserverE2eCommon.marker(
                        "target-native-container-state-sent.txt",
                        "Target sent structured Inventory slot state.\n"
                );
            }
            if (markerExists("observer-native-container-saved.txt")) {
                minecraft.setScreenAndShow(null);
                targetContainerClosed = true;
                ObserverE2eCommon.marker("target-native-container-closed.txt", "Target closed InventoryScreen.\n");
            }
            return;
        }

        if (!targetGenericOpened && targetContainerClosed && markerExists("observer-ready-for-generic-screen.txt")) {
            if (minecraft.gui.screen() != null) {
                throw new AssertionError("Unexpected Target Screen before generic proof");
            }
            minecraft.setScreenAndShow(new E2eUnsupportedScreen());
            targetGenericOpened = true;
            targetGenericTicks = 0;
            ObserverE2eCommon.marker(
                    "target-native-generic-opened.txt",
                    "Target opened unsupported non-container Screen.\n"
            );
            return;
        }

        if (targetGenericOpened && !targetGenericClosed) {
            if (!(minecraft.gui.screen() instanceof E2eUnsupportedScreen)) {
                throw new AssertionError("Unsupported Screen closed before Observer proof");
            }
            targetGenericTicks++;
            if (targetGenericTicks >= 10 && !markerExists("target-native-generic-no-frame.txt")) {
                assertFramebufferSurfaceRemoved();
                ObserverE2eCommon.marker(
                        "target-native-generic-no-frame.txt",
                        "Unsupported Screen stayed open while production client had no framebuffer transport surface.\n"
                );
            }
            if (markerExists("observer-native-generic-screen-saved.txt")) {
                minecraft.setScreenAndShow(null);
                targetGenericClosed = true;
                ObserverE2eCommon.marker("target-native-generic-closed.txt", "Target closed unsupported Screen.\n");
            }
            return;
        }

        if (targetGenericClosed && !nativeGetBoolean("targetStateEnabled")) {
            if (!targetNativeSeen || nativeGetLong("nextTargetStateSequence") <= 0L) {
                throw new AssertionError("Target stopped without native state relay");
            }
            ObserverE2eCommon.marker(
                    "target-complete.txt",
                    "Target completed world/HUD/container/unsupported-screen protocol-native observation.\n"
            );
            finished = true;
            stopMinecraft(minecraft);
        }
    }

    private static void tickObserver(Minecraft minecraft) {
        if (nativeGetBoolean("targetStateEnabled")) {
            throw new AssertionError("Observer entered Target sender state");
        }
        dismissAccessibilityOnboarding(minecraft);

        boolean observerSessionActive = nativeGetBoolean("observerSessionActive");
        Screen screen = minecraft.gui.screen();

        if (!observerNativeSeen && observerSessionActive && nativeGetLong("lastNativeStateSequence") > 0L) {
            if (!"Target".equals(nativeGetObject("observerTargetName"))) {
                throw new AssertionError("Observer Target name mismatch");
            }
            if (nativeGetInt("observerProtocolVersion") != ObserverNativePayloads.PROTOCOL_VERSION) {
                throw new AssertionError("Unexpected Observer protocol version");
            }
            float health = nativeGetFloat("remoteHealth");
            float maxHealth = nativeGetFloat("remoteMaxHealth");
            int food = nativeGetInt("remoteFood");
            if (!(health >= 0.0F && maxHealth > 0.0F && health <= maxHealth && food >= 0 && food <= 20)) {
                throw new AssertionError("Invalid native HUD state");
            }
            requireTargetCamera(minecraft);
            observerNativeSeen = true;
            ObserverE2eCommon.marker(
                    "observer-native-state-ok.txt",
                    "Observer received v" + ObserverNativePayloads.PROTOCOL_VERSION + " structured state.\n"
            );
            ObserverE2eCommon.marker(
                    "observer-native-camera-ok.txt",
                    "Observer camera entity is Target using vanilla world rendering.\n"
            );
        }

        if (observerNativeSeen && !observerHudSeen && hudGetLong("extractedFrames") > 0L) {
            observerHudSeen = true;
            ObserverE2eCommon.marker("observer-native-hud-ok.txt", "Observer locally rendered structured HUD.\n");
        }

        if (observerNativeSeen && observerHudSeen && !observerWorldScreenshotRequested) {
            if (observerSessionActive && minecraft.gui.screen() == null) {
                requireTargetCamera(minecraft);
                if (++observerNativeWorldStableTicks >= NATIVE_WORLD_SETTLE_TICKS) {
                    observerWorldScreenshotRequested = true;
                    ObserverE2eCommon.marker(
                            "observer-native-world-settled.txt",
                            "Observer native Target camera stayed stable with local HUD.\n"
                    );
                    saveScreenshot(minecraft, "observer-native-world.png", () -> {
                        observerWorldScreenshotSaved = true;
                        ObserverE2eCommon.marker("observer-native-world-saved.txt", "Native world screenshot saved locally.\n");
                    });
                }
            } else {
                observerNativeWorldStableTicks = 0;
            }
        }

        if (observerWorldScreenshotSaved && !observerContainerRequested) {
            observerContainerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-container.txt", "Target may open InventoryScreen.\n");
        }

        if (observerContainerRequested && !observerContainerSeen
                && isNativeContainerMirror(screen)
                && screenGetBoolean("remoteContainerOpen")
                && screenGetLong("lastRemoteSequence") > 0L
                && screenGetLong("extractedFrames") > 0L) {
            observerContainerSeen = true;
            ObserverE2eCommon.marker(
                    "observer-native-container-ok.txt",
                    "Observer rendered structured InventoryScreen locally.\n"
            );
        }

        if (observerContainerSeen && !observerContainerScreenshotRequested) {
            observerContainerScreenshotRequested = true;
            saveScreenshot(minecraft, "observer-native-container.png", () -> {
                observerContainerScreenshotSaved = true;
                ObserverE2eCommon.marker(
                        "observer-native-container-saved.txt",
                        "Locally reconstructed container screenshot saved.\n"
                );
            });
        }

        if (observerContainerScreenshotSaved && !observerGenericRequested
                && !screenGetBoolean("remoteContainerOpen")
                && !isNativeContainerMirror(minecraft.gui.screen())) {
            observerGenericRequested = true;
            ObserverE2eCommon.marker(
                    "observer-ready-for-generic-screen.txt",
                    "Target may open unsupported Screen.\n"
            );
        }

        if (observerGenericRequested && !observerGenericSeen
                && isNativeGenericMirror(minecraft.gui.screen())
                && screenGetBoolean("remoteGenericOpen")
                && screenGetLong("genericExtractedFrames") > 0L) {
            observerGenericSeen = true;
            ObserverE2eCommon.marker(
                    "observer-native-generic-screen-ok.txt",
                    "Observer rendered metadata-only placeholder for unsupported Screen.\n"
            );
        }

        if (observerGenericSeen && !observerGenericScreenshotRequested) {
            observerGenericScreenshotRequested = true;
            saveScreenshot(minecraft, "observer-native-generic-screen.png", () -> {
                observerGenericScreenshotSaved = true;
                ObserverE2eCommon.marker(
                        "observer-native-generic-screen-saved.txt",
                        "Metadata-only generic Screen screenshot saved locally.\n"
                );
            });
        }

        if (observerGenericScreenshotSaved && !observerStopRequested
                && !screenGetBoolean("remoteGenericOpen")
                && minecraft.gui.screen() == null) {
            requireTargetCamera(minecraft);
            ClientPlayNetworking.send(new ObserverPayloads.Stop());
            observerStopRequested = true;
            ObserverE2eCommon.marker("observer-stop-requested.txt", "Observer sent Stop.\n");
            return;
        }

        if (observerStopRequested && !observerSessionActive) {
            if (isNativeMirror(minecraft.gui.screen())) {
                return;
            }
            ObserverE2eCommon.marker(
                    "observer-complete.txt",
                    "Observer received Session(false) and cleaned up protocol-native screens.\n"
            );
            finished = true;
            stopMinecraft(minecraft);
        }
    }

    private static void assertFramebufferSurfaceRemoved() {
        assertNoField(UI_CLIENT, "captureEnabled");
        assertNoField(UI_CLIENT, "nextFrameId");
        assertNoField(UI_CLIENT, "lastFrameId");
        assertNoField(UI_CLIENT, "textureRegistered");
        assertNoNestedPayload("CaptureControl");
        assertNoNestedPayload("Session");
        assertNoNestedPayload("FrameChunk");
        assertNoNestedPayload("FrameRelay");
    }

    private static void assertNoField(Class<?> owner, String name) {
        try {
            owner.getDeclaredField(name);
            throw new AssertionError("Removed framebuffer field still exists: " + owner.getSimpleName() + "." + name);
        } catch (NoSuchFieldException expected) {
            // Required absence.
        }
    }

    private static void assertNoNestedPayload(String simpleName) {
        for (Class<?> nested : ObserverPayloads.class.getDeclaredClasses()) {
            if (nested.getSimpleName().equals(simpleName)) {
                throw new AssertionError("Removed framebuffer payload still exists: " + simpleName);
            }
        }
    }

    private static void dismissAccessibilityOnboarding(Minecraft minecraft) {
        Screen screen = minecraft.gui.screen();
        if (screen != null && screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) {
            screen.onClose();
        }
    }

    private static void requireTargetCamera(Minecraft minecraft) {
        var camera = minecraft.getCameraEntity();
        if (camera == null || !"Target".equals(camera.getName().getString())) {
            throw new AssertionError("Observer camera is not Target: "
                    + (camera == null ? "null" : camera.getName().getString()));
        }
    }

    private static boolean isNativeContainerMirror(Screen screen) {
        return screen != null && screen.getClass().getName().contains("NativeContainerMirrorScreen");
    }

    private static boolean isNativeGenericMirror(Screen screen) {
        return screen != null && screen.getClass().getName().contains("NativeGenericMirrorScreen");
    }

    private static boolean isNativeMirror(Screen screen) {
        return isNativeContainerMirror(screen) || isNativeGenericMirror(screen);
    }

    private static boolean markerExists(String name) {
        return Files.isRegularFile(ObserverE2eCommon.resultsDir().resolve(name));
    }

    private static void saveScreenshot(Minecraft minecraft, String name, Runnable onSaved) {
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            if (image == null) {
                ObserverE2eCommon.fail(role, "Screenshot callback returned null for " + name);
                return;
            }
            try (NativeImage owned = image) {
                Path output = ObserverE2eCommon.resultsDir().resolve(name);
                Files.createDirectories(output.getParent());
                owned.writeToFile(output);
                if (name.equals("observer-native-world.png")) {
                    owned.writeToFile(ObserverE2eCommon.resultsDir().resolve("observer-native-hud.png"));
                }
                onSaved.run();
            } catch (IOException error) {
                ObserverE2eCommon.fail(role, "Failed to save " + name + ": " + error);
            }
        });
    }

    private static void failAndStop(Minecraft minecraft, String message) {
        if (finished) {
            return;
        }
        finished = true;
        ObserverE2eCommon.fail(role == null || role.isBlank() ? "client" : role, message);
        stopMinecraft(minecraft);
    }

    private static void stopMinecraft(Minecraft minecraft) {
        try {
            Method method = Minecraft.class.getMethod("stop");
            method.invoke(minecraft);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Failed to stop E2E Minecraft client", error);
        }
    }

    private static Field nativeField(String name) {
        return field(NATIVE_CLIENT, "ObserverNativeClient", name);
    }

    private static Field hudField(String name) {
        return field(NATIVE_HUD, "ObserverNativeHud", name);
    }

    private static Field screenField(String name) {
        return field(NATIVE_SCREEN_CLIENT, "ObserverNativeScreenClient", name);
    }

    private static Field field(Class<?> owner, String ownerName, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Missing " + ownerName + " field: " + name, error);
        }
    }

    private static boolean nativeGetBoolean(String name) {
        try {
            return nativeField(name).getBoolean(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static boolean screenGetBoolean(String name) {
        try {
            return screenField(name).getBoolean(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static int nativeGetInt(String name) {
        try {
            return nativeField(name).getInt(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static long nativeGetLong(String name) {
        try {
            return nativeField(name).getLong(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static long hudGetLong(String name) {
        try {
            return hudField(name).getLong(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static long screenGetLong(String name) {
        try {
            return screenField(name).getLong(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static float nativeGetFloat(String name) {
        try {
            return nativeField(name).getFloat(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static Object nativeGetObject(String name) {
        try {
            return nativeField(name).get(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static final class E2eUnsupportedScreen extends Screen {
        private E2eUnsupportedScreen() {
            super(Component.literal("Observer E2E Unsupported Screen"));
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF202020);
            graphics.text(font, getTitle(), 20, 20, 0xFFFFFFFF, true);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
