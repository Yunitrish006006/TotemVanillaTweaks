package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverCartographyScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverCartographyScreenPayloads;
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

/** Runs Cartography Table semantics across the real dedicated-server + two-client Observer path. */
public final class ObserverCartographyE2eBridge implements ClientModInitializer {
    private static final Class<?> CARTOGRAPHY = ObserverCartographyScreenClient.class;
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
        ClientTickEvents.END_CLIENT_TICK.register(ObserverCartographyE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested && markerExists("observer-native-loom-closed.txt")
                && markerExists("target-native-loom-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-cartography.txt",
                    "Target may send Cartography semantic state through the dedicated server.\n");
        }
        if (observerRequested && !observerSeen && mirrorVisible(minecraft)) {
            if (!"scale".equals(getString(CARTOGRAPHY, "remoteOperation"))
                    || !getBoolean(CARTOGRAPHY, "remoteMapPresent")
                    || !getBoolean(CARTOGRAPHY, "remoteAdditionalPresent")
                    || !getBoolean(CARTOGRAPHY, "remoteResultAvailable")
                    || getListSize(CARTOGRAPHY, "remoteSlots") != 39) {
                fail("Cartography E2E semantic state mismatch");
                return;
            }
            if (getBoolean(GENERIC, "remoteGenericOpen")) {
                fail("Generic container relay competed with Cartography semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-cartography-ok.txt",
                    "Observer rendered Cartography semantic state.\n");
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), ObserverCartographyE2eBridge::saveScreenshot);
        }
        if (observerSaved && !observerClosed && !getBoolean(CARTOGRAPHY, "remoteOpen")
                && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-cartography-closed.txt",
                    "Cartography semantic mirror closed.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (targetStage == 0 && markerExists("observer-ready-for-cartography.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(
                    ObserverCartographyScreenPayloads.CAPABILITY)) {
                fail("Target did not negotiate Cartography semantic capability");
                return;
            }
            targetStage = 1;
            ClientPlayNetworking.send(openState());
            ObserverE2eCommon.marker("target-native-cartography-state-sent.txt",
                    "Target sent Cartography semantic state.\n");
        } else if (targetStage == 1 && markerExists("observer-native-cartography-saved.txt")) {
            targetStage = 2;
            ClientPlayNetworking.send(ObserverCartographyScreenPayloads.closed(++targetSequence));
            ObserverE2eCommon.marker("target-native-cartography-close-sent.txt",
                    "Target sent Cartography close state.\n");
        }
    }

    private static ObserverCartographyScreenPayloads.CartographyState openState() {
        return new ObserverCartographyScreenPayloads.CartographyState(
                ObserverCartographyScreenPayloads.PROTOCOL_VERSION, ++targetSequence, true,
                ObserverCartographyScreenPayloads.FAMILY_ID, ObserverCartographyScreenPayloads.SCREEN_CLASS,
                "Cartography Table", "scale", true, true, true, slots());
    }

    private static List<ObserverNativeScreenPayloads.SlotState> slots() {
        List<ObserverNativeScreenPayloads.SlotState> result = new ArrayList<>();
        result.add(new ObserverNativeScreenPayloads.SlotState(0, 15, 15, "minecraft:filled_map", 1, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(1, 15, 52, "minecraft:paper", 1, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(2, 145, 39, "minecraft:filled_map", 1, 0));
        int index = 3;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 84 + row * 18, "", 0, 0));
        for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 142, "", 0, 0));
        return List.copyOf(result);
    }

    private static boolean mirrorVisible(Minecraft minecraft) {
        return minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("NativeCartographyMirrorScreen")
                && getBoolean(CARTOGRAPHY, "remoteOpen")
                && getLong(CARTOGRAPHY, "lastRemoteSequence") > 0L
                && getLong(CARTOGRAPHY, "extractedFrames") > 0L;
    }

    private static void saveScreenshot(NativeImage image) {
        if (image == null) { fail("Cartography screenshot callback returned null"); return; }
        try (NativeImage owned = image) {
            Path output = ObserverE2eCommon.resultsDir().resolve("observer-native-cartography.png");
            Files.createDirectories(output.getParent());
            owned.writeToFile(output);
            observerSaved = true;
            ObserverE2eCommon.marker("observer-native-cartography-saved.txt",
                    "Cartography semantic screenshot saved.\n");
        } catch (Exception error) { fail("Failed to save Cartography screenshot: " + error); }
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
    private static long getLong(Class<?> owner, String name) {
        try { return field(owner, name).getLong(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static String getString(Class<?> owner, String name) {
        try { return (String) field(owner, name).get(null); }
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
