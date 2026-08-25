package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverStatsScreenClient;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverStatsScreenPayloads;
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

/** Client runtime proof for vanilla Statistics semantic reconstruction. */
public final class ObserverStatsClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();
            UUID targetId = UUID.randomUUID();
            context.runOnClient(minecraft -> {
                applySession(true, targetId, ObserverStatsScreenPayloads.CAPABILITY);
                accept(ObserverStatsScreenPayloads.relay(targetId, openState(1L)));
            });
            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("NativeStatsMirrorScreen"), 100);
            context.waitFor(minecraft -> getLong("extractedFrames") > 0L, 100);
            if (!"items".equals(getString("remoteActiveTab"))
                    || getDouble("remoteScrollAmount") != 20.0D
                    || !"used".equals(getString("remoteItemSortColumn"))
                    || getInt("remoteItemSortOrder") != -1
                    || getListSize("remoteItemRows") != 3
                    || getListSize("remoteGeneralRows") != 0
                    || getListSize("remoteMobRows") != 0) {
                throw new AssertionError("Statistics semantic state was not reconstructed correctly");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-stats-screen"),
                    "observer-ui-native-stats-screen.png");
            context.runOnClient(minecraft -> {
                accept(ObserverStatsScreenPayloads.relay(targetId, ObserverStatsScreenPayloads.closed(2L)));
                applySession(false, new UUID(0L, 0L), 0L);
            });
            context.waitForScreen(null);
        }
    }

    private static ObserverStatsScreenPayloads.StatsState openState(long sequence) {
        return new ObserverStatsScreenPayloads.StatsState(
                ObserverStatsScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverStatsScreenPayloads.FAMILY_ID, ObserverStatsScreenPayloads.SCREEN_CLASS,
                "Statistics", "items", false, 20.0D, "used", -1,
                List.of(),
                List.of(
                        new ObserverStatsScreenPayloads.ItemRow("minecraft:stone_pickaxe", 0, 4, 1, 125, 2, 0),
                        new ObserverStatsScreenPayloads.ItemRow("minecraft:torch", 0, 0, 64, 48, 10, 1),
                        new ObserverStatsScreenPayloads.ItemRow("minecraft:oak_log", 37, 0, 0, 12, 20, 3)
                ),
                List.of());
    }

    private static void accept(ObserverStatsScreenPayloads.StatsRelay relay) {
        invoke(ObserverStatsScreenClient.class, "acceptRelay",
                new Class<?>[]{ObserverStatsScreenPayloads.StatsRelay.class}, relay);
    }

    private static void applySession(boolean active, UUID targetId, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession", new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "StatsTarget" : "",
                        ObserverNativePayloads.PROTOCOL_VERSION, capabilities));
    }

    private static void invoke(Class<?> owner, String name, Class<?>[] types, Object... args) {
        try { Method method = owner.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(null, args); }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    private static Field field(String name) {
        try { Field field = ObserverStatsScreenClient.class.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }
    private static long getLong(String name) { try { return field(name).getLong(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static int getInt(String name) { try { return field(name).getInt(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static double getDouble(String name) { try { return field(name).getDouble(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static String getString(String name) { try { return (String) field(name).get(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
    private static int getListSize(String name) { try { Object value = field(name).get(null); return value instanceof List<?> list ? list.size() : -1; } catch (IllegalAccessException e) { throw new RuntimeException(e); } }

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
