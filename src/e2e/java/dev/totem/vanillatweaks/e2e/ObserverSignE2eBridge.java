package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.client.ObserverSignScreenClient;
import dev.totem.vanillatweaks.network.ObserverSignScreenPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Runs hanging-sign editor semantics across the dedicated-server + two-client Observer path. */
public final class ObserverSignE2eBridge implements ClientModInitializer {
    private static final Class<?> SIGN = ObserverSignScreenClient.class;
    private static final Class<?> GENERIC = ObserverNativeScreenClient.class;
    private static final Class<?> DRIVER = ObserverE2eClient.class;
    private static String role;
    private static boolean observerRequested;
    private static boolean observerSeen;
    private static volatile boolean observerSaved;
    private static boolean observerClosed;
    private static int targetStage;
    private static long targetSequence;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("totem.observer.e2e.enabled")) return;
        role = System.getProperty("totem.observer.e2e.role", "").trim();
        ClientTickEvents.END_CLIENT_TICK.register(ObserverSignE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested && markerExists("observer-native-beacon-closed.txt")
                && markerExists("target-native-beacon-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-sign.txt", "Target may send Sign semantic state.\n");
        }
        if (observerRequested && !observerSeen && mirrorVisible(minecraft)) {
            List<?> lines = getList(SIGN, "remoteLines");
            if (!"hanging_sign".equals(getString(SIGN, "remoteVariant"))
                    || getBoolean(SIGN, "remoteFrontText")
                    || getInt(SIGN, "remoteCurrentLine") != 2
                    || !"black".equals(getString(SIGN, "remoteColor"))
                    || !getBoolean(SIGN, "remoteGlowing")
                    || lines.size() != 4
                    || !List.of("Observer", "semantic", "sign editing", "works").equals(lines)) {
                fail("Sign E2E semantic state mismatch");
                return;
            }
            if (getBoolean(GENERIC, "remoteGenericOpen")) {
                fail("Generic metadata relay competed with Sign semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-sign-ok.txt", "Observer rendered Sign semantic state.\n");
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), ObserverSignE2eBridge::saveScreenshot);
        }
        if (observerSaved && !observerClosed && !getBoolean(SIGN, "remoteOpen") && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-sign-closed.txt", "Sign semantic mirror closed.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (targetStage == 0 && markerExists("observer-ready-for-sign.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(ObserverSignScreenPayloads.CAPABILITY)) {
                fail("Target did not negotiate Sign semantic capability");
                return;
            }
            targetStage = 1;
            ClientPlayNetworking.send(openState());
            ObserverE2eCommon.marker("target-native-sign-state-sent.txt", "Target sent Sign semantic state.\n");
        } else if (targetStage == 1 && markerExists("observer-native-sign-saved.txt")) {
            targetStage = 2;
            ClientPlayNetworking.send(ObserverSignScreenPayloads.closed(++targetSequence));
            ObserverE2eCommon.marker("target-native-sign-close-sent.txt", "Target sent Sign close state.\n");
        }
    }

    private static ObserverSignScreenPayloads.SignState openState() {
        return new ObserverSignScreenPayloads.SignState(
                ObserverSignScreenPayloads.PROTOCOL_VERSION, ++targetSequence, true,
                ObserverSignScreenPayloads.FAMILY_ID, ObserverSignScreenPayloads.HANGING_SIGN_SCREEN_CLASS,
                "Edit Hanging Sign", "hanging_sign", false, 2, "black", true,
                List.of("Observer", "semantic", "sign editing", "works"));
    }

    private static boolean mirrorVisible(Minecraft minecraft) {
        return minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("NativeSignMirrorScreen")
                && getBoolean(SIGN, "remoteOpen") && getLong(SIGN, "lastRemoteSequence") > 0L
                && getLong(SIGN, "extractedFrames") > 0L;
    }

    private static void saveScreenshot(NativeImage image) {
        if (image == null) { fail("Sign screenshot callback returned null"); return; }
        try (NativeImage owned = image) {
            Path output = ObserverE2eCommon.resultsDir().resolve("observer-native-sign.png");
            Files.createDirectories(output.getParent());
            owned.writeToFile(output);
            observerSaved = true;
            ObserverE2eCommon.marker("observer-native-sign-saved.txt", "Sign semantic screenshot saved.\n");
        } catch (Exception error) { fail("Failed to save Sign screenshot: " + error); }
    }

    private static boolean markerExists(String name) { return Files.isRegularFile(ObserverE2eCommon.resultsDir().resolve(name)); }
    private static void fail(String message) { ObserverE2eCommon.fail(role, message); }
    private static Field field(Class<?> owner, String name) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }
    private static boolean getBoolean(Class<?> owner, String name) { try { return field(owner, name).getBoolean(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); } }
    private static int getInt(Class<?> owner, String name) { try { return field(owner, name).getInt(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); } }
    private static long getLong(Class<?> owner, String name) { try { return field(owner, name).getLong(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); } }
    private static String getString(Class<?> owner, String name) { try { return (String) field(owner, name).get(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); } }
    private static List<?> getList(Class<?> owner, String name) { try { return (List<?>) field(owner, name).get(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); } }
    private static void setBoolean(Class<?> owner, String name, boolean value) { try { field(owner, name).setBoolean(null, value); } catch (IllegalAccessException error) { throw new RuntimeException(error); } }
}
