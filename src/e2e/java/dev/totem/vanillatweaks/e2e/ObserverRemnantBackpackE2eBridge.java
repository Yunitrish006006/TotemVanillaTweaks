package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverRemnantBackpackPayloads;
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

/** Inserts a real cross-JVM Remnant backpack semantic relay phase after enchanting and before Stop. */
public final class ObserverRemnantBackpackE2eBridge implements ClientModInitializer {
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
        ClientTickEvents.END_CLIENT_TICK.register(ObserverRemnantBackpackE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested
                && markerExists("observer-native-enchanting-closed.txt")
                && markerExists("target-native-enchanting-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-remnant-backpack.txt",
                    "Target may send Remnant backpack semantic state through the dedicated server.\n");
        }

        if (observerRequested && !observerSeen
                && minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().equals("dev.totem.remnant.client.screen.BackpackScreen")
                && minecraft.gui.screen() instanceof dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen
                && ObserverE2eSequenceEvidence.accepted(
                        ObserverNativeScreenPayloads.FAMILY_REMNANT_BACKPACK) > 0L
                && dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator.hasRemoteCursor()
                && dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator.hasRenderedActiveSnapshot()) {
            var screen = (dev.totem.remnant.client.screen.BackpackScreen) minecraft.gui.screen();
            if (screen.getMenu().getRowCount() != 8 || screen.getMenu().upgradeSlotCount() != 4
                    || screen.getMenu().getItems().get(18).getCount() != 13
                    || screen.getMenu().getCarried().getCount() != 5
                    || screen.getMenu().getCarried().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME) == null
                    || !"Remote Cursor Diamond".equals(screen.getMenu().getCarried()
                    .get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString())) {
                fail("Remnant backpack production Screen did not apply the later snapshot");
                return;
            }
            if (getBoolean(GENERIC, "remoteContainerOpen")) {
                fail("Generic container relay competed with Remnant backpack semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-remnant-backpack-ok.txt",
                    "Observer received and rendered Remnant backpack semantic state from the Target JVM.\n");
            saveScreenshot(minecraft, "observer-native-remnant-backpack.png");
        }

        if (observerSeen && observerSaved && !observerClosed
                && !dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator.isActive(
                "remnant_backpack", "", 1)
                && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-remnant-backpack-closed.txt",
                    "Remnant backpack semantic view closed after Target close state.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (!targetOpened && markerExists("observer-ready-for-remnant-backpack.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(
                    ObserverNativeScreenPayloads.CAPABILITY_REMNANT_BACKPACK)) {
                fail("Target did not negotiate Remnant backpack semantic capability");
                return;
            }
            targetOpened = true;
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.remnant(++targetSequence, 12));
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.remnant(++targetSequence, 13));
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.namedCursor("remnant_backpack", "", 1L));
            ObserverE2eCommon.marker("target-native-remnant-backpack-state-sent.txt",
                    "Target sent Remnant backpack semantic state through the dedicated server.\n");
        }
        if (targetOpened && !targetClosed && markerExists("observer-native-remnant-backpack-saved.txt")) {
            targetClosed = true;
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.close("remnant_backpack", "", ++targetSequence));
            ObserverE2eCommon.marker("target-native-remnant-backpack-close-sent.txt",
                    "Target sent Remnant backpack semantic close state.\n");
        }
    }

    private static ObserverRemnantBackpackPayloads.BackpackState openState() {
        return new ObserverRemnantBackpackPayloads.BackpackState(
                ObserverRemnantBackpackPayloads.PROTOCOL_VERSION,
                ++targetSequence,
                true,
                ObserverNativeScreenPayloads.FAMILY_REMNANT_BACKPACK,
                "dev.totem.remnant.client.screen.BackpackScreen",
                "Netherite Backpack",
                8,
                6,
                2,
                4,
                true,
                true,
                backpackSlots()
        );
    }

    private static List<ObserverNativeScreenPayloads.SlotState> backpackSlots() {
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>(122);

        // 8 storage rows. With firstVisibleRow=2, the first two rows are just above the viewport.
        for (int index = 0; index < 72; index++) {
            int row = index / 9;
            int column = index % 9;
            String itemId = "";
            int count = 0;
            if (index == 18) { itemId = "minecraft:diamond"; count = 12; }
            else if (index == 19) { itemId = "minecraft:iron_ingot"; count = 32; }
            slots.add(new ObserverNativeScreenPayloads.SlotState(
                    index, 8 + column * 18, 18 + (row - 2) * 18, itemId, count, 0));
        }

        // Player inventory: 27 main slots and 9 hotbar slots.
        for (int playerIndex = 0; playerIndex < 36; playerIndex++) {
            int index = 72 + playerIndex;
            int x;
            int y;
            if (playerIndex < 27) {
                x = 8 + playerIndex % 9 * 18;
                y = 6 * 18 + 31 + playerIndex / 9 * 18;
            } else {
                x = 8 + (playerIndex - 27) * 18;
                y = 6 * 18 + 89;
            }
            String itemId = playerIndex == 0 ? "minecraft:bread" : "";
            int count = playerIndex == 0 ? 8 : 0;
            slots.add(new ObserverNativeScreenPayloads.SlotState(index, x, y, itemId, count, 0));
        }

        // Four upgrade slots.
        int upgradeStartX = 177 + (102 - 4 * 18) / 2;
        for (int upgradeIndex = 0; upgradeIndex < 4; upgradeIndex++) {
            slots.add(new ObserverNativeScreenPayloads.SlotState(
                    108 + upgradeIndex,
                    upgradeStartX + upgradeIndex * 18,
                    17,
                    upgradeIndex == 0 ? "minecraft:nether_star" : "",
                    upgradeIndex == 0 ? 1 : 0,
                    0));
        }

        // Embedded crafting result followed by the 3x3 grid.
        slots.add(new ObserverNativeScreenPayloads.SlotState(
                112, 257, 76, "minecraft:crafting_table", 1, 0));
        for (int craftingIndex = 0; craftingIndex < 9; craftingIndex++) {
            int row = craftingIndex / 3;
            int column = craftingIndex % 3;
            slots.add(new ObserverNativeScreenPayloads.SlotState(
                    113 + craftingIndex,
                    184 + column * 18,
                    58 + row * 18,
                    craftingIndex == 0 ? "minecraft:oak_planks" : "",
                    craftingIndex == 0 ? 4 : 0,
                    0));
        }
        return List.copyOf(slots);
    }

    private static ObserverRemnantBackpackPayloads.BackpackState closeState() {
        return new ObserverRemnantBackpackPayloads.BackpackState(
                ObserverRemnantBackpackPayloads.PROTOCOL_VERSION,
                ++targetSequence,
                false,
                ObserverNativeScreenPayloads.FAMILY_REMNANT_BACKPACK,
                "", "", 0, 0, 0, 0, false, false, List.of()
        );
    }

    private static void saveScreenshot(Minecraft minecraft, String name) {
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            if (image == null) {
                fail("Remnant backpack screenshot callback returned null");
                return;
            }
            try (NativeImage owned = image) {
                Path output = ObserverE2eCommon.resultsDir().resolve(name);
                Files.createDirectories(output.getParent());
                owned.writeToFile(output);
                observerSaved = true;
                ObserverE2eCommon.marker("observer-native-remnant-backpack-saved.txt",
                        "Remnant backpack semantic screenshot saved locally.\n");
            } catch (Exception error) {
                fail("Failed to save Remnant backpack E2E screenshot: " + error);
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

    private static void setBoolean(Class<?> owner, String name, boolean value) {
        try { field(owner, name).setBoolean(null, value); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
}
