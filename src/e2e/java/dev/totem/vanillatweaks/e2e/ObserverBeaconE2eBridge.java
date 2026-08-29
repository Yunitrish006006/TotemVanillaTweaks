package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverBeaconScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverBeaconScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.Screenshot;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs Beacon semantics across the real dedicated-server + two-client Observer path. */
public final class ObserverBeaconE2eBridge implements ClientModInitializer {
    private static final Class<?> BEACON = ObserverBeaconScreenClient.class;
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
        ClientTickEvents.END_CLIENT_TICK.register(ObserverBeaconE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested && markerExists("observer-native-cartography-closed.txt")
                && markerExists("target-native-cartography-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-beacon.txt",
                    "Target may send Beacon semantic state through the dedicated server.\n");
        }
        if (observerRequested && !observerSeen && mirrorVisible(minecraft)) {
            if (getInt(BEACON, "remoteLevels") != 4
                    || !"minecraft:speed".equals(getString(BEACON, "remotePrimaryEffectId"))
                    || !"minecraft:regeneration".equals(getString(BEACON, "remoteSecondaryEffectId"))
                    || !getBoolean(BEACON, "remotePaymentPresent")
                    || !getBoolean(BEACON, "remoteCanConfirm")
                    || getListSize(BEACON, "remoteSlots") != 37) {
                fail("Beacon E2E semantic state mismatch");
                return;
            }
            BeaconScreen screen = (BeaconScreen) minecraft.gui.screen();
            if (!ObserverE2eMenuAssertions.hasNonEmptySlots(screen, 0)) {
                fail("Beacon production Menu did not apply relayed payment slot");
                return;
            }
            if (getBoolean(GENERIC, "remoteGenericOpen")) {
                fail("Generic container relay competed with Beacon semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-beacon-ok.txt", "Observer rendered Beacon semantic state.\n");
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), ObserverBeaconE2eBridge::saveScreenshot);
        }
        if (observerSaved && !observerClosed && !getBoolean(BEACON, "remoteOpen") && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-beacon-closed.txt", "Beacon semantic view closed.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (targetStage == 0 && markerExists("observer-ready-for-beacon.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(
                    ObserverBeaconScreenPayloads.CAPABILITY)) {
                fail("Target did not negotiate Beacon semantic capability");
                return;
            }
            targetStage = 1;
            ClientPlayNetworking.send(openState());
            ObserverE2eCommon.marker("target-native-beacon-state-sent.txt", "Target sent Beacon semantic state.\n");
        } else if (targetStage == 1 && markerExists("observer-native-beacon-saved.txt")) {
            targetStage = 2;
            ClientPlayNetworking.send(ObserverBeaconScreenPayloads.closed(++targetSequence));
            ObserverE2eCommon.marker("target-native-beacon-close-sent.txt", "Target sent Beacon close state.\n");
        }
    }

    private static ObserverBeaconScreenPayloads.BeaconState openState() {
        return new ObserverBeaconScreenPayloads.BeaconState(
                ObserverBeaconScreenPayloads.PROTOCOL_VERSION, ++targetSequence, true,
                ObserverBeaconScreenPayloads.FAMILY_ID, ObserverBeaconScreenPayloads.SCREEN_CLASS,
                "Beacon", 4, "minecraft:speed", "minecraft:regeneration", true, true, slots());
    }

    private static List<ObserverNativeScreenPayloads.SlotState> slots() {
        List<ObserverNativeScreenPayloads.SlotState> result = new ArrayList<>();
        result.add(new ObserverNativeScreenPayloads.SlotState(0, 136, 110, "minecraft:emerald", 1, 0));
        int index = 1;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 36 + col * 18, 137 + row * 18, "", 0, 0));
        for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 36 + col * 18, 195, "", 0, 0));
        return List.copyOf(result);
    }

    private static boolean mirrorVisible(Minecraft minecraft) {
        return minecraft.gui.screen() != null
                && minecraft.gui.screen() instanceof BeaconScreen
                && minecraft.gui.screen() instanceof ObserverReadOnlyScreen
                && getBoolean(BEACON, "remoteOpen")
                && ObserverE2eSequenceEvidence.accepted(ObserverBeaconScreenPayloads.FAMILY_ID) > 0L
                && getLong(BEACON, "extractedFrames") > 0L
                && ObserverE2eRenderBarrier.passed(ObserverBeaconScreenPayloads.FAMILY_ID,
                        getLong(BEACON, "extractedFrames"));
    }

    private static void saveScreenshot(NativeImage image) {
        if (image == null) { fail("Beacon screenshot callback returned null"); return; }
        try (NativeImage owned = image) {
            Path output = ObserverE2eCommon.resultsDir().resolve("observer-native-beacon.png");
            Files.createDirectories(output.getParent());
            owned.writeToFile(output);
            observerSaved = true;
            ObserverE2eCommon.marker("observer-native-beacon-saved.txt", "Beacon semantic screenshot saved.\n");
        } catch (Exception error) { fail("Failed to save Beacon screenshot: " + error); }
    }

    private static boolean markerExists(String name) { return Files.isRegularFile(ObserverE2eCommon.resultsDir().resolve(name)); }
    private static void fail(String message) { ObserverE2eCommon.fail(role, message); }
    private static Field field(Class<?> owner, String name) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }
    private static boolean getBoolean(Class<?> owner, String name) {
        try { return field(owner, name).getBoolean(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static int getInt(Class<?> owner, String name) {
        try { return field(owner, name).getInt(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static long getLong(Class<?> owner, String name) {
        try { return field(owner, name).getLong(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static String getString(Class<?> owner, String name) {
        try { return (String) field(owner, name).get(null); } catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static int getListSize(Class<?> owner, String name) {
        try { Object value = field(owner, name).get(null); return value instanceof List<?> list ? list.size() : -1; }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static void setBoolean(Class<?> owner, String name, boolean value) {
        try { field(owner, name).setBoolean(null, value); } catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
}
