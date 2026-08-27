package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverNativeBookScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeCraftingScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.client.ObserverUiClient;
import dev.totem.vanillatweaks.mixin.client.AbstractRecipeBookScreenAccessor;
import dev.totem.vanillatweaks.mixin.client.RecipeBookComponentAccessor;
import dev.totem.vanillatweaks.network.ObserverBookScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverCraftingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import dev.totem.vanillatweaks.observer.ObserverNativeSessionManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/** Client smoke tests for framebuffer-free Observer semantic screen reconstruction. */
public final class ObserverUiClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();
            assertFramebufferSurfaceRemoved();
            verifyPlayerInventoryProductionSender(context);
            verifyUnnegotiatedMetadataFallback(context);
            verifyFurnaceSemanticMirror(context);
            verifyBookSemanticMirror(context);
            verifyCraftingSemanticMirror(context);
            assertFramebufferSurfaceRemoved();
        }
    }

    private static void verifyPlayerInventoryProductionSender(ClientGameTestContext context) {
        UUID targetId = UUID.randomUUID();
        AtomicReference<ObserverCraftingScreenPayloads.CraftingState> captured = new AtomicReference<>();
        context.runOnClient(minecraft -> {
            minecraft.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, true);
            minecraft.player.getRecipeBook().setFiltering(RecipeBookType.CRAFTING, true);
            minecraft.player.addEffect(new MobEffectInstance(MobEffects.SPEED, 1_200, 1));
            InventoryScreen screen = new InventoryScreen(minecraft.player);
            minecraft.setScreenAndShow(screen);
            if (minecraft.gui.screen() != screen || screen.getMenu() != minecraft.player.inventoryMenu) {
                throw new AssertionError("Player inventory production test is not using the visible InventoryScreen Menu");
            }
            var recipeBook = ((AbstractRecipeBookScreenAccessor) screen).totem$getRecipeBookComponent();
            if (!recipeBook.isVisible()) throw new AssertionError("Player recipe book did not open in the real screen");
            String privateDraft = "never-transmit-this-recipe-search-draft";
            ((RecipeBookComponentAccessor) recipeBook).totem$getSearchBox().setValue(privateDraft);
            screen.getMenu().getSlot(0).set(new ItemStack(Items.CRAFTING_TABLE));
            screen.getMenu().getSlot(1).set(new ItemStack(Items.OAK_PLANKS, 4));
            screen.getMenu().getSlot(5).set(new ItemStack(Items.DIAMOND_HELMET));
            screen.getMenu().getSlot(45).set(new ItemStack(Items.SHIELD));
            ObserverCraftingScreenPayloads.CraftingState state =
                    (ObserverCraftingScreenPayloads.CraftingState) invokeResult(
                            ObserverNativeCraftingScreenClient.class, "captureTargetState",
                            new Class<?>[]{Minecraft.class, Screen.class, long.class}, minecraft, screen, 9L);
            if (state == null
                    || !ObserverCraftingScreenPayloads.VARIANT_PLAYER_2X2.equals(state.variant())
                    || !InventoryScreen.class.getName().equals(state.screenClass())
                    || state.gridWidth() != 2 || state.gridHeight() != 2
                    || state.resultSlotIndex() != 0 || state.slots().size() != 46
                    || !state.recipeBookVisible() || !state.recipeBookFiltering()
                    || !state.recipeBookSearchActive() || state.selectedRecipeBookTab().isBlank()
                    || state.activeEffects().size() != 1
                    || !"minecraft:speed".equals(state.activeEffects().getFirst().effectId())
                    || !"minecraft:crafting_table".equals(state.slots().get(0).itemId())
                    || !"minecraft:diamond_helmet".equals(state.slots().get(5).itemId())
                    || !"minecraft:shield".equals(state.slots().get(45).itemId())) {
                throw new AssertionError("Player inventory production sender classified/extracted the wrong state");
            }
            if (state.toString().contains(privateDraft)) {
                throw new AssertionError("Recipe-book search draft leaked into Observer semantic state");
            }
            for (int i = 0; i < state.slots().size(); i++) if (state.slots().get(i).index() != i) {
                throw new AssertionError("Player inventory slot " + i + " has duplicate container id "
                        + state.slots().get(i).index());
            }
            boolean valid = (boolean) invokeResult(ObserverNativeSessionManager.class, "validCrafting",
                    new Class<?>[]{ObserverCraftingScreenPayloads.CraftingState.class}, state);
            if (!valid) throw new AssertionError("Real player inventory sender state failed server validation");
            captured.set(state);
            minecraft.setScreenAndShow(null);
            applySession(true, targetId, ObserverNativeScreenPayloads.CAPABILITY_CRAFTING);
            invoke(ObserverNativeCraftingScreenClient.class, "acceptRelay",
                    new Class<?>[]{ObserverCraftingScreenPayloads.CraftingRelay.class},
                    ObserverCraftingScreenPayloads.relay(targetId, state));
            invoke(ObserverUiClient.class, "applyScreenRelay",
                    new Class<?>[]{ObserverPayloads.ScreenRelay.class},
                    new ObserverPayloads.ScreenRelay(targetId, true, InventoryScreen.class.getName(), "Inventory"));
            if (getStaticBoolean(ObserverNativeScreenClient.class, "remoteGenericOpen")) {
                throw new AssertionError("Player inventory semantic sender competed with generic metadata");
            }
        });
        context.waitFor(minecraft -> minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("NativeCraftingMirrorScreen"), 100);
        context.waitFor(minecraft -> getStaticLong(
                ObserverNativeCraftingScreenClient.class, "extractedFrames") > 0L, 100);
        persistForCi(context.takeScreenshot("observer-ui-native-player-inventory-screen"),
                "observer-ui-native-player-inventory-screen.png");
        context.runOnClient(minecraft -> {
            var state = captured.get();
            invoke(ObserverNativeCraftingScreenClient.class, "acceptRelay",
                    new Class<?>[]{ObserverCraftingScreenPayloads.CraftingRelay.class},
                    ObserverCraftingScreenPayloads.relay(targetId,
                            ObserverCraftingScreenPayloads.closed(state.sequence() + 1)));
            applySession(false, new UUID(0L, 0L), 0L);
            minecraft.player.removeEffect(MobEffects.SPEED);
        });
        context.waitForScreen(null);
    }

    private static void verifyUnnegotiatedMetadataFallback(ClientGameTestContext context) {
        context.setScreen(() -> new Screen(Component.literal("Observer Metadata Target")) {
            @Override public boolean isPauseScreen() { return false; }
        });
        context.waitFor(mc -> mc.gui.screen() != null
                && "Observer Metadata Target".equals(mc.gui.screen().getTitle().getString()));
        persistForCi(context.takeScreenshot("observer-ui-source-screen"), "observer-ui-source-screen.png");
        context.setScreen(() -> null);
        context.waitForScreen(null);

        UUID targetId = UUID.randomUUID();
        context.runOnClient(minecraft -> {
            applySession(true, targetId, 0L);
            invoke(ObserverNativeScreenClient.class, "applyGenericScreenState",
                    new Class<?>[]{boolean.class, String.class, String.class}, true,
                    "net.minecraft.client.gui.screens.inventory.InventoryScreen", "Unnegotiated Container Metadata");
        });
        context.waitFor(minecraft -> minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("NativeGenericMirrorScreen"), 100);
        context.waitTicks(2);
        persistForCi(context.takeScreenshot("observer-ui-native-generic-screen"),
                "observer-ui-native-generic-screen.png");
        context.runOnClient(minecraft -> {
            invoke(ObserverNativeScreenClient.class, "applyGenericScreenState",
                    new Class<?>[]{boolean.class, String.class, String.class}, false, "", "");
            applySession(false, new UUID(0L, 0L), 0L);
        });
        context.waitForScreen(null);
    }

    private static void verifyFurnaceSemanticMirror(ClientGameTestContext context) {
        UUID targetId = UUID.randomUUID();
        ObserverNativeScreenPayloads.FurnaceRelay open = new ObserverNativeScreenPayloads.FurnaceRelay(
                targetId, ObserverNativeScreenPayloads.FURNACE_PROTOCOL_VERSION, 1L, true,
                ObserverNativeScreenPayloads.FAMILY_FURNACE,
                "net.minecraft.client.gui.screens.inventory.FurnaceScreen", "Observer Furnace Test",
                176, 166, 88, 42,
                List.of(
                        new ObserverNativeScreenPayloads.SlotState(0, 56, 17, "minecraft:iron_ore", 3, 0),
                        new ObserverNativeScreenPayloads.SlotState(1, 56, 53, "minecraft:coal", 2, 0),
                        new ObserverNativeScreenPayloads.SlotState(2, 116, 35, "minecraft:iron_ingot", 1, 0)),
                0.625F, 0.5F, true);
        context.runOnClient(minecraft -> {
            applySession(true, targetId, ObserverNativeScreenPayloads.CAPABILITY_FURNACE);
            invoke(ObserverNativeScreenClient.class, "acceptFurnaceRelay",
                    new Class<?>[]{ObserverNativeScreenPayloads.FurnaceRelay.class}, open);
        });
        context.waitFor(minecraft -> minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("NativeFurnaceMirrorScreen"), 100);
        context.waitFor(minecraft -> getStaticLong(ObserverNativeScreenClient.class, "furnaceExtractedFrames") > 0L, 100);
        persistForCi(context.takeScreenshot("observer-ui-native-furnace-screen"),
                "observer-ui-native-furnace-screen.png");

        ObserverNativeScreenPayloads.FurnaceRelay close = new ObserverNativeScreenPayloads.FurnaceRelay(
                targetId, ObserverNativeScreenPayloads.FURNACE_PROTOCOL_VERSION, 2L, false,
                ObserverNativeScreenPayloads.FAMILY_FURNACE, "", "", 0, 0, 0, 0, List.of(), 0.0F, 0.0F, false);
        context.runOnClient(minecraft -> {
            invoke(ObserverNativeScreenClient.class, "acceptFurnaceRelay",
                    new Class<?>[]{ObserverNativeScreenPayloads.FurnaceRelay.class}, close);
            applySession(false, new UUID(0L, 0L), 0L);
        });
        context.waitForScreen(null);
    }

    private static void verifyBookSemanticMirror(ClientGameTestContext context) {
        UUID targetId = UUID.randomUUID();
        ObserverBookScreenPayloads.BookRelay open = new ObserverBookScreenPayloads.BookRelay(
                targetId, ObserverBookScreenPayloads.PROTOCOL_VERSION, 1L, true,
                ObserverNativeScreenPayloads.FAMILY_BOOK, ObserverBookScreenPayloads.VARIANT_WRITTEN,
                "net.minecraft.client.gui.screens.inventory.BookViewScreen", "Observer Book Test",
                1, 3, "The Observer should reconstruct this book page from semantic state, not framebuffer pixels.",
                "", "");
        context.runOnClient(minecraft -> {
            applySession(true, targetId, ObserverNativeScreenPayloads.CAPABILITY_BOOK);
            invoke(ObserverNativeBookScreenClient.class, "acceptRelay",
                    new Class<?>[]{ObserverBookScreenPayloads.BookRelay.class}, open);
        });
        context.waitFor(minecraft -> minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("NativeBookMirrorScreen"), 100);
        context.waitFor(minecraft -> getStaticLong(ObserverNativeBookScreenClient.class, "extractedFrames") > 0L, 100);
        persistForCi(context.takeScreenshot("observer-ui-native-book-screen"), "observer-ui-native-book-screen.png");

        ObserverBookScreenPayloads.BookRelay close = new ObserverBookScreenPayloads.BookRelay(
                targetId, ObserverBookScreenPayloads.PROTOCOL_VERSION, 2L, false,
                ObserverNativeScreenPayloads.FAMILY_BOOK, "", "", "", 0, 0, "", "", "");
        context.runOnClient(minecraft -> {
            invoke(ObserverNativeBookScreenClient.class, "acceptRelay",
                    new Class<?>[]{ObserverBookScreenPayloads.BookRelay.class}, close);
            applySession(false, new UUID(0L, 0L), 0L);
        });
        context.waitForScreen(null);
    }

    private static void verifyCraftingSemanticMirror(ClientGameTestContext context) {
        UUID targetId = UUID.randomUUID();
        ObserverCraftingScreenPayloads.CraftingRelay open = new ObserverCraftingScreenPayloads.CraftingRelay(
                targetId, ObserverCraftingScreenPayloads.PROTOCOL_VERSION, 1L, true,
                ObserverNativeScreenPayloads.FAMILY_CRAFTING,
                ObserverCraftingScreenPayloads.VARIANT_TABLE_3X3,
                "net.minecraft.client.gui.screens.inventory.CraftingScreen", "Observer Crafting Test",
                176, 166, 90, 45, 3, 3, 0,
                false, false, false, false, "", 0, 0, false, List.of(),
                List.of(
                        new ObserverNativeScreenPayloads.SlotState(0, 124, 35, "minecraft:crafting_table", 1, 0),
                        new ObserverNativeScreenPayloads.SlotState(1, 30, 17, "minecraft:oak_planks", 1, 0),
                        new ObserverNativeScreenPayloads.SlotState(2, 48, 17, "minecraft:oak_planks", 1, 0),
                        new ObserverNativeScreenPayloads.SlotState(3, 66, 17, "minecraft:oak_planks", 1, 0),
                        new ObserverNativeScreenPayloads.SlotState(4, 30, 35, "minecraft:oak_planks", 1, 0)));
        context.runOnClient(minecraft -> {
            applySession(true, targetId, ObserverNativeScreenPayloads.CAPABILITY_CRAFTING);
            invoke(ObserverNativeCraftingScreenClient.class, "acceptRelay",
                    new Class<?>[]{ObserverCraftingScreenPayloads.CraftingRelay.class}, open);
        });
        context.waitFor(minecraft -> minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass().getName().contains("NativeCraftingMirrorScreen"), 100);
        context.waitFor(minecraft -> getStaticLong(ObserverNativeCraftingScreenClient.class, "extractedFrames") > 0L, 100);
        persistForCi(context.takeScreenshot("observer-ui-native-crafting-screen"),
                "observer-ui-native-crafting-screen.png");

        ObserverCraftingScreenPayloads.CraftingRelay close = new ObserverCraftingScreenPayloads.CraftingRelay(
                targetId, ObserverCraftingScreenPayloads.PROTOCOL_VERSION, 2L, false,
                ObserverNativeScreenPayloads.FAMILY_CRAFTING, "", "", "", 0, 0, 0, 0, 0, 0, 0,
                false, false, false, false, "", 0, 0, false, List.of(), List.of());
        context.runOnClient(minecraft -> {
            invoke(ObserverNativeCraftingScreenClient.class, "acceptRelay",
                    new Class<?>[]{ObserverCraftingScreenPayloads.CraftingRelay.class}, close);
            applySession(false, new UUID(0L, 0L), 0L);
        });
        context.waitForScreen(null);
    }

    private static void applySession(boolean active, UUID targetId, long screenCapabilities) {
        invoke(ObserverNativeClient.class, "applySession", new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "ObserverSmokeTarget" : "",
                        ObserverNativePayloads.PROTOCOL_VERSION, screenCapabilities));
    }

    private static void assertFramebufferSurfaceRemoved() {
        Set<String> payloadTypes = Arrays.stream(ObserverPayloads.class.getDeclaredClasses())
                .map(Class::getSimpleName).collect(Collectors.toSet());
        if (!payloadTypes.equals(Set.of("ScreenState", "ScreenRelay", "Stop"))) {
            throw new AssertionError("Unexpected Observer compatibility payload surface: " + payloadTypes);
        }
        assertNoField(ObserverUiClient.class, "captureEnabled");
        assertNoField(ObserverUiClient.class, "nextFrameId");
        assertNoField(ObserverUiClient.class, "lastFrameId");
        assertNoField(ObserverUiClient.class, "textureRegistered");
    }

    private static void assertNoField(Class<?> owner, String name) {
        try {
            owner.getDeclaredField(name);
            throw new AssertionError("Removed framebuffer field still exists: " + owner.getSimpleName() + "." + name);
        } catch (NoSuchFieldException expected) {
            // Required absence.
        }
    }

    private static long getStaticLong(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.getLong(null);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Failed to read " + owner.getSimpleName() + "." + name, error);
        }
    }

    private static boolean getStaticBoolean(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.getBoolean(null);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Failed to read " + owner.getSimpleName() + "." + name, error);
        }
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

    private static Object invokeResult(Class<?> owner, String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Failed to invoke " + owner.getSimpleName() + "." + name, error);
        }
    }

    private static void persistForCi(Path screenshot, String fileName) {
        String workspace = System.getenv("GITHUB_WORKSPACE");
        if (workspace == null || workspace.isBlank()) return;
        try {
            Path destinationDir = Path.of(workspace).resolve("build/client-gametest-screenshots");
            Files.createDirectories(destinationDir);
            Files.copy(screenshot, destinationDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            throw new RuntimeException("Failed to persist client gametest screenshot " + screenshot, error);
        }
    }
}
