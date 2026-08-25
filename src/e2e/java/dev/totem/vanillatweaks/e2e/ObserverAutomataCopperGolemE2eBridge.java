package dev.totem.vanillatweaks.e2e;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverAutomataCopperGolemScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverAutomataCopperGolemPayloads;
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

/** Inserts a real cross-JVM Automata Copper Golem semantic phase after Remnant Backpack and before Stop. */
public final class ObserverAutomataCopperGolemE2eBridge implements ClientModInitializer {
    private static final Class<?> AUTOMATA = ObserverAutomataCopperGolemScreenClient.class;
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
        ClientTickEvents.END_CLIENT_TICK.register(ObserverAutomataCopperGolemE2eBridge::tick);
    }

    private static void tick(Minecraft minecraft) {
        if ("observer".equals(role)) tickObserver(minecraft);
        else if ("target".equals(role)) tickTarget();
    }

    private static void tickObserver(Minecraft minecraft) {
        if (!observerRequested
                && markerExists("observer-native-remnant-backpack-closed.txt")
                && markerExists("target-native-remnant-backpack-close-sent.txt")) {
            setBoolean(DRIVER, "observerStopRequested", true);
            observerRequested = true;
            ObserverE2eCommon.marker("observer-ready-for-automata-copper-golem.txt",
                    "Target may send Automata Copper Golem semantic state through the dedicated server.\n");
        }

        if (observerRequested && !observerSeen
                && minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("NativeAutomataCopperGolemMirrorScreen")
                && getBoolean(AUTOMATA, "remoteOpen")
                && getLong(AUTOMATA, "lastRemoteSequence") > 0L
                && getLong(AUTOMATA, "extractedFrames") > 0L) {
            if (!"sorting".equals(String.valueOf(getObject(AUTOMATA, "remoteMode")))
                    || !"bindings".equals(String.valueOf(getObject(AUTOMATA, "remoteTab")))) {
                fail("Automata Copper Golem E2E mode/tab mismatch");
                return;
            }
            if (!getBoolean(AUTOMATA, "remoteApiKeyConfigured")) {
                fail("Automata Copper Golem configured-key semantic flag missing");
                return;
            }
            if (!ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED.equals(getObject(AUTOMATA, "remoteApiUrl"))
                    || !ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED.equals(getObject(AUTOMATA, "remoteModel"))
                    || !ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED.equals(getObject(AUTOMATA, "remoteGatheringPrompt"))
                    || !ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED.equals(getObject(AUTOMATA, "remoteBindingPrompt"))
                    || !ObserverAutomataCopperGolemPayloads.TOKEN_VALID.equals(getObject(AUTOMATA, "remoteCacheValueText"))) {
                fail("Automata Copper Golem privacy-token semantic state mismatch");
                return;
            }
            if (hasStringApiKeyField()) {
                fail("Observer Automata client retained API-key text field");
                return;
            }
            if (getBoolean(GENERIC, "remoteContainerOpen")) {
                fail("Generic container relay competed with Automata Copper Golem semantic relay");
                return;
            }
            observerSeen = true;
            ObserverE2eCommon.marker("observer-native-automata-copper-golem-ok.txt",
                    "Observer received and rendered Automata Copper Golem semantic state without API-key text.\n");
            saveScreenshot(minecraft, "observer-native-automata-copper-golem.png");
        }

        if (observerSeen && observerSaved && !observerClosed && !getBoolean(AUTOMATA, "remoteOpen")
                && minecraft.gui.screen() == null) {
            observerClosed = true;
            ObserverE2eCommon.marker("observer-native-automata-copper-golem-closed.txt",
                    "Automata Copper Golem semantic mirror closed after Target close state.\n");
            setBoolean(DRIVER, "observerStopRequested", false);
        }
    }

    private static void tickTarget() {
        if (!targetOpened && markerExists("observer-ready-for-automata-copper-golem.txt")) {
            if (!dev.totem.vanillatweaks.client.ObserverNativeClient.targetSupportsScreen(
                    ObserverNativeScreenPayloads.CAPABILITY_AUTOMATA_COPPER_GOLEM)) {
                fail("Target did not negotiate Automata Copper Golem semantic capability");
                return;
            }
            targetOpened = true;
            ClientPlayNetworking.send(openState());
            ObserverE2eCommon.marker("target-native-automata-copper-golem-state-sent.txt",
                    "Target sent Automata Copper Golem semantic state through the dedicated server.\n");
        }
        if (targetOpened && !targetClosed && markerExists("observer-native-automata-copper-golem-saved.txt")) {
            targetClosed = true;
            ClientPlayNetworking.send(closeState());
            ObserverE2eCommon.marker("target-native-automata-copper-golem-close-sent.txt",
                    "Target sent Automata Copper Golem semantic close state.\n");
        }
    }

    private static ObserverAutomataCopperGolemPayloads.CopperGolemState openState() {
        var binding = new ObserverAutomataCopperGolemPayloads.BindingState(
                "minecraft:overworld", 10, 64, -5, "minecraft:chest", "minecraft:chest",
                true, true, true, ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED, 3, 1,
                List.of("minecraft:iron_ingot"), List.of("minecraft:dirt"), List.of("c:ingots"), List.of());
        return new ObserverAutomataCopperGolemPayloads.CopperGolemState(
                ObserverAutomataCopperGolemPayloads.PROTOCOL_VERSION,
                ++targetSequence,
                true,
                ObserverNativeScreenPayloads.FAMILY_AUTOMATA_COPPER_GOLEM,
                "dev.totem.automata.client.CopperGolemMenuScreen",
                "Copper Golem",
                true,
                "sorting",
                "sorting",
                "bindings",
                0,
                0,
                true,
                false,
                false,
                false,
                false,
                "minecraft:coal",
                8,
                1200,
                false,
                "minecraft:iron_pickaxe",
                1,
                12,
                250,
                "minecraft:chest",
                1,
                ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED,
                true,
                ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED,
                1,
                ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED,
                ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED,
                ObserverAutomataCopperGolemPayloads.TOKEN_VALID,
                binding,
                new ObserverAutomataCopperGolemPayloads.GatheringAreaState(
                        "minecraft:overworld", true, 0, 60, 0, true, 8, 70, 8),
                List.of("minecraft:stone", "minecraft:coal_ore"),
                true,
                2,
                1,
                List.of("minecraft:stone"),
                List.of("minecraft:bedrock"),
                List.of("c:ores"),
                List.of(),
                List.of(binding),
                List.of(
                        new ObserverNativeScreenPayloads.SlotState(0, 8, 99, "minecraft:coal", 8, 0),
                        new ObserverNativeScreenPayloads.SlotState(1, 119, 71, "minecraft:iron_pickaxe", 1, 12),
                        new ObserverNativeScreenPayloads.SlotState(2, 137, 71, "minecraft:chest", 1, 0)
                ));
    }

    private static ObserverAutomataCopperGolemPayloads.CopperGolemState closeState() {
        return new ObserverAutomataCopperGolemPayloads.CopperGolemState(
                ObserverAutomataCopperGolemPayloads.PROTOCOL_VERSION, ++targetSequence, false,
                ObserverNativeScreenPayloads.FAMILY_AUTOMATA_COPPER_GOLEM, "", "", false, "", "", "bindings",
                -1, 0, false, false, false, false, false, "", 0, 0, false, "", 0, 0, 0, "", 0,
                "", false, "", 0, "", "", "", null, null, List.of(), false, 0, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static boolean hasStringApiKeyField() {
        for (Field field : AUTOMATA.getDeclaredFields()) {
            if (field.getName().toLowerCase().contains("apikey") && field.getType() == String.class) return true;
        }
        return false;
    }

    private static void saveScreenshot(Minecraft minecraft, String name) {
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            if (image == null) {
                fail("Automata Copper Golem screenshot callback returned null");
                return;
            }
            try (NativeImage owned = image) {
                Path output = ObserverE2eCommon.resultsDir().resolve(name);
                Files.createDirectories(output.getParent());
                owned.writeToFile(output);
                observerSaved = true;
                ObserverE2eCommon.marker("observer-native-automata-copper-golem-saved.txt",
                        "Automata Copper Golem semantic screenshot saved locally.\n");
            } catch (Exception error) {
                fail("Failed to save Automata Copper Golem E2E screenshot: " + error);
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
