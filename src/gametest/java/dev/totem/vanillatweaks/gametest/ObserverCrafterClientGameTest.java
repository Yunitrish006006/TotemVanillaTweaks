package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverCrafterScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.network.ObserverCrafterScreenPayloads;
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

/** Client runtime proof for Crafter semantic reconstruction. */
public final class ObserverCrafterClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();
            UUID targetId = UUID.randomUUID();
            context.runOnClient(minecraft -> {
                applySession(true, targetId, ObserverCrafterScreenPayloads.CAPABILITY);
                accept(ObserverCrafterScreenPayloads.relay(targetId, openState(1L)));
            });
            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("ObserverCrafterScreen"), 100);
            context.waitFor(minecraft -> getLong("extractedFrames") > 0L, 100);
            if (!getBoolean("remotePowered") || getInt("remoteDisabledMask") != ((1 << 1) | (1 << 7))
                    || getInt("remoteOccupiedInputSlots") != 3 || getListSize("remoteSlots") != 46) {
                throw new AssertionError("Crafter semantic state was not reconstructed correctly");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-crafter-screen"),
                    "observer-ui-native-crafter-screen.png");
            context.runOnClient(minecraft -> {
                accept(ObserverCrafterScreenPayloads.relay(targetId, ObserverCrafterScreenPayloads.closed(2L)));
                applySession(false, new UUID(0L, 0L), 0L);
            });
            context.waitForScreen(null);
        }
    }

    private static ObserverCrafterScreenPayloads.CrafterState openState(long sequence) {
        return new ObserverCrafterScreenPayloads.CrafterState(
                ObserverCrafterScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverCrafterScreenPayloads.FAMILY_ID, ObserverCrafterScreenPayloads.SCREEN_CLASS,
                "Crafter", true, (1 << 1) | (1 << 7), 3, slots());
    }

    private static List<ObserverNativeScreenPayloads.SlotState> slots() {
        List<ObserverNativeScreenPayloads.SlotState> result = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                String item = switch (index) {
                    case 0 -> "minecraft:iron_ingot";
                    case 4 -> "minecraft:redstone";
                    case 8 -> "minecraft:crafting_table";
                    default -> "";
                };
                result.add(new ObserverNativeScreenPayloads.SlotState(index, 26 + col * 18, 17 + row * 18,
                        item, item.isEmpty() ? 0 : 1, 0));
            }
        }
        int index = 9;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 84 + row * 18, "", 0, 0));
        for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 142, "", 0, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(index, 134, 35,
                "minecraft:comparator", 1, 0));
        return List.copyOf(result);
    }

    private static void accept(ObserverCrafterScreenPayloads.CrafterRelay relay) {
        invoke(ObserverCrafterScreenClient.class, "acceptRelay",
                new Class<?>[]{ObserverCrafterScreenPayloads.CrafterRelay.class}, relay);
    }

    private static void applySession(boolean active, UUID targetId, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession", new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "CrafterTarget" : "",
                        ObserverNativePayloads.PROTOCOL_VERSION, capabilities));
    }

    private static void invoke(Class<?> owner, String name, Class<?>[] types, Object... args) {
        try { Method method = owner.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(null, args); }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    private static Field field(String name) {
        try { Field field = ObserverCrafterScreenClient.class.getDeclaredField(name); field.setAccessible(true); return field; }
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
