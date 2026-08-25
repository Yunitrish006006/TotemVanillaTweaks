package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverVillagersWoodcutterScreenClient;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverVillagersWoodcutterPayloads;
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

/** Client runtime proof for the TotemVillagers Woodcutter semantic mirror. */
public final class ObserverVillagersWoodcutterClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();

            UUID targetId = UUID.randomUUID();
            context.runOnClient(minecraft -> {
                applySession(true, targetId, ObserverVillagersWoodcutterPayloads.CAPABILITY);
                accept(ObserverVillagersWoodcutterPayloads.relay(targetId, openState(1L)));
            });

            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("NativeVillagersWoodcutterMirrorScreen"), 100);
            context.waitFor(minecraft -> getLong("extractedFrames") > 0L, 100);
            if (getInt("remoteSelectedRecipeIndex") != 1 || getInt("remoteRecipeCount") != 3
                    || getInt("remoteRequiredInputCount") != 2 || !getBoolean("remoteHasInputItem")
                    || getListSize("remoteSlots") != 38) {
                throw new AssertionError("Woodcutter semantic state was not reconstructed correctly");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-villagers-woodcutter-screen"),
                    "observer-ui-native-villagers-woodcutter-screen.png");

            context.runOnClient(minecraft -> {
                accept(ObserverVillagersWoodcutterPayloads.relay(targetId,
                        ObserverVillagersWoodcutterPayloads.closed(2L)));
                applySession(false, new UUID(0L, 0L), 0L);
            });
            context.waitForScreen(null);
        }
    }

    private static ObserverVillagersWoodcutterPayloads.WoodcutterState openState(long sequence) {
        return new ObserverVillagersWoodcutterPayloads.WoodcutterState(
                ObserverVillagersWoodcutterPayloads.PROTOCOL_VERSION,
                sequence,
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

    private static void accept(ObserverVillagersWoodcutterPayloads.WoodcutterRelay relay) {
        invoke(ObserverVillagersWoodcutterScreenClient.class, "acceptRelay",
                new Class<?>[]{ObserverVillagersWoodcutterPayloads.WoodcutterRelay.class}, relay);
    }

    private static void applySession(boolean active, UUID targetId, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession",
                new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "WoodcutterTarget" : "",
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
            Field field = ObserverVillagersWoodcutterScreenClient.class.getDeclaredField(name);
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

    private static int getListSize(String name) {
        try {
            Object value = field(name).get(null);
            return value instanceof List<?> list ? list.size() : -1;
        } catch (IllegalAccessException error) {
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
            throw new RuntimeException(error);
        }
    }
}
