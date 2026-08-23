package dev.totem.vanillatweaks.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverUiClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Boots a real Minecraft client and exercises the Observer View framebuffer capture and mirror render paths.
 * A true two-client observer/target transport test is intentionally separate from this smoke test.
 */
public final class ObserverUiClientGameTest implements FabricClientGameTest {
    private static final Class<?> OBSERVER = ObserverUiClient.class;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();

            context.setScreen(() -> new Screen(Component.literal("Observer Capture Target")) {
                @Override
                public boolean isPauseScreen() {
                    return false;
                }
            });
            context.waitFor(mc -> mc.gui.screen() != null
                    && "Observer Capture Target".equals(mc.gui.screen().getTitle().getString()));
            context.takeScreenshot("observer-ui-source-screen");

            exerciseFramebufferCapture(context);

            context.setScreen(() -> null);
            context.waitForScreen(null);
            exerciseMirrorRendering(context);
        } finally {
            forceClientCleanup(context);
        }
    }

    private static void exerciseFramebufferCapture(ClientGameTestContext context) {
        final long[] previousFrameId = new long[1];
        context.runOnClient(minecraft -> {
            previousFrameId[0] = getLong("nextFrameId");
            setBoolean("captureEnabled", true);
            setInt("captureMaxWidth", 640);
            setInt("captureMaxHeight", 360);
            setInt("captureFps", 1);
            setLong("lastCaptureNanos", System.nanoTime());
            invoke("captureFrame", new Class<?>[]{minecraft.getClass()}, minecraft);
        });

        context.waitFor(minecraft -> {
            try {
                return !getBoolean("captureInFlight") && getLong("nextFrameId") > previousFrameId[0];
            } catch (ReflectiveOperationException error) {
                throw new RuntimeException(error);
            }
        }, 100);

        context.runOnClient(minecraft -> setBoolean("captureEnabled", false));
    }

    private static void exerciseMirrorRendering(ClientGameTestContext context) {
        byte[] png = createTestPng();
        UUID target = UUID.randomUUID();

        context.runOnClient(minecraft -> {
            setBoolean("sessionActive", true);
            setObject("targetId", target);
            setObject("targetName", "ObserverSmokeTarget");
            setBoolean("remoteScreenOpen", true);
            setInt("frameWidth", 160);
            setInt("frameHeight", 90);
            setInt("sourceWidth", 160);
            setInt("sourceHeight", 90);
            setInt("remoteMouseX", 80);
            setInt("remoteMouseY", 45);
            invoke("installFrameTexture", new Class<?>[]{byte[].class}, png);
            if (!getBoolean("textureRegistered")) {
                throw new AssertionError("Observer frame texture was not registered");
            }
            invoke("ensureMirrorScreen", new Class<?>[0]);
        });

        context.waitFor(minecraft -> minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("ObserverMirrorScreen"));
        context.waitTicks(2);
        context.takeScreenshot("observer-ui-mirror-screen");

        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);
        context.waitFor(minecraft -> {
            try {
                return !getBoolean("sessionActive") && !getBoolean("textureRegistered");
            } catch (ReflectiveOperationException error) {
                throw new RuntimeException(error);
            }
        }, 100);
    }

    private static byte[] createTestPng() {
        NativeImage image = new NativeImage(160, 90, false);
        Path temp = null;
        try {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    boolean checker = ((x / 10) + (y / 10)) % 2 == 0;
                    image.setPixel(x, y, checker ? 0xFF24405C : 0xFF182430);
                }
            }
            temp = Files.createTempFile("totem-observer-client-gametest-", ".png");
            image.writeToFile(temp);
            return Files.readAllBytes(temp);
        } catch (Exception error) {
            throw new RuntimeException("Failed to create observer client gametest PNG", error);
        } finally {
            image.close();
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void forceClientCleanup(ClientGameTestContext context) {
        try {
            context.runOnClient(minecraft -> {
                setBoolean("captureEnabled", false);
                setBoolean("sessionActive", false);
                setBoolean("remoteScreenOpen", false);
                invoke("closeMirrorScreen", new Class<?>[0]);
                invoke("releaseFrameTexture", new Class<?>[0]);
            });
        } catch (Throwable ignored) {
        }
    }

    private static Field field(String name) throws ReflectiveOperationException {
        Field field = OBSERVER.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(String name, Class<?>... parameterTypes) throws ReflectiveOperationException {
        Method method = OBSERVER.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static void invoke(String name, Class<?>[] parameterTypes, Object... args) throws ReflectiveOperationException {
        method(name, parameterTypes).invoke(null, args);
    }

    private static boolean getBoolean(String name) throws ReflectiveOperationException {
        return field(name).getBoolean(null);
    }

    private static long getLong(String name) throws ReflectiveOperationException {
        return field(name).getLong(null);
    }

    private static void setBoolean(String name, boolean value) throws ReflectiveOperationException {
        field(name).setBoolean(null, value);
    }

    private static void setInt(String name, int value) throws ReflectiveOperationException {
        field(name).setInt(null, value);
    }

    private static void setLong(String name, long value) throws ReflectiveOperationException {
        field(name).setLong(null, value);
    }

    private static void setObject(String name, Object value) throws ReflectiveOperationException {
        field(name).set(null, value);
    }
}
