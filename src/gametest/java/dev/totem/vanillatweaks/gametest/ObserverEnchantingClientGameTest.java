package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeEnchantingScreenClient;
import dev.totem.vanillatweaks.network.ObserverEnchantingScreenPayloads;
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

/** Client runtime proof for framebuffer-free enchanting semantic reconstruction. */
public final class ObserverEnchantingClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();

            UUID targetId = UUID.randomUUID();
            ObserverEnchantingScreenPayloads.EnchantingRelay open = new ObserverEnchantingScreenPayloads.EnchantingRelay(
                    targetId,
                    ObserverEnchantingScreenPayloads.PROTOCOL_VERSION,
                    1L,
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

            context.runOnClient(minecraft -> {
                applySession(true, targetId, ObserverNativeScreenPayloads.CAPABILITY_ENCHANTING);
                invoke(ObserverNativeEnchantingScreenClient.class, "acceptRelay",
                        new Class<?>[]{ObserverEnchantingScreenPayloads.EnchantingRelay.class}, open);
            });

            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("ObserverEnchantmentScreen"), 100);
            context.waitFor(minecraft -> getLong("extractedFrames") > 0L, 100);
            if (getInt("remotePlayerLevel") != 30 || getInt("remoteLapisCount") != 12) {
                throw new AssertionError("Enchanting resource state was not reconstructed");
            }
            @SuppressWarnings("unchecked")
            List<ObserverEnchantingScreenPayloads.OptionState> options =
                    (List<ObserverEnchantingScreenPayloads.OptionState>) getObject("remoteOptions");
            if (options.size() != 3 || options.get(2).cost() != 30 || options.get(2).levelClue() != 4) {
                throw new AssertionError("Enchanting offers were not reconstructed");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-enchanting-screen"),
                    "observer-ui-native-enchanting-screen.png");

            ObserverEnchantingScreenPayloads.EnchantingRelay close = new ObserverEnchantingScreenPayloads.EnchantingRelay(
                    targetId, ObserverEnchantingScreenPayloads.PROTOCOL_VERSION, 2L, false,
                    ObserverNativeScreenPayloads.FAMILY_ENCHANTING, "", "", 0, 0, List.of(), List.of());
            context.runOnClient(minecraft -> {
                invoke(ObserverNativeEnchantingScreenClient.class, "acceptRelay",
                        new Class<?>[]{ObserverEnchantingScreenPayloads.EnchantingRelay.class}, close);
                applySession(false, new UUID(0L, 0L), 0L);
            });
            context.waitForScreen(null);
        }
    }

    private static void applySession(boolean active, UUID targetId, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession",
                new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "EnchantingTarget" : "",
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
            Field field = ObserverNativeEnchantingScreenClient.class.getDeclaredField(name);
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

    private static Object getObject(String name) {
        try { return field(name).get(null); }
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
