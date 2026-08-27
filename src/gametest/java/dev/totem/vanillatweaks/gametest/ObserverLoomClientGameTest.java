package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverLoomScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.client.ObserverUiClient;
import dev.totem.vanillatweaks.network.ObserverLoomScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import dev.totem.vanillatweaks.observer.ObserverLoomRelayManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.LoomMenu;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Client runtime proof for Loom semantic reconstruction. */
public final class ObserverLoomClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();
            verifyProductionSenderUsesMenuOrdinals(context);
            UUID targetId = UUID.randomUUID();
            context.runOnClient(minecraft -> {
                applySession(true, targetId, ObserverLoomScreenPayloads.CAPABILITY);
                accept(ObserverLoomScreenPayloads.relay(targetId, openState(1L)));
                applyMetadata(targetId, ObserverLoomScreenPayloads.SCREEN_CLASS, "Loom");
                if (getBoolean(ObserverNativeScreenClient.class, "remoteGenericOpen")) {
                    throw new AssertionError("Loom metadata competed with its negotiated semantic relay");
                }
            });
            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("NativeLoomMirrorScreen"), 100);
            context.waitFor(minecraft -> getLong("extractedFrames") > 0L, 100);
            if (!getBoolean("remoteDisplayPatterns") || getBoolean("remoteHasMaxPatterns")
                    || !getBoolean("remoteResultAvailable")
                    || getInt("remoteSelectedPatternIndex") != 5
                    || getInt("remoteStartRow") != 1
                    || getFloat("remoteScrollOffset") != 1.0F
                    || getInt("remoteResultBaseColorId") != 0
                    || getListSize("remotePatterns") != 20
                    || getListSize("remoteResultLayers") != 1
                    || getListSize("remoteSlots") != 40) {
                throw new AssertionError("Loom semantic state was not reconstructed correctly");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-loom-screen"),
                    "observer-ui-native-loom-screen.png");
            context.runOnClient(minecraft -> {
                accept(ObserverLoomScreenPayloads.relay(targetId, ObserverLoomScreenPayloads.closed(2L)));
                applySession(false, new UUID(0L, 0L), 0L);
            });
            context.waitForScreen(null);
        }
    }

    private static void verifyProductionSenderUsesMenuOrdinals(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            LoomMenu menu = new LoomMenu(41, minecraft.player.getInventory());
            LoomScreen screen = new LoomScreen(menu, minecraft.player.getInventory(), Component.literal("Loom"));
            if (minecraft.player.containerMenu == menu) {
                throw new AssertionError("Loom test accidentally reused player.containerMenu");
            }
            minecraft.setScreenAndShow(screen);
            if (minecraft.gui.screen() != screen || screen.getMenu() != menu) {
                throw new AssertionError("Loom production test did not expose its Menu through the visible Screen");
            }
            menu.getSlot(0).set(new ItemStack(BuiltInRegistries.ITEM.getValue(
                    Identifier.parse("minecraft:white_banner"))));
            menu.getSlot(1).set(new ItemStack(BuiltInRegistries.ITEM.getValue(
                    Identifier.parse("minecraft:red_dye"))));
            menu.slotsChanged(menu.getSlot(0).container);
            if (menu.getSelectablePatterns().isEmpty()) {
                throw new AssertionError("Real Loom menu did not expose selectable banner patterns");
            }
            menu.clickMenuButton(minecraft.player, 0);
            ObserverLoomScreenPayloads.LoomState state = (ObserverLoomScreenPayloads.LoomState) invokeResult(
                    ObserverLoomScreenClient.class, "captureTargetState",
                    new Class<?>[]{LoomScreen.class, long.class}, screen, 7L);
            assertOrdinalSlots(state.slots(), 40, "Loom");
            if (state.patterns().isEmpty() || state.patterns().stream().anyMatch(pattern -> pattern.assetId().isBlank())) {
                throw new AssertionError("Real Loom sender omitted bounded banner pattern assets");
            }
            if (!state.resultAvailable() || state.resultBaseColorId() != 0 || state.resultLayers().isEmpty()) {
                throw new AssertionError("Real Loom sender omitted result banner layers/base color");
            }
            boolean valid = (boolean) invokeResult(ObserverLoomRelayManager.class, "valid",
                    new Class<?>[]{ObserverLoomScreenPayloads.LoomState.class}, state);
            if (!valid) throw new AssertionError("Real Loom sender state failed server validation");
            minecraft.setScreenAndShow(null);
        });
    }

    private static void assertOrdinalSlots(
            List<ObserverNativeScreenPayloads.SlotState> slots, int expected, String family) {
        if (slots.size() != expected) throw new AssertionError(family + " slot count " + slots.size());
        for (int i = 0; i < slots.size(); i++) if (slots.get(i).index() != i) {
            throw new AssertionError(family + " slot " + i + " has non-menu id " + slots.get(i).index());
        }
    }

    private static ObserverLoomScreenPayloads.LoomState openState(long sequence) {
        List<ObserverLoomScreenPayloads.PatternState> patterns = patternStates();
        return new ObserverLoomScreenPayloads.LoomState(
                ObserverLoomScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverLoomScreenPayloads.FAMILY_ID, ObserverLoomScreenPayloads.SCREEN_CLASS,
                "Loom", 5, 1, 1.0F, true, false, true, 0,
                patterns, List.of(new ObserverLoomScreenPayloads.BannerLayerState(
                        "minecraft:stripe_bottom", 14)), slots());
    }

    private static List<ObserverLoomScreenPayloads.PatternState> patternStates() {
        return List.of(
                pattern("base"), pattern("square_bottom_left"), pattern("square_bottom_right"),
                pattern("square_top_left"), pattern("square_top_right"), pattern("stripe_bottom"),
                pattern("stripe_top"), pattern("stripe_left"), pattern("stripe_right"),
                pattern("stripe_center"), pattern("stripe_middle"), pattern("stripe_downright"),
                pattern("stripe_downleft"), pattern("stripe_small"), pattern("cross"),
                pattern("straight_cross"), pattern("triangle_bottom"), pattern("triangle_top"),
                pattern("triangles_bottom"), pattern("triangles_top"));
    }

    private static ObserverLoomScreenPayloads.PatternState pattern(String path) {
        return new ObserverLoomScreenPayloads.PatternState("minecraft:" + path, "minecraft:" + path);
    }

    private static List<ObserverNativeScreenPayloads.SlotState> slots() {
        List<ObserverNativeScreenPayloads.SlotState> result = new ArrayList<>();
        result.add(new ObserverNativeScreenPayloads.SlotState(0, 13, 26, "minecraft:white_banner", 1, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(1, 33, 26, "minecraft:red_dye", 1, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(2, 23, 45, "", 0, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(3, 143, 57, "minecraft:white_banner", 1, 0));
        int index = 4;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 84 + row * 18, "", 0, 0));
        for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 142, "", 0, 0));
        return List.copyOf(result);
    }

    private static void accept(ObserverLoomScreenPayloads.LoomRelay relay) {
        invoke(ObserverLoomScreenClient.class, "acceptRelay",
                new Class<?>[]{ObserverLoomScreenPayloads.LoomRelay.class}, relay);
    }

    private static void applyMetadata(UUID targetId, String screenClass, String title) {
        invoke(ObserverUiClient.class, "applyScreenRelay", new Class<?>[]{ObserverPayloads.ScreenRelay.class},
                new ObserverPayloads.ScreenRelay(targetId, true, screenClass, title));
    }

    private static void applySession(boolean active, UUID targetId, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession", new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "LoomTarget" : "",
                        ObserverNativePayloads.PROTOCOL_VERSION, capabilities));
    }

    private static void invoke(Class<?> owner, String name, Class<?>[] types, Object... args) {
        try { Method method = owner.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(null, args); }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    private static Object invokeResult(Class<?> owner, String name, Class<?>[] types, Object... args) {
        try { Method method = owner.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(null, args); }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    private static Field field(String name) {
        try { Field field = ObserverLoomScreenClient.class.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    private static boolean getBoolean(String name) {
        try { return field(name).getBoolean(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static boolean getBoolean(Class<?> owner, String name) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.getBoolean(null); }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    private static int getInt(String name) {
        try { return field(name).getInt(null); }
        catch (IllegalAccessException error) { throw new RuntimeException(error); }
    }

    private static float getFloat(String name) {
        try { return field(name).getFloat(null); }
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
