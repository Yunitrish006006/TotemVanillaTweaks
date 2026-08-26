package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverLoomScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverLoomScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs Loom semantics across the real dedicated-server + two-client Observer path. */
public final class ObserverLoomE2eBridge implements ClientModInitializer {
    private static final Class<?> LOOM = ObserverLoomScreenClient.class;
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
        ClientTickEvents.END_CLIENT_TICK.register(ObserverLoomE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested && markerExists("observer-native-grindstone-closed.txt")
                && markerExists("target-native-grindstone-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-loom.txt",
                    "Target may send Loom semantic state through the dedicated server.\n");
        }
        if (observerRequested && !observerSeen && mirrorVisible(minecraft)) {
            if (!getBoolean(LOOM, "remoteDisplayPatterns") || getBoolean(LOOM, "remoteHasMaxPatterns")
                    || !getBoolean(LOOM, "remoteResultAvailable")
                    || getInt(LOOM, "remoteSelectedPatternIndex") != 5
                    || getInt(LOOM, "remoteStartRow") != 1
                    || getListSize(LOOM, "remotePatternIds") != 20
                    || getListSize(LOOM, "remoteSlots") != 40) {
                fail("Loom E2E semantic state mismatch");
                return;
            }
            if (getBoolean(GENERIC, "remoteGenericOpen")) {
                fail("Generic container relay competed with Loom semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-loom-ok.txt", "Observer rendered Loom semantic state.\n");
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), ObserverLoomE2eBridge::saveScreenshot);
        }
        if (observerSaved && !observerClosed && !getBoolean(LOOM, "remoteOpen") && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-loom-closed.txt", "Loom semantic mirror closed.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (targetStage == 0 && markerExists("observer-ready-for-loom.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(
                    ObserverLoomScreenPayloads.CAPABILITY)) {
                fail("Target did not negotiate Loom semantic capability");
                return;
            }
            targetStage = 1;
            ClientPlayNetworking.send(openState());
            ObserverE2eCommon.marker("target-native-loom-state-sent.txt", "Target sent Loom semantic state.\n");
        } else if (targetStage == 1 && markerExists("observer-native-loom-saved.txt")) {
            targetStage = 2;
            ClientPlayNetworking.send(ObserverLoomScreenPayloads.closed(++targetSequence));
            ObserverE2eCommon.marker("target-native-loom-close-sent.txt", "Target sent Loom close state.\n");
        }
    }

    private static ObserverLoomScreenPayloads.LoomState openState() {
        List<String> patterns = new ArrayList<>();
        for (int i = 0; i < 20; i++) patterns.add("minecraft:e2e_pattern_" + i);
        return new ObserverLoomScreenPayloads.LoomState(
                ObserverLoomScreenPayloads.PROTOCOL_VERSION, ++targetSequence, true,
                ObserverLoomScreenPayloads.FAMILY_ID, ObserverLoomScreenPayloads.SCREEN_CLASS,
                "Loom", 5, 1, true, false, true, List.copyOf(patterns), slots());
    }

    private static List<ObserverNativeScreenPayloads.SlotState> slots() {
        List<ObserverNativeScreenPayloads.SlotState> result = new ArrayList<>();
        result.add(new ObserverNativeScreenPayloads.SlotState(0, 13, 26, "minecraft:white_banner", 1, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(1, 33, 26, "minecraft:blue_dye", 1, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(2, 23, 45, "", 0, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(3, 143, 57, "minecraft:white_banner", 1, 0));
        int index = 4;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 84 + row * 18, "", 0, 0));
        for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 142, "", 0, 0));
        return List.copyOf(result);
    }

    private static boolean mirrorVisible(Minecraft minecraft) {
        return minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("NativeLoomMirrorScreen")
                && getBoolean(LOOM, "remoteOpen") && getLong(LOOM, "lastRemoteSequence") > 0L
                && getLong(LOOM, "extractedFrames") > 0L;
    }

    private static void saveScreenshot(NativeImage image) {
        if (image == null) { fail("Loom screenshot callback returned null"); return; }
        try (NativeImage owned = image) {
            Path output = ObserverE2eCommon.resultsDir().resolve("observer-native-loom.png");
            Files.createDirectories(output.getParent());
            owned.writeToFile(output);
            observerSaved = true;
            ObserverE2eCommon.marker("observer-native-loom-saved.txt", "Loom semantic screenshot saved.\n");
        } catch (Exception error) { fail("Failed to save Loom screenshot: " + error); }
    }

    private static boolean markerExists(String name) { return Files.isRegularFile(ObserverE2eCommon.resultsDir().resolve(name)); }
    private static void fail(String message) { ObserverE2eCommon.fail(role, message); }
    private static Field field(Class<?> owner, String name) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }
    private static boolean getBoolean(Class<?> owner, String name) {
        try { return field(owner, name).getBoolean(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static int getInt(Class<?> owner, String name) {
        try { return field(owner, name).getInt(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static long getLong(Class<?> owner, String name) {
        try { return field(owner, name).getLong(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static int getListSize(Class<?> owner, String name) {
        try { Object value = field(owner, name).get(null); return value instanceof List<?> list ? list.size() : -1; }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static void setBoolean(Class<?> owner, String name, boolean value) {
        try { field(owner, name).setBoolean(null, value); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
}
