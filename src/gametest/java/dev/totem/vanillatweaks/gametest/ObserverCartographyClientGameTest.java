package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverCartographyScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.network.ObserverCartographyScreenPayloads;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Client runtime proof for Cartography Table semantic reconstruction. */
public final class ObserverCartographyClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();
            UUID targetId = UUID.randomUUID();
            context.runOnClient(minecraft -> {
                applySession(true, targetId, ObserverCartographyScreenPayloads.CAPABILITY);
                accept(ObserverCartographyScreenPayloads.relay(targetId, openState(1L)));
            });
            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("ObserverCartographyScreen"), 100);
            context.waitFor(minecraft -> getLong("extractedFrames") > 0L, 100);
            if (!"scale".equals(getString("remoteOperation")) || !getBoolean("remoteMapPresent")
                    || !getBoolean("remoteAdditionalPresent") || !getBoolean("remoteResultAvailable")
                    || getListSize("remoteSlots") != 39) {
                throw new AssertionError("Cartography semantic state was not reconstructed correctly");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-cartography-screen"),
                    "observer-ui-native-cartography-screen.png");
            context.runOnClient(minecraft -> {
                accept(ObserverCartographyScreenPayloads.relay(targetId, ObserverCartographyScreenPayloads.closed(2L)));
                applySession(false, new UUID(0L, 0L), 0L);
            });
            context.waitForScreen(null);
        }
    }

    private static ObserverCartographyScreenPayloads.CartographyState openState(long sequence) {
        return new ObserverCartographyScreenPayloads.CartographyState(
                ObserverCartographyScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverCartographyScreenPayloads.FAMILY_ID, ObserverCartographyScreenPayloads.SCREEN_CLASS,
                "Cartography Table", "scale", true, true, true, slots());
    }

    private static List<ObserverNativeScreenPayloads.SlotState> slots() {
        List<ObserverNativeScreenPayloads.SlotState> result = new ArrayList<>();
        result.add(new ObserverNativeScreenPayloads.SlotState(0, 15, 15, "minecraft:filled_map", 1, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(1, 15, 52, "minecraft:paper", 1, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(2, 145, 39, "minecraft:filled_map", 1, 0));
        int index = 3;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 84 + row * 18, "", 0, 0));
        for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 142, "", 0, 0));
        return List.copyOf(result);
    }

    private static void accept(ObserverCartographyScreenPayloads.CartographyRelay relay) {
        invoke(ObserverCartographyScreenClient.class, "acceptRelay",
                new Class<?>[]{ObserverCartographyScreenPayloads.CartographyRelay.class}, relay);
    }

    private static void applySession(boolean active, UUID targetId, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession", new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "CartographyTarget" : "",
                        ObserverNativePayloads.PROTOCOL_VERSION, capabilities));
    }

    private static void invoke(Class<?> owner, String name, Class<?>[] types, Object... args) {
        try { Method method = owner.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(null, args); }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    private static Field field(String name) {
        try { Field field = ObserverCartographyScreenClient.class.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    private static boolean getBoolean(String name) {
        try { return field(name).getBoolean(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static long getLong(String name) {
        try { return field(name).getLong(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static String getString(String name) {
        try { return (String) field(name).get(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static int getListSize(String name) {
        try { Object value = field(name).get(null); return value instanceof List<?> list ? list.size() : -1; }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static void persistForCi(Path screenshot, String fileName) {
        String workspace = System.getenv("GITHUB_WORKSPACE");
        if (workspace == null || workspace.isBlank()) return;
        try {
            Path dir = Path.of(workspace).resolve("build/client-gametest-screenshots");
            Files.createDirectories(dir);
            Files.copy(screenshot, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) { throw new RuntimeException(error); }
    }
}
