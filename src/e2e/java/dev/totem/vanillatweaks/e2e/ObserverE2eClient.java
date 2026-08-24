package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverUiClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/** Test-only Target/Observer client driver for the real three-process Observer View E2E. */
public final class ObserverE2eClient implements ClientModInitializer {
    private static final Class<?> CLIENT = ObserverUiClient.class;
    private static final int CLIENT_TIMEOUT_TICKS = 20 * 120;
    private static final int MAX_CONNECTION_ATTEMPTS = 5;
    private static final int RECONNECT_DELAY_TICKS = 20 * 2;
    private static final int TARGET_WORLD_SETTLE_TICKS = 40;
    private static final int MIRROR_SETTLE_TICKS = 20;
    private static final ServerAddress E2E_SERVER = new ServerAddress("127.0.0.1", 25570);

    private static String role;
    private static int ticks;
    private static boolean connectionStarted;
    private static int connectionAttempts;
    private static int nextConnectionTick = 5;
    private static int targetWorldStableTicks;
    private static boolean targetReady;
    private static boolean targetCaptureSeen;
    private static boolean observerFrameSeen;
    private static int observerMirrorStableTicks;
    private static boolean observerScreenshotRequested;
    private static volatile boolean observerScreenshotSaved;
    private static boolean observerStopRequested;
    private static boolean finished;

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
        boolean disconnected = current != null
                && current.getClass().getSimpleName().contains("Disconnected");

        if (connectionStarted && disconnected) {
            connectionStarted = false;
            nextConnectionTick = ticks + RECONNECT_DELAY_TICKS;
            ObserverE2eCommon.marker(
                    role + "-reconnect-" + connectionAttempts + ".txt",
                    role + " connection attempt " + connectionAttempts
                            + " failed; retrying after " + RECONNECT_DELAY_TICKS + " ticks.\n"
            );
        }

        if (connectionStarted || ticks < nextConnectionTick) {
            return;
        }

        if (connectionAttempts >= MAX_CONNECTION_ATTEMPTS) {
            failAndStop(
                    minecraft,
                    "Failed to connect to dedicated server after " + MAX_CONNECTION_ATTEMPTS + " attempts"
            );
            return;
        }

        connectToDedicatedServer(minecraft);
    }

    private static void connectToDedicatedServer(Minecraft minecraft) {
        connectionStarted = true;
        connectionAttempts++;
        ServerData serverData = new ServerData(
                "Totem Observer E2E",
                E2E_SERVER.toString(),
                ServerData.Type.OTHER
        );
        Screen parent = minecraft.gui.screen();
        ConnectScreen.startConnecting(parent, minecraft, E2E_SERVER, serverData, false, null);
        ObserverE2eCommon.marker(
                role + "-connect-started.txt",
                role + " invoked Minecraft 26.2 ConnectScreen for 127.0.0.1:25570; attempt "
                        + connectionAttempts + ".\n"
        );
    }

    private static void tickTarget(Minecraft minecraft) {
        if (getBoolean("sessionActive")) {
            throw new AssertionError("Target JVM incorrectly entered observer session state");
        }
        if (getBoolean("textureRegistered")) {
            throw new AssertionError("Target JVM incorrectly installed observer mirror texture");
        }

        if (!targetReady) {
            if (minecraft.gui.screen() != null) {
                targetWorldStableTicks = 0;
                return;
            }
            targetWorldStableTicks++;
            if (targetWorldStableTicks < TARGET_WORLD_SETTLE_TICKS) {
                return;
            }
            targetReady = true;
            ObserverE2eCommon.marker(
                    "target-world-ready.txt",
                    "Target stayed in the dedicated-server gameplay view with no Screen open for "
                            + TARGET_WORLD_SETTLE_TICKS + " client ticks.\n"
            );
        }

        if (minecraft.gui.screen() != null) {
            throw new AssertionError("Target opened a Screen during gameplay framebuffer E2E: "
                    + minecraft.gui.screen().getClass().getName());
        }

        boolean captureEnabled = getBoolean("captureEnabled");
        if (captureEnabled && !targetCaptureSeen) {
            targetCaptureSeen = true;
            ObserverE2eCommon.marker(
                    "target-capture-enabled.txt",
                    "Target received CaptureControl(true) while remaining in normal gameplay view.\n"
            );
        }

        if (targetCaptureSeen && !captureEnabled) {
            if (getLong("nextFrameId") <= 0L) {
                throw new AssertionError("Target capture was enabled and then stopped without sending any frame");
            }
            ObserverE2eCommon.marker(
                    "target-complete.txt",
                    "Target sent at least one live gameplay framebuffer and received CaptureControl(false).\n"
            );
            finished = true;
            stopMinecraft(minecraft);
        }
    }

    private static void tickObserver(Minecraft minecraft) {
        if (getBoolean("captureEnabled")) {
            throw new AssertionError("Observer JVM incorrectly entered target capture state");
        }

        boolean sessionActive = getBoolean("sessionActive");
        Screen screen = minecraft.gui.screen();
        boolean mirrorOpen = screen != null && screen.getClass().getName().contains("ObserverMirrorScreen");
        boolean textureRegistered = getBoolean("textureRegistered");

        if (!observerFrameSeen
                && sessionActive
                && mirrorOpen
                && textureRegistered
                && getLong("lastFrameId") >= 0L
                && getInt("frameWidth") > 0
                && getInt("frameHeight") > 0) {
            Object targetName = getObject("targetName");
            if (!"Target".equals(targetName)) {
                throw new AssertionError("Observer session target name mismatch: " + targetName);
            }
            if (getBoolean("remoteScreenOpen")) {
                throw new AssertionError("Gameplay framebuffer E2E unexpectedly reports Target Screen open");
            }

            observerFrameSeen = true;
            observerMirrorStableTicks = 0;
            ObserverE2eCommon.marker(
                    "observer-frame-ok.txt",
                    "Observer received and installed a real relayed Target gameplay framebuffer while remoteScreenOpen=false.\n"
            );
        }

        if (observerFrameSeen && !observerScreenshotRequested) {
            if (sessionActive && mirrorOpen && textureRegistered && !getBoolean("remoteScreenOpen")) {
                observerMirrorStableTicks++;
                if (observerMirrorStableTicks >= MIRROR_SETTLE_TICKS) {
                    observerScreenshotRequested = true;
                    ObserverE2eCommon.marker(
                            "observer-mirror-settled.txt",
                            "Observer Mirror remained open with a live gameplay texture for "
                                    + MIRROR_SETTLE_TICKS + " client ticks before capture.\n"
                    );
                    saveMirrorScreenshot(minecraft);
                }
            } else {
                observerMirrorStableTicks = 0;
            }
        }

        if (observerFrameSeen && observerScreenshotSaved && !observerStopRequested) {
            Screen current = minecraft.gui.screen();
            if (current == null || !current.getClass().getName().contains("ObserverMirrorScreen")) {
                throw new AssertionError("Observer mirror closed before production Stop was requested");
            }
            current.onClose();
            observerStopRequested = true;
            ObserverE2eCommon.marker("observer-stop-requested.txt", "Observer Mirror Screen requested Stop.\n");
            return;
        }

        if (observerStopRequested && !sessionActive) {
            if (getBoolean("textureRegistered")) {
                return;
            }
            Screen current = minecraft.gui.screen();
            if (current != null && current.getClass().getName().contains("ObserverMirrorScreen")) {
                return;
            }
            ObserverE2eCommon.marker(
                    "observer-complete.txt",
                    "Observer received Session(false), released texture, and closed Mirror Screen.\n"
            );
            finished = true;
            stopMinecraft(minecraft);
        }
    }

    private static void saveMirrorScreenshot(Minecraft minecraft) {
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            if (image == null) {
                ObserverE2eCommon.fail("observer", "Screenshot callback returned null image");
                return;
            }
            try (NativeImage owned = image) {
                Path output = ObserverE2eCommon.resultsDir().resolve("observer-two-client-e2e.png");
                Files.createDirectories(output.getParent());
                owned.writeToFile(output);
                observerScreenshotSaved = true;
                ObserverE2eCommon.marker(
                        "observer-screenshot-saved.txt",
                        "Observer Mirror screenshot was flushed to disk after the settled gameplay mirror gate.\n"
                );
            } catch (IOException error) {
                ObserverE2eCommon.fail("observer", "Failed to save E2E screenshot: " + error);
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

    private static Field field(String name) {
        try {
            Field field = CLIENT.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Missing ObserverUiClient field: " + name, error);
        }
    }

    private static boolean getBoolean(String name) {
        try {
            return field(name).getBoolean(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static int getInt(String name) {
        try {
            return field(name).getInt(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static long getLong(String name) {
        try {
            return field(name).getLong(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static Object getObject(String name) {
        try {
            return field(name).get(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }
}
