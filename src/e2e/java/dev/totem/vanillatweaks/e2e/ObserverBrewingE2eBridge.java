package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverBrewingScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverBrewingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.Screenshot;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs Brewing Stand semantics across the real dedicated-server + two-client Observer path. */
public final class ObserverBrewingE2eBridge implements ClientModInitializer {
    private static final Class<?> BREWING = ObserverBrewingScreenClient.class;
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
        ClientTickEvents.END_CLIENT_TICK.register(ObserverBrewingE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested
                && markerExists("observer-native-villagers-woodcutter-closed.txt")
                && markerExists("target-native-villagers-woodcutter-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-brewing.txt",
                    "Target may send Brewing Stand semantic state through the dedicated server.\n");
        }
        if (observerRequested && !observerSeen && mirrorVisible(minecraft)) {
            if (getInt(BREWING, "remoteBrewingTicks") != 180 || getInt(BREWING, "remoteFuel") != 12
                    || getListSize(BREWING, "remoteSlots") != 41) {
                fail("Brewing E2E semantic state mismatch");
                return;
            }
            BrewingStandScreen screen = (BrewingStandScreen) minecraft.gui.screen();
            if (!ObserverE2eMenuAssertions.hasNonEmptySlots(screen, 0, 1, 2, 3, 4)) {
                fail("Brewing production Menu did not apply relayed bottle, ingredient, and fuel slots");
                return;
            }
            if (getBoolean(GENERIC, "remoteGenericOpen")) {
                fail("Generic container relay competed with Brewing semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-brewing-ok.txt", "Observer rendered Brewing semantic state.\n");
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), ObserverBrewingE2eBridge::saveScreenshot);
        }
        if (observerSaved && !observerClosed && !getBoolean(BREWING, "remoteOpen") && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-brewing-closed.txt", "Brewing semantic view closed.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (targetStage == 0 && markerExists("observer-ready-for-brewing.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(
                    ObserverBrewingScreenPayloads.CAPABILITY)) {
                fail("Target did not negotiate Brewing semantic capability");
                return;
            }
            targetStage = 1;
            ClientPlayNetworking.send(openState());
            ObserverE2eCommon.marker("target-native-brewing-state-sent.txt", "Target sent Brewing semantic state.\n");
        } else if (targetStage == 1 && markerExists("observer-native-brewing-saved.txt")) {
            targetStage = 2;
            ClientPlayNetworking.send(ObserverBrewingScreenPayloads.closed(++targetSequence));
            ObserverE2eCommon.marker("target-native-brewing-close-sent.txt", "Target sent Brewing close state.\n");
        }
    }

    private static ObserverBrewingScreenPayloads.BrewingState openState() {
        return new ObserverBrewingScreenPayloads.BrewingState(
                ObserverBrewingScreenPayloads.PROTOCOL_VERSION, ++targetSequence, true,
                ObserverBrewingScreenPayloads.FAMILY_ID, ObserverBrewingScreenPayloads.SCREEN_CLASS,
                "Brewing Stand", 180, 12, slots());
    }

    private static List<ObserverNativeScreenPayloads.SlotState> slots() {
        List<ObserverNativeScreenPayloads.SlotState> result = new ArrayList<>();
        result.add(new ObserverNativeScreenPayloads.SlotState(0, 56, 51, "minecraft:potion", 1, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(1, 79, 58, "minecraft:potion", 1, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(2, 102, 51, "minecraft:potion", 1, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(3, 79, 17, "minecraft:nether_wart", 3, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(4, 17, 17, "minecraft:blaze_powder", 12, 0));
        int index = 5;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 84 + row * 18, "", 0, 0));
        for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 142, "", 0, 0));
        return List.copyOf(result);
    }

    private static boolean mirrorVisible(Minecraft minecraft) {
        return minecraft.gui.screen() != null
                && minecraft.gui.screen() instanceof BrewingStandScreen
                && minecraft.gui.screen() instanceof ObserverReadOnlyScreen
                && getBoolean(BREWING, "remoteOpen")
                && ObserverE2eSequenceEvidence.accepted(ObserverBrewingScreenPayloads.FAMILY_ID) > 0L
                && getLong(BREWING, "extractedFrames") > 0L
                && ObserverE2eRenderBarrier.passed(ObserverBrewingScreenPayloads.FAMILY_ID,
                        getLong(BREWING, "extractedFrames"));
    }

    private static void saveScreenshot(NativeImage image) {
        if (image == null) { fail("Brewing screenshot callback returned null"); return; }
        try (NativeImage owned = image) {
            Path output = ObserverE2eCommon.resultsDir().resolve("observer-native-brewing.png");
            Files.createDirectories(output.getParent());
            owned.writeToFile(output);
            observerSaved = true;
            ObserverE2eCommon.marker("observer-native-brewing-saved.txt", "Brewing semantic screenshot saved.\n");
        } catch (Exception error) { fail("Failed to save Brewing screenshot: " + error); }
    }

    private static boolean markerExists(String name) {
        return Files.isRegularFile(ObserverE2eCommon.resultsDir().resolve(name));
    }
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
