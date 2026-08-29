package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverBeaconScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.network.ObserverBeaconScreenPayloads;
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

/** Client runtime proof for Beacon semantic reconstruction. */
public final class ObserverBeaconClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();
            UUID targetId = UUID.randomUUID();
            context.runOnClient(minecraft -> {
                applySession(true, targetId, ObserverBeaconScreenPayloads.CAPABILITY);
                accept(ObserverBeaconScreenPayloads.relay(targetId, openState(1L)));
            });
            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("ObserverBeaconScreen"), 100);
            context.waitFor(minecraft -> getLong("extractedFrames") > 0L, 100);
            if (getInt("remoteLevels") != 4
                    || !"minecraft:speed".equals(getString("remotePrimaryEffectId"))
                    || !"minecraft:regeneration".equals(getString("remoteSecondaryEffectId"))
                    || !getBoolean("remotePaymentPresent") || !getBoolean("remoteCanConfirm")
                    || getListSize("remoteSlots") != 37) {
                throw new AssertionError("Beacon semantic state was not reconstructed correctly");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-beacon-screen"),
                    "observer-ui-native-beacon-screen.png");
            context.runOnClient(minecraft -> {
                accept(ObserverBeaconScreenPayloads.relay(targetId, ObserverBeaconScreenPayloads.closed(2L)));
                applySession(false, new UUID(0L, 0L), 0L);
            });
            context.waitForScreen(null);
        }
    }

    private static ObserverBeaconScreenPayloads.BeaconState openState(long sequence) {
        return new ObserverBeaconScreenPayloads.BeaconState(
                ObserverBeaconScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverBeaconScreenPayloads.FAMILY_ID, ObserverBeaconScreenPayloads.SCREEN_CLASS,
                "Beacon", 4, "minecraft:speed", "minecraft:regeneration", true, true, slots());
    }

    private static List<ObserverNativeScreenPayloads.SlotState> slots() {
        List<ObserverNativeScreenPayloads.SlotState> result = new ArrayList<>();
        result.add(new ObserverNativeScreenPayloads.SlotState(0, 136, 110, "minecraft:emerald", 1, 0));
        int index = 1;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 36 + col * 18, 137 + row * 18, "", 0, 0));
        for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 36 + col * 18, 195, "", 0, 0));
        return List.copyOf(result);
    }

    private static void accept(ObserverBeaconScreenPayloads.BeaconRelay relay) {
        invoke(ObserverBeaconScreenClient.class, "acceptRelay",
                new Class<?>[]{ObserverBeaconScreenPayloads.BeaconRelay.class}, relay);
    }

    private static void applySession(boolean active, UUID targetId, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession", new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "BeaconTarget" : "",
                        ObserverNativePayloads.PROTOCOL_VERSION, capabilities));
    }

    private static void invoke(Class<?> owner, String name, Class<?>[] types, Object... args) {
        try { Method method = owner.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(null, args); }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    private static Field field(String name) {
        try { Field field = ObserverBeaconScreenClient.class.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }
    private static boolean getBoolean(String name) {
        try { return field(name).getBoolean(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }
    private static int getInt(String name) {
        try { return field(name).getInt(null); }
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
