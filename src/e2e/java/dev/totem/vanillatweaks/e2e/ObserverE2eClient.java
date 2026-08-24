package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverUiClient;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
    private static final Class<?> NATIVE_CLIENT = ObserverNativeClient.class;
    private static final int CLIENT_TIMEOUT_TICKS = 20 * 120;
    private static final int MAX_CONNECTION_ATTEMPTS = 5;
    private static final int RECONNECT_DELAY_TICKS = 20 * 2;
    private static final int TARGET_WORLD_SETTLE_TICKS = 40;
    private static final int NATIVE_WORLD_SETTLE_TICKS = 20;
    private static final ServerAddress E2E_SERVER = new ServerAddress("127.0.0.1", 25570);

    private static String role;
    private static int ticks;
    private static boolean connectionStarted;
    private static int connectionAttempts;
    private static int nextConnectionTick = 5;
    private static int targetWorldStableTicks;
    private static boolean targetReady;
    private static boolean targetCaptureSeen;
    private static boolean targetNativeSeen;
    private static boolean targetNoFrameProven;
    private static boolean observerNativeSeen;
    private static int observerNativeWorldStableTicks;
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
        if (nativeGetBoolean("observerSessionActive")) {
            throw new AssertionError("Target JVM incorrectly entered native observer session state");
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
            throw new AssertionError("Target opened a Screen during native gameplay E2E: "
                    + minecraft.gui.screen().getClass().getName());
        }

        boolean captureEnabled = getBoolean("captureEnabled");
        if (captureEnabled && !targetCaptureSeen) {
            targetCaptureSeen = true;
            ObserverE2eCommon.marker(
                    "target-capture-enabled.txt",
                    "Target received compatibility CaptureControl(true); native mode must suppress gameplay frames.\n"
            );
        }

        boolean nativeEnabled = nativeGetBoolean("targetStateEnabled");
        if (nativeEnabled && !targetNativeSeen) {
            targetNativeSeen = true;
            if (nativeGetInt("targetProtocolVersion") != 1) {
                throw new AssertionError("Target negotiated unexpected native protocol version: "
                        + nativeGetInt("targetProtocolVersion"));
            }
            if (nativeGetBoolean("captureGameplayFrames")) {
                throw new AssertionError("Native-only E2E incorrectly requested gameplay framebuffer fallback");
            }
            ObserverE2eCommon.marker(
                    "target-native-state-enabled.txt",
                    "Target negotiated Observer protocol v1 with gameplay framebuffer suppression.\n"
            );
        }

        if (targetNativeSeen && nativeGetLong("nextTargetStateSequence") >= 3L && !targetNoFrameProven) {
            if (getLong("nextFrameId") != 0L) {
                throw new AssertionError("Target emitted gameplay framebuffer despite protocol-native mode; nextFrameId="
                        + getLong("nextFrameId"));
            }
            targetNoFrameProven = true;
            ObserverE2eCommon.marker(
                    "target-native-no-frame.txt",
                    "Target sent structured state while nextFrameId remained zero with no Screen open.\n"
            );
        }

        if (targetCaptureSeen && !captureEnabled) {
            if (!targetNativeSeen || nativeGetLong("nextTargetStateSequence") <= 0L) {
                throw new AssertionError("Target session completed without sending protocol-native structured state");
            }
            if (!targetNoFrameProven || getLong("nextFrameId") != 0L) {
                throw new AssertionError("Target completed native gameplay session after emitting a framebuffer");
            }
            ObserverE2eCommon.marker(
                    "target-complete.txt",
                    "Target completed protocol-native gameplay observation with zero PNG/framebuffer frames.\n"
            );
            finished = true;
            stopMinecraft(minecraft);
        }
    }

    private static void tickObserver(Minecraft minecraft) {
        if (getBoolean("captureEnabled")) {
            throw new AssertionError("Observer JVM incorrectly entered target capture state");
        }
        if (nativeGetBoolean("targetStateEnabled")) {
            throw new AssertionError("Observer JVM incorrectly entered native target-state sender mode");
        }

        boolean sessionActive = getBoolean("sessionActive");
        Screen screen = minecraft.gui.screen();
        boolean mirrorOpen = screen != null && screen.getClass().getName().contains("ObserverMirrorScreen");
        boolean textureRegistered = getBoolean("textureRegistered");

        if (nativeGetBoolean("observerSessionActive")) {
            if (!sessionActive || !getBoolean("nativeSession")) {
                throw new AssertionError("Native session was not bridged into ObserverUiClient lifecycle");
            }
            if (mirrorOpen) {
                throw new AssertionError("Protocol-native gameplay unexpectedly opened ObserverMirrorScreen");
            }
            if (textureRegistered || getLong("lastFrameId") >= 0L) {
                throw new AssertionError("Protocol-native gameplay unexpectedly installed/received framebuffer state");
            }
        }

        if (!observerNativeSeen
                && nativeGetBoolean("observerSessionActive")
                && nativeGetLong("lastNativeStateSequence") > 0L) {
            Object targetName = nativeGetObject("observerTargetName");
            if (!"Target".equals(targetName)) {
                throw new AssertionError("Native Observer target name mismatch: " + targetName);
            }
            if (nativeGetInt("observerProtocolVersion") != 1) {
                throw new AssertionError("Observer negotiated unexpected native protocol version: "
                        + nativeGetInt("observerProtocolVersion"));
            }
            float health = nativeGetFloat("remoteHealth");
            float maxHealth = nativeGetFloat("remoteMaxHealth");
            int food = nativeGetInt("remoteFood");
            if (!(health >= 0.0F && maxHealth > 0.0F && health <= maxHealth && food >= 0 && food <= 20)) {
                throw new AssertionError("Observer received invalid structured HUD state: health="
                        + health + " maxHealth=" + maxHealth + " food=" + food);
            }
            var camera = minecraft.getCameraEntity();
            if (camera == null || !"Target".equals(camera.getName().getString())) {
                throw new AssertionError("Observer native camera is not attached to Target: "
                        + (camera == null ? "null" : camera.getName().getString()));
            }
            observerNativeSeen = true;
            observerNativeWorldStableTicks = 0;
            ObserverE2eCommon.marker(
                    "observer-native-state-ok.txt",
                    "Observer received protocol-v1 Target camera/HUD structured state over the real server relay.\n"
            );
            ObserverE2eCommon.marker(
                    "observer-native-camera-ok.txt",
                    "Observer Minecraft camera entity is Target with no Mirror Screen or framebuffer texture.\n"
            );
        }

        if (observerNativeSeen && !observerScreenshotRequested) {
            if (nativeGetBoolean("observerSessionActive")
                    && minecraft.gui.screen() == null
                    && !getBoolean("textureRegistered")
                    && getLong("lastFrameId") < 0L) {
                observerNativeWorldStableTicks++;
                if (observerNativeWorldStableTicks >= NATIVE_WORLD_SETTLE_TICKS) {
                    observerScreenshotRequested = true;
                    ObserverE2eCommon.marker(
                            "observer-native-world-settled.txt",
                            "Observer stayed on Minecraft-native Target camera rendering for "
                                    + NATIVE_WORLD_SETTLE_TICKS + " client ticks with no framebuffer.\n"
                    );
                    saveNativeWorldScreenshot(minecraft);
                }
            } else {
                observerNativeWorldStableTicks = 0;
            }
        }

        if (observerNativeSeen && observerScreenshotSaved && !observerStopRequested) {
            if (minecraft.gui.screen() != null) {
                throw new AssertionError("Observer opened a Screen before native Stop request");
            }
            ClientPlayNetworking.send(new ObserverPayloads.Stop());
            observerStopRequested = true;
            ObserverE2eCommon.marker("observer-stop-requested.txt", "Observer sent production Stop payload.\n");
            return;
        }

        if (observerStopRequested && !sessionActive) {
            if (getBoolean("textureRegistered") || nativeGetBoolean("observerSessionActive")) {
                return;
            }
            Screen current = minecraft.gui.screen();
            if (current != null && current.getClass().getName().contains("ObserverMirrorScreen")) {
                return;
            }
            ObserverE2eCommon.marker(
                    "observer-complete.txt",
                    "Observer received native Session(false) and returned from Target camera observation cleanly.\n"
            );
            finished = true;
            stopMinecraft(minecraft);
        }
    }

    private static void saveNativeWorldScreenshot(Minecraft minecraft) {
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            if (image == null) {
                ObserverE2eCommon.fail("observer", "Native-world screenshot callback returned null image");
                return;
            }
            try (NativeImage owned = image) {
                Path output = ObserverE2eCommon.resultsDir().resolve("observer-native-world.png");
                Files.createDirectories(output.getParent());
                owned.writeToFile(output);
                observerScreenshotSaved = true;
                ObserverE2eCommon.marker(
                        "observer-native-world-saved.txt",
                        "Observer native Target-camera framebuffer was saved locally for CI evidence; it was not received over network.\n"
                );
            } catch (IOException error) {
                ObserverE2eCommon.fail("observer", "Failed to save native-world E2E screenshot: " + error);
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
        return field(CLIENT, "ObserverUiClient", name);
    }

    private static Field nativeField(String name) {
        return field(NATIVE_CLIENT, "ObserverNativeClient", name);
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

    private static boolean getBoolean(String name) {
        try {
            return field(name).getBoolean(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static boolean nativeGetBoolean(String name) {
        try {
            return nativeField(name).getBoolean(null);
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

    private static int nativeGetInt(String name) {
        try {
            return nativeField(name).getInt(null);
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

    private static long nativeGetLong(String name) {
        try {
            return nativeField(name).getLong(null);
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
}
