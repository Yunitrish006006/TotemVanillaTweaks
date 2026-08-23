package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverUiClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/** Test-only Target/Observer client driver for the real three-process Observer View E2E. */
public final class ObserverE2eClient implements ClientModInitializer {
    private static final Class<?> CLIENT = ObserverUiClient.class;
    private static final int CLIENT_TIMEOUT_TICKS = 20 * 120;

    private static String role;
    private static int ticks;
    private static boolean targetReady;
    private static boolean targetCaptureSeen;
    private static boolean targetScreenOpened;
    private static boolean observerFrameSeen;
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

    private static void tickTarget(Minecraft minecraft) {
        if (getBoolean("sessionActive")) {
            throw new AssertionError("Target JVM incorrectly entered observer session state");
        }
        if (getBoolean("textureRegistered")) {
            throw new AssertionError("Target JVM incorrectly installed observer mirror texture");
        }

        if (!targetReady) {
            targetReady = true;
            ObserverE2eCommon.marker(
                    "target-ready.txt",
                    "Target joined the dedicated server with observer-only client state disabled.\n"
            );
        }

        if (!targetScreenOpened) {
            minecraft.setScreenAndShow(new TargetScreen());
            targetScreenOpened = true;
            ObserverE2eCommon.marker("target-screen-open.txt", "Target opened the E2E source Screen.\n");
        }

        boolean captureEnabled = getBoolean("captureEnabled");
        if (captureEnabled && !targetCaptureSeen) {
            targetCaptureSeen = true;
            ObserverE2eCommon.marker(
                    "target-capture-enabled.txt",
                    "Target received CaptureControl(true) while observer-only state remained false.\n"
            );
        }

        if (targetCaptureSeen && !captureEnabled) {
            if (getLong("nextFrameId") <= 0L) {
                throw new AssertionError("Target capture was enabled and then stopped without sending any frame");
            }
            ObserverE2eCommon.marker(
                    "target-complete.txt",
                    "Target sent at least one framebuffer and received CaptureControl(false) after observer stopped.\n"
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
            if (!getBoolean("remoteScreenOpen")) {
                throw new AssertionError("Observer mirror texture arrived without ScreenRelay(open=true)");
            }

            observerFrameSeen = true;
            ObserverE2eCommon.marker(
                    "observer-frame-ok.txt",
                    "Observer received and installed a real relayed Target framebuffer.\n"
            );
            saveMirrorScreenshot(minecraft);
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
                        "Observer Mirror screenshot was flushed to disk before Stop.\n"
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

    private static final class TargetScreen extends Screen {
        private TargetScreen() {
            super(Component.literal("Observer E2E Target UI"));
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF163047);
            graphics.fill(24, 36, Math.max(25, width - 24), Math.max(37, height - 36), 0xFF315D7D);
            graphics.fill(48, 72, Math.max(49, width - 48), Math.max(73, height - 72), 0xFF102231);
            graphics.text(font, Component.literal("TOTEM OBSERVER TWO-CLIENT E2E"), 32, 48, 0xFFFFFFFF);
            graphics.text(font, Component.literal("TARGET FRAMEBUFFER SOURCE"), 32, 62, 0xFFE0E0E0);
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }
    }
}
