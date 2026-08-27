package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverGrindstoneScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverGrindstoneScreenPayloads;
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

/** Runs Grindstone semantics across the real dedicated-server + two-client Observer path. */
public final class ObserverGrindstoneE2eBridge implements ClientModInitializer {
    private static final Class<?> GRINDSTONE = ObserverGrindstoneScreenClient.class;
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
        ClientTickEvents.END_CLIENT_TICK.register(ObserverGrindstoneE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested && markerExists("observer-native-stonecutter-closed.txt")
                && markerExists("target-native-stonecutter-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-grindstone.txt",
                    "Target may send Grindstone semantic state through the dedicated server.\n");
        }
        if (observerRequested && !observerSeen && mirrorVisible(minecraft)) {
            if (!getBoolean(GRINDSTONE, "remotePrimaryInputPresent")
                    || !getBoolean(GRINDSTONE, "remoteSecondaryInputPresent")
                    || !getBoolean(GRINDSTONE, "remoteResultAvailable")
                    || getBoolean(GRINDSTONE, "remoteInvalidCombination")
                    || getListSize(GRINDSTONE, "remoteSlots") != 39) {
                fail("Grindstone E2E semantic state mismatch");
                return;
            }
            if (getBoolean(GENERIC, "remoteGenericOpen")) {
                fail("Generic container relay competed with Grindstone semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-grindstone-ok.txt", "Observer rendered Grindstone semantic state.\n");
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), ObserverGrindstoneE2eBridge::saveScreenshot);
        }
        if (observerSaved && !observerClosed && !getBoolean(GRINDSTONE, "remoteOpen") && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-grindstone-closed.txt", "Grindstone semantic mirror closed.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (targetStage == 0 && markerExists("observer-ready-for-grindstone.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(
                    ObserverGrindstoneScreenPayloads.CAPABILITY)) {
                fail("Target did not negotiate Grindstone semantic capability");
                return;
            }
            targetStage = 1;
            ClientPlayNetworking.send(openState());
            ObserverE2eCommon.marker("target-native-grindstone-state-sent.txt", "Target sent Grindstone semantic state.\n");
        } else if (targetStage == 1 && markerExists("observer-native-grindstone-saved.txt")) {
            targetStage = 2;
            ClientPlayNetworking.send(ObserverGrindstoneScreenPayloads.closed(++targetSequence));
            ObserverE2eCommon.marker("target-native-grindstone-close-sent.txt", "Target sent Grindstone close state.\n");
        }
    }

    private static ObserverGrindstoneScreenPayloads.GrindstoneState openState() {
        return new ObserverGrindstoneScreenPayloads.GrindstoneState(
                ObserverGrindstoneScreenPayloads.PROTOCOL_VERSION, ++targetSequence, true,
                ObserverGrindstoneScreenPayloads.FAMILY_ID, ObserverGrindstoneScreenPayloads.SCREEN_CLASS,
                "Repair & Disenchant", true, true, true, false, slots());
    }

    private static List<ObserverNativeScreenPayloads.SlotState> slots() {
        List<ObserverNativeScreenPayloads.SlotState> result = new ArrayList<>();
        result.add(new ObserverNativeScreenPayloads.SlotState(0, 49, 19, "minecraft:iron_sword", 1, 120));
        result.add(new ObserverNativeScreenPayloads.SlotState(1, 49, 40, "minecraft:iron_sword", 1, 80));
        result.add(new ObserverNativeScreenPayloads.SlotState(2, 129, 34, "minecraft:iron_sword", 1, 20));
        int index = 3;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 84 + row * 18, "", 0, 0));
        for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 142, "", 0, 0));
        return List.copyOf(result);
    }

    private static boolean mirrorVisible(Minecraft minecraft) {
        return minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("NativeGrindstoneMirrorScreen")
                && getBoolean(GRINDSTONE, "remoteOpen")
                && ObserverE2eSequenceEvidence.accepted(ObserverGrindstoneScreenPayloads.FAMILY_ID) > 0L
                && getLong(GRINDSTONE, "extractedFrames") > 0L;
    }

    private static void saveScreenshot(NativeImage image) {
        if (image == null) { fail("Grindstone screenshot callback returned null"); return; }
        try (NativeImage owned = image) {
            Path output = ObserverE2eCommon.resultsDir().resolve("observer-native-grindstone.png");
            Files.createDirectories(output.getParent());
            owned.writeToFile(output);
            observerSaved = true;
            ObserverE2eCommon.marker("observer-native-grindstone-saved.txt", "Grindstone semantic screenshot saved.\n");
        } catch (Exception error) { fail("Failed to save Grindstone screenshot: " + error); }
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
    private static int getListSize(Class<?> owner, String name) {
        try { Object value = field(owner, name).get(null); return value instanceof List<?> list ? list.size() : -1; }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static void setBoolean(Class<?> owner, String name, boolean value) {
        try { field(owner, name).setBoolean(null, value); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
}
