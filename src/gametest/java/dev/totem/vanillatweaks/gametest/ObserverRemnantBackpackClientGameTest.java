package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverRemnantBackpackScreenClient;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverRemnantBackpackPayloads;
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

/** Client runtime proof for framebuffer-free TotemRemnant backpack reconstruction. */
public final class ObserverRemnantBackpackClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();

            UUID targetId = UUID.randomUUID();
            ObserverRemnantBackpackPayloads.BackpackRelay open = new ObserverRemnantBackpackPayloads.BackpackRelay(
                    targetId,
                    ObserverRemnantBackpackPayloads.PROTOCOL_VERSION,
                    1L,
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
                    List.of(
                            new ObserverNativeScreenPayloads.SlotState(18, 8, 18, "minecraft:diamond", 12, 0),
                            new ObserverNativeScreenPayloads.SlotState(19, 26, 18, "minecraft:iron_ingot", 32, 0),
                            new ObserverNativeScreenPayloads.SlotState(72, 8, 139, "minecraft:bread", 8, 0),
                            new ObserverNativeScreenPayloads.SlotState(108, 192, 17, "minecraft:nether_star", 1, 0),
                            new ObserverNativeScreenPayloads.SlotState(112, 257, 76, "minecraft:crafting_table", 1, 0),
                            new ObserverNativeScreenPayloads.SlotState(113, 184, 58, "minecraft:oak_planks", 4, 0)
                    )
            );

            context.runOnClient(minecraft -> {
                applySession(true, targetId, ObserverNativeScreenPayloads.CAPABILITY_REMNANT_BACKPACK);
                invoke(ObserverRemnantBackpackScreenClient.class, "acceptRelay",
                        new Class<?>[]{ObserverRemnantBackpackPayloads.BackpackRelay.class}, open);
            });

            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("NativeRemnantBackpackMirrorScreen"), 100);
            context.waitFor(minecraft -> getLong("extractedFrames") > 0L, 100);
            if (getInt("remoteRowCount") != 8 || getInt("remoteVisibleRows") != 6
                    || getInt("remoteFirstVisibleRow") != 2 || getInt("remoteUpgradeSlotCount") != 4) {
                throw new AssertionError("Backpack rows, viewport, or upgrade state was not reconstructed");
            }
            if (!getBoolean("remoteCraftingEnabled") || !getBoolean("remoteEnderAccessVisible")) {
                throw new AssertionError("Backpack upgrade semantics were not reconstructed");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-remnant-backpack-screen"),
                    "observer-ui-native-remnant-backpack-screen.png");

            ObserverRemnantBackpackPayloads.BackpackRelay close = new ObserverRemnantBackpackPayloads.BackpackRelay(
                    targetId, ObserverRemnantBackpackPayloads.PROTOCOL_VERSION, 2L, false,
                    ObserverNativeScreenPayloads.FAMILY_REMNANT_BACKPACK, "", "", 0, 0, 0, 0,
                    false, false, List.of());
            context.runOnClient(minecraft -> {
                invoke(ObserverRemnantBackpackScreenClient.class, "acceptRelay",
                        new Class<?>[]{ObserverRemnantBackpackPayloads.BackpackRelay.class}, close);
                applySession(false, new UUID(0L, 0L), 0L);
            });
            context.waitForScreen(null);
        }
    }

    private static void applySession(boolean active, UUID targetId, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession",
                new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "BackpackTarget" : "",
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
            Field field = ObserverRemnantBackpackScreenClient.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private static int getInt(String name) {
        try { return field(name).getInt(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static long getLong(String name) {
        try { return field(name).getLong(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static boolean getBoolean(String name) {
        try { return field(name).getBoolean(null); }
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
