package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.client.ObserverStonecutterScreenClient;
import dev.totem.vanillatweaks.client.ObserverUiClient;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStonecutterScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import dev.totem.vanillatweaks.observer.ObserverStonecutterRelayManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.StonecutterMenu;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Client runtime proof for Stonecutter semantic reconstruction. */
public final class ObserverStonecutterClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();
            verifyProductionSenderUsesMenuOrdinals(context);
            UUID targetId = UUID.randomUUID();
            context.runOnClient(minecraft -> {
                applySession(true, targetId, ObserverStonecutterScreenPayloads.CAPABILITY);
                accept(ObserverStonecutterScreenPayloads.relay(targetId, openState(1L)));
                applyMetadata(targetId, ObserverStonecutterScreenPayloads.SCREEN_CLASS, "Stonecutter");
                if (getBoolean(ObserverNativeScreenClient.class, "remoteGenericOpen")) {
                    throw new AssertionError("Stonecutter metadata competed with its negotiated semantic relay");
                }
            });
            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("ObserverStonecutterScreen"), 100);
            context.waitFor(minecraft -> getLong("extractedFrames") > 0L, 100);
            if (getInt("remoteSelectedRecipeIndex") != 2 || getInt("remoteRecipeCount") != 5
                    || getInt("remoteStartIndex") != 0 || getFloat("remoteScrollOffset") != 0.0F
                    || !getBoolean("remoteDisplayRecipes") || getListSize("remoteRecipes") != 5
                    || !getBoolean("remoteHasInputItem") || !getBoolean("remoteResultAvailable")
                    || getListSize("remoteSlots") != 38) {
                throw new AssertionError("Stonecutter semantic state was not reconstructed correctly");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-stonecutter-screen"),
                    "observer-ui-native-stonecutter-screen.png");
            context.runOnClient(minecraft -> {
                accept(ObserverStonecutterScreenPayloads.relay(targetId, ObserverStonecutterScreenPayloads.closed(2L)));
                applySession(false, new UUID(0L, 0L), 0L);
            });
            context.waitForScreen(null);
        }
    }

    private static void verifyProductionSenderUsesMenuOrdinals(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            StonecutterMenu menu = new StonecutterMenu(42, minecraft.player.getInventory());
            StonecutterScreen screen = new StonecutterScreen(
                    menu, minecraft.player.getInventory(), Component.literal("Stonecutter"));
            if (minecraft.player.containerMenu == menu) {
                throw new AssertionError("Stonecutter test accidentally reused player.containerMenu");
            }
            minecraft.setScreenAndShow(screen);
            if (minecraft.gui.screen() != screen || screen.getMenu() != menu) {
                throw new AssertionError("Stonecutter production test did not expose its Menu through the visible Screen");
            }
            menu.getSlot(0).set(new ItemStack(Items.STONE));
            menu.slotsChanged(menu.getSlot(0).container);
            ObserverStonecutterScreenPayloads.StonecutterState state =
                    (ObserverStonecutterScreenPayloads.StonecutterState) invokeResult(
                            ObserverStonecutterScreenClient.class, "captureTargetState",
                            new Class<?>[]{StonecutterScreen.class, long.class}, screen, 8L);
            assertOrdinalSlots(state.slots(), 38, "Stonecutter");
            if (!state.hasInputItem() || !state.displayRecipes() || state.recipes().isEmpty()
                    || state.recipes().size() != state.recipeCount()
                    || state.recipes().stream().anyMatch(recipe -> recipe.outputItemId().isBlank())) {
                throw new AssertionError("Real Stonecutter sender omitted bounded visible output recipes");
            }
            boolean valid = (boolean) invokeResult(ObserverStonecutterRelayManager.class, "valid",
                    new Class<?>[]{ObserverStonecutterScreenPayloads.StonecutterState.class}, state);
            if (!valid) throw new AssertionError("Real Stonecutter sender state failed server validation");
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

    private static ObserverStonecutterScreenPayloads.StonecutterState openState(long sequence) {
        return new ObserverStonecutterScreenPayloads.StonecutterState(
                ObserverStonecutterScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverStonecutterScreenPayloads.FAMILY_ID, ObserverStonecutterScreenPayloads.SCREEN_CLASS,
                "Stonecutter", 2, 5, 0, 0.0F, true, true, true, recipes(), slots());
    }

    private static List<ObserverStonecutterScreenPayloads.RecipeState> recipes() {
        return List.of(
                recipe(0, "stone"), recipe(1, "stone_bricks"), recipe(2, "stone_stairs"),
                recipe(3, "stone_slab"), recipe(4, "chiseled_stone_bricks"));
    }

    private static ObserverStonecutterScreenPayloads.RecipeState recipe(int index, String itemPath) {
        return new ObserverStonecutterScreenPayloads.RecipeState(
                index, "minecraft:" + itemPath, "minecraft:" + itemPath, 1, 0);
    }

    private static List<ObserverNativeScreenPayloads.SlotState> slots() {
        List<ObserverNativeScreenPayloads.SlotState> result = new ArrayList<>();
        result.add(new ObserverNativeScreenPayloads.SlotState(0, 20, 33, "minecraft:stone", 16, 0));
        result.add(new ObserverNativeScreenPayloads.SlotState(1, 143, 33, "minecraft:stone_bricks", 1, 0));
        int index = 2;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 84 + row * 18, "", 0, 0));
        for (int col = 0; col < 9; col++)
            result.add(new ObserverNativeScreenPayloads.SlotState(index++, 8 + col * 18, 142, "", 0, 0));
        return List.copyOf(result);
    }

    private static void accept(ObserverStonecutterScreenPayloads.StonecutterRelay relay) {
        invoke(ObserverStonecutterScreenClient.class, "acceptRelay",
                new Class<?>[]{ObserverStonecutterScreenPayloads.StonecutterRelay.class}, relay);
    }
    private static void applyMetadata(UUID targetId, String screenClass, String title) {
        invoke(ObserverUiClient.class, "applyScreenRelay", new Class<?>[]{ObserverPayloads.ScreenRelay.class},
                new ObserverPayloads.ScreenRelay(targetId, true, screenClass, title));
    }
    private static void applySession(boolean active, UUID targetId, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession", new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "StonecutterTarget" : "",
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
        try { Field field = ObserverStonecutterScreenClient.class.getDeclaredField(name); field.setAccessible(true); return field; }
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
