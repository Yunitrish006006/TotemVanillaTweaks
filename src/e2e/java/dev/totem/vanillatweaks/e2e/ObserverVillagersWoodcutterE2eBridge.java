package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverVillagersWoodcutterPayloads;
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

/** Runs TotemVillagers Woodcutter semantics across the real three-JVM Observer path. */
public final class ObserverVillagersWoodcutterE2eBridge implements ClientModInitializer {
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
        ClientTickEvents.END_CLIENT_TICK.register(ObserverVillagersWoodcutterE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested
                && markerExists("observer-native-nexus-closed.txt")
                && markerExists("target-native-nexus-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-villagers-woodcutter.txt",
                    "Target may send TotemVillagers Woodcutter semantic state through the dedicated server.\n");
        }

        if (observerRequested && !observerSeen && mirrorVisible(minecraft)) {
            var screen = (dev.totem.villagers.client.WoodcutterScreen) minecraft.gui.screen();
            if (screen.getMenu().recipeCount() != 3 || screen.getMenu().requiredInputCount() != 3
                    || screen.getMenu().getItems().getFirst().getCount() != 3) {
                fail("Woodcutter production Screen did not apply the later snapshot");
                return;
            }
            if (getBoolean(GENERIC, "remoteGenericOpen")) {
                fail("Generic container relay competed with Woodcutter semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-villagers-woodcutter-ok.txt",
                    "Observer rendered TotemVillagers Woodcutter semantic state.\n");
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> saveScreenshot(image));
        }

        if (observerSaved && !observerClosed
                && !dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator.isActive(
                "villagers_woodcutter", "", 1)
                && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-villagers-woodcutter-closed.txt",
                    "Woodcutter semantic view closed after Target close state.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (targetStage == 0 && markerExists("observer-ready-for-villagers-woodcutter.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(
                    ObserverVillagersWoodcutterPayloads.CAPABILITY)) {
                fail("Target did not negotiate Woodcutter semantic capability");
                return;
            }
            targetStage = 1;
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.villagers(++targetSequence, 2));
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.villagers(++targetSequence, 3));
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.cursor(
                    "villagers_woodcutter", "", 1L));
            ObserverE2eCommon.marker("target-native-villagers-woodcutter-state-sent.txt",
                    "Target sent TotemVillagers Woodcutter semantic state.\n");
        } else if (targetStage == 1 && markerExists("observer-native-villagers-woodcutter-saved.txt")) {
            targetStage = 2;
            ClientPlayNetworking.send(ObserverOwnedE2eSnapshots.close(
                    "villagers_woodcutter", "", ++targetSequence));
            ObserverE2eCommon.marker("target-native-villagers-woodcutter-close-sent.txt",
                    "Target sent TotemVillagers Woodcutter semantic close state.\n");
        }
    }

    private static ObserverVillagersWoodcutterPayloads.WoodcutterState openState() {
        return new ObserverVillagersWoodcutterPayloads.WoodcutterState(
                ObserverVillagersWoodcutterPayloads.PROTOCOL_VERSION,
                ++targetSequence,
                true,
                ObserverVillagersWoodcutterPayloads.FAMILY_ID,
                ObserverVillagersWoodcutterPayloads.SCREEN_CLASS,
                "Woodcutter",
                1,
                3,
                2,
                true,
                slots()
        );
    }

    private static List<ObserverNativeScreenPayloads.SlotState> slots() {
        List<ObserverNativeScreenPayloads.SlotState> result = new ArrayList<>();
        result.add(new ObserverNativeScreenPayloads.SlotState(0, 20, 33, "minecraft:oak_log", 8, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(1, 143, 33, "minecraft:oak_planks", 4, 0));
        int index = 2;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                String item = index == 2 ? "minecraft:iron_axe" : "";
                result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 84 + row * 18,
                        item, item.isEmpty() ? 0 : 1, 0));
            }
        }
        for (int col = 0; col < 9; col++) {
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 142, "", 0, 0));
        }
        return List.copyOf(result);
    }

    private static boolean mirrorVisible(Minecraft minecraft) {
        return minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().equals("dev.totem.villagers.client.WoodcutterScreen")
                && minecraft.gui.screen() instanceof dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen
                && ObserverE2eSequenceEvidence.accepted(
                        ObserverVillagersWoodcutterPayloads.FAMILY_ID) > 0L
                && dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator.hasRemoteCursor()
                && dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator.hasRenderedActiveSnapshot();
    }

    private static void saveScreenshot(NativeImage image) {
        if (image == null) {
            fail("Woodcutter screenshot callback returned null");
            return;
        }
        try (NativeImage owned = image) {
            Path output = ObserverE2eCommon.resultsDir().resolve("observer-native-villagers-woodcutter.png");
            Files.createDirectories(output.getParent());
            owned.writeToFile(output);
            observerSaved = true;
            ObserverE2eCommon.marker("observer-native-villagers-woodcutter-saved.txt",
                    "Woodcutter semantic screenshot saved locally.\n");
        } catch (Exception error) {
            fail("Failed to save Woodcutter E2E screenshot: " + error);
        }
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

    private static int getListSize(Class<?> owner, String name) {
        try {
            Object value = field(owner, name).get(null);
            return value instanceof List<?> list ? list.size() : -1;
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static void setBoolean(Class<?> owner, String name, boolean value) {
        try { field(owner, name).setBoolean(null, value); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
}
