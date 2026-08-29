package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.client.ObserverSmithingScreenClient;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverSmithingScreenPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.Screenshot;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs Smithing Table semantics across the real dedicated-server + two-client Observer path. */
public final class ObserverSmithingE2eBridge implements ClientModInitializer {
    private static final Class<?> SMITHING = ObserverSmithingScreenClient.class;
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
        ClientTickEvents.END_CLIENT_TICK.register(ObserverSmithingE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested && markerExists("observer-native-brewing-closed.txt")
                && markerExists("target-native-brewing-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-smithing.txt",
                    "Target may send Smithing Table semantic state through the dedicated server.\n");
        }
        if (observerRequested && !observerSeen && mirrorVisible(minecraft)) {
            if (getBoolean(SMITHING, "remoteRecipeError") || !getBoolean(SMITHING, "remoteResultAvailable")
                    || getListSize(SMITHING, "remoteSlots") != 40) {
                fail("Smithing E2E semantic state mismatch");
                return;
            }
            SmithingScreen screen = (SmithingScreen) minecraft.gui.screen();
            if (!ObserverE2eMenuAssertions.hasNonEmptySlots(screen, 0, 1, 2, 3)) {
                fail("Smithing production Menu did not apply relayed template, input, material, and result slots");
                return;
            }
            if (getBoolean(GENERIC, "remoteGenericOpen")) {
                fail("Generic container relay competed with Smithing semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-smithing-ok.txt", "Observer rendered Smithing semantic state.\n");
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), ObserverSmithingE2eBridge::saveScreenshot);
        }
        if (observerSaved && !observerClosed && !getBoolean(SMITHING, "remoteOpen") && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-smithing-closed.txt", "Smithing semantic view closed.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (targetStage == 0 && markerExists("observer-ready-for-smithing.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(
                    ObserverSmithingScreenPayloads.CAPABILITY)) {
                fail("Target did not negotiate Smithing semantic capability");
                return;
            }
            targetStage = 1;
            ClientPlayNetworking.send(openState());
            ObserverE2eCommon.marker("target-native-smithing-state-sent.txt", "Target sent Smithing semantic state.\n");
        } else if (targetStage == 1 && markerExists("observer-native-smithing-saved.txt")) {
            targetStage = 2;
            ClientPlayNetworking.send(ObserverSmithingScreenPayloads.closed(++targetSequence));
            ObserverE2eCommon.marker("target-native-smithing-close-sent.txt", "Target sent Smithing close state.\n");
        }
    }

    private static ObserverSmithingScreenPayloads.SmithingState openState() {
        return new ObserverSmithingScreenPayloads.SmithingState(
                ObserverSmithingScreenPayloads.PROTOCOL_VERSION, ++targetSequence, true,
                ObserverSmithingScreenPayloads.FAMILY_ID, ObserverSmithingScreenPayloads.SCREEN_CLASS,
                "Smithing Table", false, true, slots());
    }

    private static List<ObserverNativeScreenPayloads.SlotState> slots() {
        List<ObserverNativeScreenPayloads.SlotState> result = new ArrayList<>();
        result.add(new ObserverNativeScreenPayloads.SlotState(0, 8, 48, "minecraft:netherite_upgrade_smithing_template", 1, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(1, 26, 48, "minecraft:diamond_chestplate", 1, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(2, 44, 48, "minecraft:netherite_ingot", 1, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(3, 98, 48, "minecraft:netherite_chestplate", 1, 0));
        int index = 4;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 84 + row * 18, "", 0, 0));
        for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 142, "", 0, 0));
        return List.copyOf(result);
    }

    private static boolean mirrorVisible(Minecraft minecraft) {
        return minecraft.gui.screen() != null
                && minecraft.gui.screen() instanceof SmithingScreen
                && minecraft.gui.screen() instanceof ObserverReadOnlyScreen
                && getBoolean(SMITHING, "remoteOpen")
                && ObserverE2eSequenceEvidence.accepted(ObserverSmithingScreenPayloads.FAMILY_ID) > 0L
                && getLong(SMITHING, "extractedFrames") > 0L
                && ObserverE2eRenderBarrier.passed(ObserverSmithingScreenPayloads.FAMILY_ID,
                        getLong(SMITHING, "extractedFrames"));
    }

    private static void saveScreenshot(NativeImage image) {
        if (image == null) { fail("Smithing screenshot callback returned null"); return; }
        try (NativeImage owned = image) {
            Path output = ObserverE2eCommon.resultsDir().resolve("observer-native-smithing.png");
            Files.createDirectories(output.getParent());
            owned.writeToFile(output);
            observerSaved = true;
            ObserverE2eCommon.marker("observer-native-smithing-saved.txt", "Smithing semantic screenshot saved.\n");
        } catch (Exception error) { fail("Failed to save Smithing screenshot: " + error); }
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
