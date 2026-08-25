package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverAutomataCopperGolemScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.network.ObserverAutomataCopperGolemPayloads;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/** Client runtime proof for framebuffer-free TotemAutomata Copper Golem reconstruction. */
public final class ObserverAutomataCopperGolemClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();

            UUID targetId = UUID.randomUUID();
            var binding = new ObserverAutomataCopperGolemPayloads.BindingState(
                    "minecraft:overworld", 10, 64, -5, "minecraft:chest", "minecraft:chest",
                    true, true, true, ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED, 3, 1,
                    List.of("minecraft:iron_ingot"), List.of("minecraft:dirt"),
                    List.of("c:ingots"), List.of());
            var relay = new ObserverAutomataCopperGolemPayloads.CopperGolemRelay(
                    targetId,
                    ObserverAutomataCopperGolemPayloads.PROTOCOL_VERSION,
                    1L,
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

            context.runOnClient(minecraft -> {
                applySession(true, targetId, ObserverNativeScreenPayloads.CAPABILITY_AUTOMATA_COPPER_GOLEM);
                invoke(ObserverAutomataCopperGolemScreenClient.class, "acceptRelay",
                        new Class<?>[]{ObserverAutomataCopperGolemPayloads.CopperGolemRelay.class}, relay);
            });

            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("NativeAutomataCopperGolemMirrorScreen"), 100);
            context.waitFor(minecraft -> getLong("extractedFrames") > 0L, 100);
            if (!"sorting".equals(getString("remoteMode")) || !"bindings".equals(getString("remoteTab"))) {
                throw new AssertionError("Automata mode/tab state was not reconstructed");
            }
            if (!getBoolean("remoteApiKeyConfigured")) {
                throw new AssertionError("Automata configured-key semantic flag was not reconstructed");
            }
            if (!ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED.equals(getString("remoteApiUrl"))
                    || !ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED.equals(getString("remoteModel"))
                    || !ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED.equals(getString("remoteGatheringPrompt"))
                    || !ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED.equals(getString("remoteBindingPrompt"))
                    || !ObserverAutomataCopperGolemPayloads.TOKEN_VALID.equals(getString("remoteCacheValueText"))) {
                throw new AssertionError("Automata privacy-token state was not reconstructed");
            }
            assertNoRemoteApiKeyStringField();
            persistForCi(context.takeScreenshot("observer-ui-native-automata-copper-golem-screen"),
                    "observer-ui-native-automata-copper-golem-screen.png");

            var close = ObserverAutomataCopperGolemPayloads.relay(targetId, emptyState(2L));
            context.runOnClient(minecraft -> {
                invoke(ObserverAutomataCopperGolemScreenClient.class, "acceptRelay",
                        new Class<?>[]{ObserverAutomataCopperGolemPayloads.CopperGolemRelay.class}, close);
                applySession(false, new UUID(0L, 0L), 0L);
            });
            context.waitForScreen(null);
        }
    }

    private static ObserverAutomataCopperGolemPayloads.CopperGolemState emptyState(long sequence) {
        return new ObserverAutomataCopperGolemPayloads.CopperGolemState(
                ObserverAutomataCopperGolemPayloads.PROTOCOL_VERSION, sequence, false,
                ObserverNativeScreenPayloads.FAMILY_AUTOMATA_COPPER_GOLEM, "", "", false, "", "", "bindings",
                -1, 0, false, false, false, false, false, "", 0, 0, false, "", 0, 0, 0, "", 0,
                "", false, "", 0, "", "", "", null, null, List.of(), false, 0, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static void assertNoRemoteApiKeyStringField() {
        for (Field field : ObserverAutomataCopperGolemScreenClient.class.getDeclaredFields()) {
            if (field.getName().toLowerCase().contains("apikey") && field.getType() == String.class) {
                throw new AssertionError("Observer client stores API key text: " + field.getName());
            }
        }
    }

    private static void applySession(boolean active, UUID targetId, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession",
                new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "AutomataTarget" : "",
                        ObserverNativePayloads.PROTOCOL_VERSION, capabilities));
    }

    private static void invoke(Class<?> owner, String name, Class<?>[] types, Object... args) {
        try {
            Method method = owner.getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(null, args);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private static Field field(String name) {
        try {
            Field field = ObserverAutomataCopperGolemScreenClient.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private static long getLong(String name) {
        try { return field(name).getLong(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static boolean getBoolean(String name) {
        try { return field(name).getBoolean(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static String getString(String name) {
        try { return String.valueOf(field(name).get(null)); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static void persistForCi(Path screenshot, String fileName) {
        String workspace = System.getenv("GITHUB_WORKSPACE");
        if (workspace == null || workspace.isBlank()) return;
        try {
            Path dir = Path.of(workspace).resolve("build/client-gametest-screenshots");
            Files.createDirectories(dir);
            Files.copy(screenshot, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }
}
