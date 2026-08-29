package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverNativeEnchantingScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverEnchantingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.Screenshot;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Inserts a real cross-JVM enchanting semantic relay phase after anvil and before Stop. */
public final class ObserverEnchantingE2eBridge implements ClientModInitializer {
    private static final Class<?> ENCHANTING = ObserverNativeEnchantingScreenClient.class;
    private static final Class<?> GENERIC = ObserverNativeScreenClient.class;
    private static final Class<?> DRIVER = ObserverE2eClient.class;

    private static String role;
    private static boolean observerRequested;
    private static boolean observerSeen;
    private static volatile boolean observerSaved;
    private static boolean observerClosed;
    private static boolean targetOpened;
    private static boolean targetClosed;
    private static long targetSequence;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("totem.observer.e2e.enabled")) return;
        role = System.getProperty("totem.observer.e2e.role", "").trim();
        ClientTickEvents.END_CLIENT_TICK.register(ObserverEnchantingE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested
                && markerExists("observer-native-anvil-closed.txt")
                && markerExists("target-native-anvil-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-enchanting.txt",
                    "Target may send enchanting semantic state through the dedicated server.\n");
        }

        if (observerRequested && !observerSeen
                && minecraft.gui.screen() != null
                && minecraft.gui.screen() instanceof EnchantmentScreen
                && minecraft.gui.screen() instanceof ObserverReadOnlyScreen
                && getBoolean(ENCHANTING, "remoteOpen")
                && ObserverE2eSequenceEvidence.accepted(
                        ObserverNativeScreenPayloads.FAMILY_ENCHANTING) > 0L
                && getLong(ENCHANTING, "extractedFrames") > 0L
                && ObserverE2eRenderBarrier.passed(ObserverNativeScreenPayloads.FAMILY_ENCHANTING,
                        getLong(ENCHANTING, "extractedFrames"))) {
            if (getInt(ENCHANTING, "remotePlayerLevel") != 30 || getInt(ENCHANTING, "remoteLapisCount") != 12) {
                fail("Enchanting E2E resource state mismatch");
                return;
            }
            @SuppressWarnings("unchecked")
            List<ObserverEnchantingScreenPayloads.OptionState> options =
                    (List<ObserverEnchantingScreenPayloads.OptionState>) getObject(ENCHANTING, "remoteOptions");
            if (options.size() != 3 || options.get(0).cost() != 5 || options.get(2).cost() != 30
                    || options.get(2).levelClue() != 4 || !options.get(2).affordable()) {
                fail("Enchanting E2E offer state mismatch");
                return;
            }
            EnchantmentScreen screen = (EnchantmentScreen) minecraft.gui.screen();
            if (!ObserverE2eMenuAssertions.hasNonEmptySlots(screen, 0, 1)) {
                fail("Enchanting production Menu did not apply relayed item and lapis slots");
                return;
            }
            if (getBoolean(GENERIC, "remoteContainerOpen")) {
                fail("Generic container relay competed with enchanting semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-enchanting-ok.txt",
                    "Observer received and rendered enchanting semantic state from the Target JVM.\n");
            saveScreenshot(minecraft, "observer-native-enchanting.png");
        }

        if (observerSeen && observerSaved && !observerClosed && !getBoolean(ENCHANTING, "remoteOpen")
                && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-enchanting-closed.txt",
                    "Enchanting semantic view closed after Target close state.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (!targetOpened && markerExists("observer-ready-for-enchanting.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(
                    ObserverNativeScreenPayloads.CAPABILITY_ENCHANTING)) {
                fail("Target did not negotiate enchanting semantic capability");
                return;
            }
            targetOpened = true;
            ClientPlayNetworking.send(openState());
            ObserverE2eCommon.marker("target-native-enchanting-state-sent.txt",
                    "Target sent enchanting semantic state through the dedicated server.\n");
        }
        if (targetOpened && !targetClosed && markerExists("observer-native-enchanting-saved.txt")) {
            targetClosed = true;
            ClientPlayNetworking.send(closeState());
            ObserverE2eCommon.marker("target-native-enchanting-close-sent.txt",
                    "Target sent enchanting semantic close state.\n");
        }
    }

    private static ObserverEnchantingScreenPayloads.EnchantingState openState() {
        return new ObserverEnchantingScreenPayloads.EnchantingState(
                ObserverEnchantingScreenPayloads.PROTOCOL_VERSION,
                ++targetSequence,
                true,
                ObserverNativeScreenPayloads.FAMILY_ENCHANTING,
                "net.minecraft.client.gui.screens.inventory.EnchantmentScreen",
                "Enchant",
                30,
                12,
                List.of(
                        new ObserverEnchantingScreenPayloads.OptionState(0, 5, 3, 1, true),
                        new ObserverEnchantingScreenPayloads.OptionState(1, 15, 7, 2, true),
                        new ObserverEnchantingScreenPayloads.OptionState(2, 30, 11, 4, true)
                ),
                List.of(
                        new ObserverNativeScreenPayloads.SlotState(0, 15, 47, "minecraft:diamond_sword", 1, 0),
                        new ObserverNativeScreenPayloads.SlotState(1, 35, 47, "minecraft:lapis_lazuli", 12, 0)
                )
        );
    }

    private static ObserverEnchantingScreenPayloads.EnchantingState closeState() {
        return new ObserverEnchantingScreenPayloads.EnchantingState(
                ObserverEnchantingScreenPayloads.PROTOCOL_VERSION,
                ++targetSequence,
                false,
                ObserverNativeScreenPayloads.FAMILY_ENCHANTING,
                "", "", 0, 0, List.of(), List.of()
        );
    }

    private static void saveScreenshot(Minecraft minecraft, String name) {
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            if (image == null) {
                fail("Enchanting screenshot callback returned null");
                return;
            }
            try (NativeImage owned = image) {
                Path output = ObserverE2eCommon.resultsDir().resolve(name);
                Files.createDirectories(output.getParent());
                owned.writeToFile(output);
                observerSaved = true;
                ObserverE2eCommon.marker("observer-native-enchanting-saved.txt",
                        "Enchanting semantic screenshot saved locally.\n");
            } catch (Exception error) {
                fail("Failed to save enchanting E2E screenshot: " + error);
            }
        });
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

    private static int getInt(Class<?> owner, String name) {
        try { return field(owner, name).getInt(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static long getLong(Class<?> owner, String name) {
        try { return field(owner, name).getLong(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static Object getObject(Class<?> owner, String name) {
        try { return field(owner, name).get(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static void setBoolean(Class<?> owner, String name, boolean value) {
        try { field(owner, name).setBoolean(null, value); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
}
