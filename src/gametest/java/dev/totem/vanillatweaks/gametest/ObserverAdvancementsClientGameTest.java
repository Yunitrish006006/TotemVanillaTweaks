package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverAdvancementsScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.network.ObserverAdvancementsScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
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

/** Client runtime proof for vanilla Advancements semantic reconstruction. */
public final class ObserverAdvancementsClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();
            UUID targetId = UUID.randomUUID();
            context.runOnClient(minecraft -> {
                applySession(true, targetId, ObserverAdvancementsScreenPayloads.CAPABILITY);
                accept(ObserverAdvancementsScreenPayloads.relay(targetId, openState(1L)));
            });
            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("ObserverAdvancementsScreen"), 100);
            context.waitFor(minecraft -> getLong("extractedFrames") > 0L, 100);
            if (!"minecraft:story/root".equals(getString("remoteSelectedRootId"))
                    || getDouble("remoteScrollX") != -14.0D
                    || getDouble("remoteScrollY") != 9.0D
                    || getListSize("remoteTabs") != 2
                    || getListSize("remoteNodes") != 3) {
                throw new AssertionError("Advancements semantic state was not reconstructed correctly");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-advancements-screen"),
                    "observer-ui-native-advancements-screen.png");
            context.runOnClient(minecraft -> {
                accept(ObserverAdvancementsScreenPayloads.relay(targetId, ObserverAdvancementsScreenPayloads.closed(2L)));
                applySession(false, new UUID(0L, 0L), 0L);
            });
            context.waitForScreen(null);
        }
    }

    private static ObserverAdvancementsScreenPayloads.AdvancementsState openState(long sequence) {
        return new ObserverAdvancementsScreenPayloads.AdvancementsState(
                ObserverAdvancementsScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverAdvancementsScreenPayloads.FAMILY_ID, ObserverAdvancementsScreenPayloads.SCREEN_CLASS,
                "Advancements", "minecraft:story/root", -14.0D, 9.0D,
                List.of(
                        new ObserverAdvancementsScreenPayloads.TabState("minecraft:story/root", "Minecraft", "minecraft:grass_block",
                                "minecraft:gui/advancements/backgrounds/stone"),
                        new ObserverAdvancementsScreenPayloads.TabState("minecraft:adventure/root", "Adventure", "minecraft:map",
                                "minecraft:gui/advancements/backgrounds/adventure")
                ),
                List.of(
                        new ObserverAdvancementsScreenPayloads.NodeState(
                                "minecraft:story/root", "minecraft:story/root", "", "Minecraft", "The heart and story of the game",
                                "minecraft:grass_block", "task", 0.0F, 0.0F, 1.0F, true, false),
                        new ObserverAdvancementsScreenPayloads.NodeState(
                                "minecraft:story/mine_stone", "minecraft:story/root", "minecraft:story/root", "Stone Age", "Mine stone with your new pickaxe",
                                "minecraft:cobblestone", "task", 1.5F, 0.0F, 1.0F, true, false),
                        new ObserverAdvancementsScreenPayloads.NodeState(
                                "minecraft:story/upgrade_tools", "minecraft:story/root", "minecraft:story/mine_stone", "Getting an Upgrade", "Construct a better pickaxe",
                                "minecraft:stone_pickaxe", "task", 3.0F, 0.0F, 0.5F, false, false)
                ));
    }

    private static void accept(ObserverAdvancementsScreenPayloads.AdvancementsRelay relay) {
        invoke(ObserverAdvancementsScreenClient.class, "acceptRelay",
                new Class<?>[]{ObserverAdvancementsScreenPayloads.AdvancementsRelay.class}, relay);
    }

    private static void applySession(boolean active, UUID targetId, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession", new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "AdvancementTarget" : "",
                        ObserverNativePayloads.PROTOCOL_VERSION, capabilities));
    }

    private static void invoke(Class<?> owner, String name, Class<?>[] types, Object... args) {
        try { Method method = owner.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(null, args); }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    private static Field field(String name) {
        try { Field field = ObserverAdvancementsScreenClient.class.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }
    private static long getLong(String name) { try { return field(name).getLong(null); } catch (IllegalAccessException e) { throw new RuntimeException(e); } }
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
