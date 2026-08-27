package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverNativeAnvilScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverAnvilScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Inserts a real cross-JVM anvil semantic relay phase after the merchant proof and before Stop. */
public final class ObserverAnvilE2eBridge implements ClientModInitializer {
    private static final Class<?> ANVIL = ObserverNativeAnvilScreenClient.class;
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
        ClientTickEvents.END_CLIENT_TICK.register(ObserverAnvilE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested
                && markerExists("observer-native-merchant-closed.txt")
                && markerExists("target-native-merchant-close-sent.txt")) {
            // Keep the legacy driver at the Stop boundary until the anvil family proof completes.
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-anvil.txt",
                    "Target may send anvil semantic state through the dedicated server.\n");
        }

        if (observerRequested && !observerSeen
                && minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("NativeAnvilMirrorScreen")
                && getBoolean(ANVIL, "remoteOpen")
                && ObserverE2eSequenceEvidence.accepted(ObserverNativeScreenPayloads.FAMILY_ANVIL) > 0L
                && getLong(ANVIL, "extractedFrames") > 0L) {
            if (!"E2E Blade".equals(String.valueOf(getObject(ANVIL, "remoteItemName")))) {
                fail("Anvil E2E rename mismatch");
                return;
            }
            if (getInt(ANVIL, "remoteLevelCost") != 7) {
                fail("Anvil E2E level cost mismatch");
                return;
            }
            if (!getBoolean(ANVIL, "remoteResultAvailable") || getBoolean(ANVIL, "remoteTooExpensive")) {
                fail("Anvil E2E result state mismatch");
                return;
            }
            if (getBoolean(GENERIC, "remoteContainerOpen")) {
                fail("Generic container relay competed with anvil semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-anvil-ok.txt",
                    "Observer received and rendered anvil semantic state from the Target JVM.\n");
            saveScreenshot(minecraft, "observer-native-anvil.png");
        }

        if (observerSeen && observerSaved && !observerClosed && !getBoolean(ANVIL, "remoteOpen")
                && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-anvil-closed.txt",
                    "Anvil semantic mirror closed after Target close state.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (!targetOpened && markerExists("observer-ready-for-anvil.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(
                    ObserverNativeScreenPayloads.CAPABILITY_ANVIL)) {
                fail("Target did not negotiate anvil semantic capability");
                return;
            }
            targetOpened = true;
            ClientPlayNetworking.send(openState());
            ObserverE2eCommon.marker("target-native-anvil-state-sent.txt",
                    "Target sent anvil semantic state through the dedicated server.\n");
        }
        if (targetOpened && !targetClosed && markerExists("observer-native-anvil-saved.txt")) {
            targetClosed = true;
            ClientPlayNetworking.send(closeState());
            ObserverE2eCommon.marker("target-native-anvil-close-sent.txt",
                    "Target sent anvil semantic close state.\n");
        }
    }

    private static ObserverAnvilScreenPayloads.AnvilState openState() {
        return new ObserverAnvilScreenPayloads.AnvilState(
                ObserverAnvilScreenPayloads.PROTOCOL_VERSION,
                ++targetSequence,
                true,
                ObserverNativeScreenPayloads.FAMILY_ANVIL,
                "net.minecraft.client.gui.screens.inventory.AnvilScreen",
                "Repair & Name",
                "E2E Blade",
                7,
                false,
                true,
                List.of(
                        new ObserverNativeScreenPayloads.SlotState(0, 27, 47, "minecraft:diamond_sword", 1, 14),
                        new ObserverNativeScreenPayloads.SlotState(1, 76, 47, "minecraft:diamond", 2, 0),
                        new ObserverNativeScreenPayloads.SlotState(2, 134, 47, "minecraft:diamond_sword", 1, 0)
                )
        );
    }

    private static ObserverAnvilScreenPayloads.AnvilState closeState() {
        return new ObserverAnvilScreenPayloads.AnvilState(
                ObserverAnvilScreenPayloads.PROTOCOL_VERSION,
                ++targetSequence,
                false,
                ObserverNativeScreenPayloads.FAMILY_ANVIL,
                "",
                "",
                "",
                0,
                false,
                false,
                List.of()
        );
    }

    private static void saveScreenshot(Minecraft minecraft, String name) {
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            if (image == null) {
                fail("Anvil screenshot callback returned null");
                return;
            }
            try (NativeImage owned = image) {
                Path output = ObserverE2eCommon.resultsDir().resolve(name);
                Files.createDirectories(output.getParent());
                owned.writeToFile(output);
                observerSaved = true;
                ObserverE2eCommon.marker("observer-native-anvil-saved.txt",
                        "Anvil semantic screenshot saved locally.\n");
            } catch (Exception error) {
                fail("Failed to save anvil E2E screenshot: " + error);
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
