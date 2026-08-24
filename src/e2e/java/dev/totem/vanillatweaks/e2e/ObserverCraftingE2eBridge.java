package dev.totem.vanillatweaks.e2e;

import dev.totem.vanillatweaks.client.ObserverNativeCraftingScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.lang.reflect.Field;
import java.nio.file.Files;

/**
 * Upgrades the existing three-JVM Inventory proof to the specialized crafting family without
 * duplicating the rest of the world/HUD/generic/Stop lifecycle driver.
 */
public final class ObserverCraftingE2eBridge implements ClientModInitializer {
    private static final Class<?> CRAFTING = ObserverNativeCraftingScreenClient.class;
    private static final Class<?> GENERIC_CONTAINER = ObserverNativeScreenClient.class;
    private static final Class<?> DRIVER = ObserverE2eClient.class;

    private static String role;
    private static boolean targetProofWritten;
    private static boolean observerProofWritten;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("totem.observer.e2e.enabled")) {
            return;
        }
        role = System.getProperty("totem.observer.e2e.role", "").trim();
        ClientTickEvents.END_CLIENT_TICK.register(ObserverCraftingE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("target".equals(role)) {
            tickTarget(minecraft);
        } else if ("observer".equals(role)) {
            tickObserver(minecraft);
        }
    }

    private static void tickTarget(Minecraft minecraft) {
        if (targetProofWritten || !(minecraft.gui.screen() instanceof InventoryScreen)) {
            return;
        }
        if (!getBoolean(CRAFTING, "targetOpen") || getLong(CRAFTING, "nextTargetSequence") <= 0L) {
            return;
        }
        if (getBoolean(GENERIC_CONTAINER, "targetContainerOpen")) {
            fail("Generic container sender remained active while player_2x2 crafting semantics were negotiated");
            return;
        }
        targetProofWritten = true;
        ObserverE2eCommon.marker(
                "target-native-container-state-sent.txt",
                "Target sent player_2x2 crafting semantic state for real InventoryScreen; generic container sender stayed closed.\n"
        );
    }

    private static void tickObserver(Minecraft minecraft) {
        if (observerProofWritten || minecraft.gui.screen() == null
                || !minecraft.gui.screen().getClass().getName().contains("NativeCraftingMirrorScreen")) {
            return;
        }
        if (!getBoolean(CRAFTING, "remoteOpen")
                || getLong(CRAFTING, "lastRemoteSequence") <= 0L
                || getLong(CRAFTING, "extractedFrames") <= 0L) {
            return;
        }
        String variant = String.valueOf(getObject(CRAFTING, "remoteVariant"));
        int gridWidth = getInt(CRAFTING, "remoteGridWidth");
        int gridHeight = getInt(CRAFTING, "remoteGridHeight");
        if (!"player_2x2".equals(variant) || gridWidth != 2 || gridHeight != 2) {
            fail("Unexpected Inventory crafting semantic state: variant=" + variant
                    + " grid=" + gridWidth + "x" + gridHeight);
            return;
        }
        if (getBoolean(GENERIC_CONTAINER, "remoteContainerOpen")) {
            fail("Observer received competing generic container state while rendering player_2x2 crafting semantics");
            return;
        }

        setBoolean(DRIVER, "observerContainerSeen", true);
        observerProofWritten = true;
        ObserverE2eCommon.marker(
                "observer-native-container-ok.txt",
                "Observer rendered player_2x2 Inventory crafting semantics locally with no competing generic container mirror.\n"
        );
    }

    private static void fail(String message) {
        if (!Files.isRegularFile(ObserverE2eCommon.resultsDir().resolve("failure-" + role + ".txt"))) {
            ObserverE2eCommon.fail(role, message);
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Missing E2E field " + owner.getSimpleName() + "." + name, error);
        }
    }

    private static boolean getBoolean(Class<?> owner, String name) {
        try {
            return field(owner, name).getBoolean(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static int getInt(Class<?> owner, String name) {
        try {
            return field(owner, name).getInt(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static long getLong(Class<?> owner, String name) {
        try {
            return field(owner, name).getLong(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static Object getObject(Class<?> owner, String name) {
        try {
            return field(owner, name).get(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static void setBoolean(Class<?> owner, String name, boolean value) {
        try {
            field(owner, name).setBoolean(null, value);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }
}
