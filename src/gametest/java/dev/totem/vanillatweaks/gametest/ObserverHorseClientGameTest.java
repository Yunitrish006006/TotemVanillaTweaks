package dev.totem.vanillatweaks.gametest;

import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import dev.totem.vanillatweaks.client.ObserverHorseScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.network.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ObserverHorseClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            UUID target = UUID.randomUUID(), mount = UUID.randomUUID();
            context.runOnClient(client -> {
                applySession(true, target, ObserverHorseScreenPayloads.CAPABILITY);
                accept(ObserverHorseScreenPayloads.relay(target, open(1, mount, 1)));
            });
            context.waitFor(client -> client.gui.screen() instanceof HorseInventoryScreen
                    && client.gui.screen() instanceof ObserverReadOnlyScreen marker
                    && marker.totem$isObserverReadOnly(), 100);
            context.waitFor(client -> getLong("extractedFrames") > 0, 100);
            long beforeUpdate = getLong("extractedFrames");
            context.runOnClient(client -> accept(ObserverHorseScreenPayloads.relay(target, open(2, mount, 4))));
            context.waitFor(client -> client.gui.screen() instanceof HorseInventoryScreen horse
                    && horse.getMenu().getSlot(2).getItem().getCount() == 4
                    && Component.literal("Remote gem").equals(horse.getMenu().getSlot(2).getItem()
                    .get(DataComponents.CUSTOM_NAME))
                    && "Llama".equals(client.gui.screen().getTitle().getString())
                    && getLong("extractedFrames") > beforeUpdate, 100);
            persist(context.takeScreenshot("observer-ui-native-horse-inventory-screen"),
                    "observer-ui-native-horse-inventory-screen.png");
            context.runOnClient(client -> {
                accept(ObserverHorseScreenPayloads.relay(target, ObserverHorseScreenPayloads.closed(3)));
                applySession(false, target, 0);
            });
            context.waitForScreen(null);
        }
    }

    private static ObserverHorseScreenPayloads.HorseState open(long sequence, UUID mount, int diamonds) {
        List<ObserverHorseScreenPayloads.HorseSlotState> slots = new ArrayList<>();
        for (int i = 0; i < 47; i++) slots.add(new ObserverHorseScreenPayloads.HorseSlotState(i, 0, 0, ItemStack.EMPTY));
        ItemStack gem = new ItemStack(Items.DIAMOND, diamonds);
        gem.set(DataComponents.CUSTOM_NAME, Component.literal("Remote gem"));
        slots.set(2, new ObserverHorseScreenPayloads.HorseSlotState(2, 80, 18, gem));
        return new ObserverHorseScreenPayloads.HorseState(1, sequence, true,
                ObserverHorseScreenPayloads.FAMILY_ID, ObserverHorseScreenPayloads.SCREEN_CLASS,
                "Llama", 4242, mount, "minecraft:llama", 3, slots);
    }

    private static void accept(ObserverHorseScreenPayloads.HorseRelay relay) {
        invoke(ObserverHorseScreenClient.class, "accept", new Class<?>[]{ObserverHorseScreenPayloads.HorseRelay.class}, relay);
    }

    private static void applySession(boolean active, UUID target, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession", new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, target, active ? "HorseTarget" : "",
                        ObserverNativePayloads.PROTOCOL_VERSION, capabilities));
    }

    private static void invoke(Class<?> owner, String name, Class<?>[] types, Object... args) {
        try { Method method = owner.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(null, args); }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    private static long getLong(String name) {
        try { Field field = ObserverHorseScreenClient.class.getDeclaredField(name); field.setAccessible(true); return field.getLong(null); }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    private static void persist(Path screenshot, String name) {
        String workspace = System.getenv("GITHUB_WORKSPACE");
        if (workspace == null || workspace.isBlank()) return;
        try {
            Path dir = Path.of(workspace).resolve("build/client-gametest-screenshots");
            Files.createDirectories(dir); Files.copy(screenshot, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) { throw new RuntimeException(error); }
    }
}
