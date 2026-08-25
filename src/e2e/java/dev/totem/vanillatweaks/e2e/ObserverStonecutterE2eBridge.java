package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.client.ObserverStonecutterScreenClient;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStonecutterScreenPayloads;
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

/** Runs Stonecutter semantics across the real dedicated-server + two-client Observer path. */
public final class ObserverStonecutterE2eBridge implements ClientModInitializer {
    private static final Class<?> STONECUTTER = ObserverStonecutterScreenClient.class;
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
        ClientTickEvents.END_CLIENT_TICK.register(ObserverStonecutterE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested && markerExists("observer-native-smithing-closed.txt")
                && markerExists("target-native-smithing-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-stonecutter.txt",
                    "Target may send Stonecutter semantic state through the dedicated server.\n");
        }
        if (observerRequested && !observerSeen && mirrorVisible(minecraft)) {
            if (getInt(STONECUTTER, "remoteSelectedRecipeIndex") != 2
                    || getInt(STONECUTTER, "remoteRecipeCount") != 5
                    || !getBoolean(STONECUTTER, "remoteHasInputItem")
                    || !getBoolean(STONECUTTER, "remoteResultAvailable")
                    || getListSize(STONECUTTER, "remoteSlots") != 38) {
                fail("Stonecutter E2E semantic state mismatch");
                return;
            }
            if (getBoolean(GENERIC, "remoteGenericOpen")) {
                fail("Generic container relay competed with Stonecutter semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-stonecutter-ok.txt", "Observer rendered Stonecutter semantic state.\n");
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), ObserverStonecutterE2eBridge::saveScreenshot);
        }
        if (observerSaved && !observerClosed && !getBoolean(STONECUTTER, "remoteOpen") && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-stonecutter-closed.txt", "Stonecutter semantic mirror closed.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (targetStage == 0 && markerExists("observer-ready-for-stonecutter.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(
                    ObserverStonecutterScreenPayloads.CAPABILITY)) {
                fail("Target did not negotiate Stonecutter semantic capability");
                return;
            }
            targetStage = 1;
            ClientPlayNetworking.send(openState());
            ObserverE2eCommon.marker("target-native-stonecutter-state-sent.txt", "Target sent Stonecutter semantic state.\n");
        } else if (targetStage == 1 && markerExists("observer-native-stonecutter-saved.txt")) {
            targetStage = 2;
            ClientPlayNetworking.send(ObserverStonecutterScreenPayloads.closed(++targetSequence));
            ObserverE2eCommon.marker("target-native-stonecutter-close-sent.txt", "Target sent Stonecutter close state.\n");
        }
    }

    private static ObserverStonecutterScreenPayloads.StonecutterState openState() {
        return new ObserverStonecutterScreenPayloads.StonecutterState(
                ObserverStonecutterScreenPayloads.PROTOCOL_VERSION, ++targetSequence, true,
                ObserverStonecutterScreenPayloads.FAMILY_ID, ObserverStonecutterScreenPayloads.SCREEN_CLASS,
                "Stonecutter", 2, 5, true, true, slots());
    }

    private static List<ObserverNativeScreenPayloads.SlotState> slots() {
        List<ObserverNativeScreenPayloads.SlotState> result = new ArrayList<>();
        result.add(new ObserverNativeScreenPayloads.SlotState(0, 20, 33, "minecraft:stone", 16, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(1, 143, 33, "minecraft:stone_bricks", 1, 0));
        int index = 2;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 84 + row * 18, "", 0, 0));
        for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 142, "", 0, 0));
        return List.copyOf(result);
    }

    private static boolean mirrorVisible(Minecraft minecraft) {
        return minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("NativeStonecutterMirrorScreen")
                && getBoolean(STONECUTTER, "remoteOpen") && getLong(STONECUTTER, "lastRemoteSequence") > 0L
                && getLong(STONECUTTER, "extractedFrames") > 0L;
    }

    private static void saveScreenshot(NativeImage image) {
        if (image == null) { fail("Stonecutter screenshot callback returned null"); return; }
        try (NativeImage owned = image) {
            Path output = ObserverE2eCommon.resultsDir().resolve("observer-native-stonecutter.png");
            Files.createDirectories(output.getParent());
            owned.writeToFile(output);
            observerSaved = true;
            ObserverE2eCommon.marker("observer-native-stonecutter-saved.txt", "Stonecutter semantic screenshot saved.\n");
        } catch (Exception error) { fail("Failed to save Stonecutter screenshot: " + error); }
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
