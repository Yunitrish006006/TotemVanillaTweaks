package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverNativeMerchantScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverMerchantScreenPayloads;
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

/** Inserts a real cross-JVM merchant semantic relay phase before the existing E2E Stop phase. */
public final class ObserverMerchantE2eBridge implements ClientModInitializer {
    private static final Class<?> MERCHANT = ObserverNativeMerchantScreenClient.class;
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
        ClientTickEvents.END_CLIENT_TICK.register(ObserverMerchantE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested
                && markerExists("observer-native-generic-screen-saved.txt")
                && markerExists("target-native-generic-closed.txt")) {
            // Hold the legacy driver at its Stop boundary until this semantic-family proof finishes.
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-merchant.txt",
                    "Target may send merchant semantic state through the dedicated server.\n");
        }

        if (observerRequested && !observerSeen
                && minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("NativeMerchantMirrorScreen")
                && getBoolean(MERCHANT, "remoteOpen")
                && getLong(MERCHANT, "lastRemoteSequence") > 0L
                && getLong(MERCHANT, "extractedFrames") > 0L) {
            if (!"vanilla_merchant".equals(String.valueOf(getObject(MERCHANT, "remoteVariant")))) {
                fail("Merchant E2E variant mismatch");
                return;
            }
            if (getInt(MERCHANT, "remoteSelectedOffer") != 1) {
                fail("Merchant E2E selected offer mismatch");
                return;
            }
            if (getBoolean(GENERIC, "remoteContainerOpen")) {
                fail("Generic container relay competed with merchant semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-merchant-ok.txt",
                    "Observer received and rendered merchant semantic state from the Target JVM.\n");
            saveScreenshot(minecraft, "observer-native-merchant.png");
        }

        if (observerSeen && observerSaved && !observerClosed && !getBoolean(MERCHANT, "remoteOpen")
                && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-merchant-closed.txt",
                    "Merchant semantic mirror closed after Target close state.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (!targetOpened && markerExists("observer-ready-for-merchant.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(
                    ObserverNativeScreenPayloads.CAPABILITY_MERCHANT)) {
                fail("Target did not negotiate merchant semantic capability");
                return;
            }
            targetOpened = true;
            ClientPlayNetworking.send(openState());
            ObserverE2eCommon.marker("target-native-merchant-state-sent.txt",
                    "Target sent merchant semantic state through the dedicated server.\n");
        }
        if (targetOpened && !targetClosed && markerExists("observer-native-merchant-saved.txt")) {
            targetClosed = true;
            ClientPlayNetworking.send(closeState());
            ObserverE2eCommon.marker("target-native-merchant-close-sent.txt",
                    "Target sent merchant semantic close state.\n");
        }
    }

    private static ObserverMerchantScreenPayloads.MerchantState openState() {
        ObserverMerchantScreenPayloads.ItemState emerald = item("minecraft:emerald", 5);
        ObserverMerchantScreenPayloads.ItemState book = item("minecraft:book", 1);
        ObserverMerchantScreenPayloads.ItemState enchantedBook = item("minecraft:enchanted_book", 1);
        ObserverMerchantScreenPayloads.ItemState carrot = item("minecraft:carrot", 22);
        ObserverMerchantScreenPayloads.ItemState empty = item("", 0);
        return new ObserverMerchantScreenPayloads.MerchantState(
                ObserverMerchantScreenPayloads.PROTOCOL_VERSION, ++targetSequence, true,
                ObserverNativeScreenPayloads.FAMILY_MERCHANT, ObserverMerchantScreenPayloads.VARIANT_VANILLA,
                "net.minecraft.client.gui.screens.inventory.MerchantScreen", "E2E Merchant", 1,
                3, 72, 150, true, true,
                List.of(
                        new ObserverMerchantScreenPayloads.OfferState(0, carrot, empty, emerald, 2, 16, 2, false),
                        new ObserverMerchantScreenPayloads.OfferState(1, emerald, book, enchantedBook, 12, 12, 5, true)
                )
        );
    }

    private static ObserverMerchantScreenPayloads.MerchantState closeState() {
        return new ObserverMerchantScreenPayloads.MerchantState(
                ObserverMerchantScreenPayloads.PROTOCOL_VERSION, ++targetSequence, false,
                ObserverNativeScreenPayloads.FAMILY_MERCHANT, "", "", "", 0,
                0, 0, 0, false, false, List.of()
        );
    }

    private static ObserverMerchantScreenPayloads.ItemState item(String id, int count) {
        return new ObserverMerchantScreenPayloads.ItemState(id, count, 0);
    }

    private static void saveScreenshot(Minecraft minecraft, String name) {
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            if (image == null) {
                fail("Merchant screenshot callback returned null");
                return;
            }
            try (NativeImage owned = image) {
                Path output = ObserverE2eCommon.resultsDir().resolve(name);
                Files.createDirectories(output.getParent());
                owned.writeToFile(output);
                observerSaved = true;
                ObserverE2eCommon.marker("observer-native-merchant-saved.txt",
                        "Merchant semantic screenshot saved locally.\n");
            } catch (Exception error) {
                fail("Failed to save merchant E2E screenshot: " + error);
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
