package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverNativeAnvilScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.network.ObserverAnvilScreenPayloads;
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

/** Client runtime proof for framebuffer-free anvil semantic reconstruction. */
public final class ObserverAnvilClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();

            UUID targetId = UUID.randomUUID();
            ObserverAnvilScreenPayloads.AnvilRelay open = new ObserverAnvilScreenPayloads.AnvilRelay(
                    targetId,
                    ObserverAnvilScreenPayloads.PROTOCOL_VERSION,
                    1L,
                    true,
                    ObserverNativeScreenPayloads.FAMILY_ANVIL,
                    "net.minecraft.client.gui.screens.inventory.AnvilScreen",
                    "Repair & Name",
                    "Observer Blade",
                    7,
                    false,
                    true,
                    List.of(
                            new ObserverNativeScreenPayloads.SlotState(0, 27, 47, "minecraft:diamond_sword", 1, 14),
                            new ObserverNativeScreenPayloads.SlotState(1, 76, 47, "minecraft:diamond", 2, 0),
                            new ObserverNativeScreenPayloads.SlotState(2, 134, 47, "minecraft:diamond_sword", 1, 0)
                    )
            );

            context.runOnClient(minecraft -> {
                applySession(true, targetId, ObserverNativeScreenPayloads.CAPABILITY_ANVIL);
                invoke(ObserverNativeAnvilScreenClient.class, "acceptRelay",
                        new Class<?>[]{ObserverAnvilScreenPayloads.AnvilRelay.class}, open);
            });

            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("ObserverAnvilScreen"), 100);
            context.waitFor(minecraft -> getStaticLong(ObserverNativeAnvilScreenClient.class, "extractedFrames") > 0L, 100);
            if (!"Observer Blade".equals(getStaticObject(ObserverNativeAnvilScreenClient.class, "remoteItemName"))) {
                throw new AssertionError("Anvil rename field was not reconstructed");
            }
            if (getStaticInt(ObserverNativeAnvilScreenClient.class, "remoteLevelCost") != 7) {
                throw new AssertionError("Anvil level cost was not reconstructed");
            }
            if (!getStaticBoolean(ObserverNativeAnvilScreenClient.class, "remoteResultAvailable")) {
                throw new AssertionError("Anvil result availability was not reconstructed");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-anvil-screen"),
                    "observer-ui-native-anvil-screen.png");

            ObserverAnvilScreenPayloads.AnvilRelay close = new ObserverAnvilScreenPayloads.AnvilRelay(
                    targetId,
                    ObserverAnvilScreenPayloads.PROTOCOL_VERSION,
                    2L,
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
            context.runOnClient(minecraft -> {
                invoke(ObserverNativeAnvilScreenClient.class, "acceptRelay",
                        new Class<?>[]{ObserverAnvilScreenPayloads.AnvilRelay.class}, close);
                applySession(false, new UUID(0L, 0L), 0L);
            });
            context.waitForScreen(null);
        }
    }

    private static void applySession(boolean active, UUID targetId, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession",
                new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "AnvilTarget" : "",
                        ObserverNativePayloads.PROTOCOL_VERSION, capabilities));
    }

    private static void invoke(Class<?> owner, String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            method.invoke(null, args);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Failed to invoke " + owner.getSimpleName() + "." + name, error);
        }
    }

    private static long getStaticLong(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.getLong(null);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private static int getStaticInt(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private static boolean getStaticBoolean(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.getBoolean(null);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private static Object getStaticObject(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private static void persistForCi(Path screenshot, String fileName) {
        String workspace = System.getenv("GITHUB_WORKSPACE");
        if (workspace == null || workspace.isBlank()) return;
        try {
            Path dir = Path.of(workspace).resolve("build/client-gametest-screenshots");
            Files.createDirectories(dir);
            Files.copy(screenshot, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            throw new RuntimeException("Failed to persist anvil screenshot", error);
        }
    }
}
