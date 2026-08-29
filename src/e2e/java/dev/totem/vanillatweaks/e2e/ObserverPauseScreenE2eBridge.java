package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.client.ObserverPauseScreenClient;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.Screenshot;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/** Verifies PauseScreen metadata gets semantic reconstruction through the real three-JVM Observer path. */
public final class ObserverPauseScreenE2eBridge implements ClientModInitializer {
    private static final Class<?> PAUSE = ObserverPauseScreenClient.class;
    private static final Class<?> GENERIC = ObserverNativeScreenClient.class;
    private static final Class<?> DRIVER = ObserverE2eClient.class;

    private static String role;
    private static boolean observerRequested;
    private static boolean observerSeen;
    private static volatile boolean observerSaved;
    private static boolean observerClosed;
    private static int targetStage;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("totem.observer.e2e.enabled")) return;
        role = System.getProperty("totem.observer.e2e.role", "").trim();
        ClientTickEvents.END_CLIENT_TICK.register(ObserverPauseScreenE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested
                && markerExists("observer-native-locksmith-management-closed.txt")
                && markerExists("target-native-locksmith-management-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-pause-screen.txt",
                    "Target may send PauseScreen metadata through the dedicated server.\n");
        }

        if (observerRequested && !observerSeen && mirrorVisible(minecraft)) {
            if (!getBoolean(PAUSE, "remoteOpen")
                    || !"Game Menu".equals(getString(PAUSE, "remoteTitle"))) {
                fail("PauseScreen E2E semantic state mismatch");
                return;
            }
            if (getBoolean(GENERIC, "remoteGenericOpen")) {
                fail("Generic semantic-adapter-pending metadata screen competed with PauseScreen semantic view");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-pause-screen-ok.txt",
                    "Observer rendered PauseScreen semantic state.\n");
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), ObserverPauseScreenE2eBridge::saveScreenshot);
        }

        if (observerSaved && !observerClosed && !getBoolean(PAUSE, "remoteOpen")
                && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-pause-screen-closed.txt",
                    "PauseScreen semantic view closed after metadata close.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (targetStage == 0 && markerExists("observer-ready-for-pause-screen.txt")) {
            targetStage = 1;
            ClientPlayNetworking.send(new ObserverPayloads.ScreenState(
                    true, PauseScreen.class.getName(), "Game Menu"));
            ObserverE2eCommon.marker("target-native-pause-screen-state-sent.txt",
                    "Target sent PauseScreen metadata.\n");
        } else if (targetStage == 1 && markerExists("observer-native-pause-screen-saved.txt")) {
            targetStage = 2;
            ClientPlayNetworking.send(new ObserverPayloads.ScreenState(false, "", ""));
            ObserverE2eCommon.marker("target-native-pause-screen-close-sent.txt",
                    "Target sent PauseScreen close metadata.\n");
        }
    }

    private static boolean mirrorVisible(Minecraft minecraft) {
        return minecraft.gui.screen() != null
                && minecraft.gui.screen() instanceof PauseScreen
                && minecraft.gui.screen() instanceof ObserverReadOnlyScreen
                && getBoolean(PAUSE, "remoteOpen")
                && getLong(PAUSE, "extractedFrames") > 0L
                && ObserverE2eRenderBarrier.passed(PauseScreen.class.getName(),
                        getLong(PAUSE, "extractedFrames"));
    }

    private static void saveScreenshot(NativeImage image) {
        if (image == null) {
            fail("PauseScreen screenshot callback returned null");
            return;
        }
        try (NativeImage owned = image) {
            Path output = ObserverE2eCommon.resultsDir().resolve("observer-native-pause-screen.png");
            Files.createDirectories(output.getParent());
            owned.writeToFile(output);
            observerSaved = true;
            ObserverE2eCommon.marker("observer-native-pause-screen-saved.txt",
                    "PauseScreen semantic screenshot saved.\n");
        } catch (Exception error) {
            fail("Failed to save PauseScreen screenshot: " + error);
        }
    }

    private static boolean markerExists(String name) {
        return Files.isRegularFile(ObserverE2eCommon.resultsDir().resolve(name));
    }

    private static void fail(String message) {
        ObserverE2eCommon.fail(role, message);
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private static boolean getBoolean(Class<?> owner, String name) {
        try { return field(owner, name).getBoolean(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static long getLong(Class<?> owner, String name) {
        try { return field(owner, name).getLong(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static String getString(Class<?> owner, String name) {
        try { return (String) field(owner, name).get(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static void setBoolean(Class<?> owner, String name, boolean value) {
        try { field(owner, name).setBoolean(null, value); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
}
