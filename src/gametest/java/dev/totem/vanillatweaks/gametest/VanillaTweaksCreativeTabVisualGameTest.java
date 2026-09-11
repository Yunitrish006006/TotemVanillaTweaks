package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.content.VanillaTweaksContent;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Captures and verifies VanillaTweaks's module-owned Creative tab. */
@SuppressWarnings("UnstableApiUsage")
public final class VanillaTweaksCreativeTabVisualGameTest implements FabricClientGameTest {
    private static Object creativeScreen;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            singleplayer.getServer().runCommand("gamemode creative @a");
            context.waitFor(VanillaTweaksCreativeTabVisualGameTest::hasCreativeAbilities);
            for (String locale : List.of("en_us", "zh_tw")) {
                java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>> reload =
                        new java.util.concurrent.atomic.AtomicReference<>();
                context.runOnClient(client -> {
                    client.options.languageCode = locale;
                    client.getLanguageManager().setSelected(locale);
                    reload.set(client.reloadResourcePacks());
                });
                context.waitFor(client -> reload.get() != null && reload.get().isDone());
                context.runOnClient(VanillaTweaksCreativeTabVisualGameTest::openCreativeScreen);
                context.waitTicks(20);
                context.runOnClient(VanillaTweaksCreativeTabVisualGameTest::selectVanillaTweaksCreativeTab);
                context.waitTicks(2);
                context.runOnClient(client -> {
                    var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.parse("totem:vanilla_tweaks/main"));
                    String expected = locale.equals("zh_tw") ? "圖靈騰原版微調" : "Totem Vanilla Tweaks";
                    if (!tab.getDisplayName().getString().equals(expected)) {
                        throw new AssertionError("Untranslated VanillaTweaks tab: " + tab.getDisplayName().getString());
                    }
                });
                context.takeScreenshot("totem-vanilla-tweaks-creative-" + locale);
                context.runOnClient(VanillaTweaksCreativeTabVisualGameTest::closeScreen);
            }
        }
    }

    private static void openCreativeScreen(Object client) {
        try {
            Object player = client.getClass().getField("player").get(client);
            Object level = client.getClass().getField("level").get(client);
            Object enabledFeatures = invoke(level, "enabledFeatures");
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen");
            Class<?> playerClass = Class.forName("net.minecraft.client.player.LocalPlayer");
            Class<?> featureFlagsClass = Class.forName("net.minecraft.world.flag.FeatureFlagSet");
            creativeScreen = screenClass
                    .getConstructor(playerClass, featureFlagsClass, boolean.class)
                    .newInstance(player, enabledFeatures, true);
            invoke(client, "setScreenAndShow", creativeScreen);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not open the Creative inventory", exception);
        }
    }

    private static void selectVanillaTweaksCreativeTab(Object client) {
        try {
            if (creativeScreen == null) {
                throw new IllegalStateException("Creative inventory was not opened");
            }
            Identifier tabId = Identifier.fromNamespaceAndPath("totem", "vanilla_tweaks/main");
            CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(tabId);
            if (tab == null) {
                throw new IllegalStateException("Missing module-owned VanillaTweaks Creative tab");
            }
            assertTabContents(tab);
            if (BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(
                    Identifier.fromNamespaceAndPath("deadrecall", "main"))) {
                throw new IllegalStateException("Standalone VanillaTweaks still registered the legacy DeadRecall tab");
            }
            Class<?> tabClass = Class.forName("net.minecraft.world.item.CreativeModeTab");
            Class<?> screenExtension = Class.forName(
                    "net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen");
            boolean selected = (Boolean) screenExtension
                    .getMethod("setSelectedTab", tabClass)
                    .invoke(creativeScreen, tab);
            if (!selected && screenExtension.getMethod("getSelectedTab").invoke(creativeScreen) != tab) {
                throw new IllegalStateException("Could not switch to the VanillaTweaks Creative tab");
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not select the VanillaTweaks Creative tab", exception);
        }
    }

    private static void assertTabContents(CreativeModeTab tab) {
        List<Item> expected = List.of(VanillaTweaksContent.COOKED_PUFFERFISH,
                VanillaTweaksContent.SMOKED_ROTTEN_FLESH, VanillaTweaksContent.GRAVEL_IRON_ORE_ITEM);
        List<Item> actual = tab.getDisplayItems().stream().map(ItemStack::getItem).toList();
        if (!actual.equals(expected)) {
            throw new IllegalStateException("VanillaTweaks Creative tab contents differ: " + actual);
        }
    }

    private static boolean hasCreativeAbilities(Object client) {
        try {
            Object player = client.getClass().getField("player").get(client);
            if (player == null) {
                return false;
            }
            Object abilities = invoke(player, "getAbilities");
            return abilities.getClass().getField("instabuild").getBoolean(abilities);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not verify the Creative-mode transition", exception);
        }
    }

    private static void closeScreen(Object client) {
        try {
            invoke(client, "setScreenAndShow", new Object[]{null});
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not close the Creative inventory", exception);
        }
    }

    private static Object invoke(Object target, String name, Object... arguments) throws ReflectiveOperationException {
        for (var method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arguments.length) {
                return method.invoke(target, arguments);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
    }
}
