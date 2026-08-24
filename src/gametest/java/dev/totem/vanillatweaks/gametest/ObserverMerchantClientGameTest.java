package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeMerchantScreenClient;
import dev.totem.vanillatweaks.network.ObserverMerchantScreenPayloads;
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
import java.util.List;
import java.util.UUID;

/** Client runtime proof for framebuffer-free merchant semantic reconstruction. */
public final class ObserverMerchantClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();

            UUID targetId = UUID.randomUUID();
            ObserverMerchantScreenPayloads.ItemState emeralds =
                    new ObserverMerchantScreenPayloads.ItemState("minecraft:emerald", 5, 0);
            ObserverMerchantScreenPayloads.ItemState book =
                    new ObserverMerchantScreenPayloads.ItemState("minecraft:book", 1, 0);
            ObserverMerchantScreenPayloads.ItemState enchantedBook =
                    new ObserverMerchantScreenPayloads.ItemState("minecraft:enchanted_book", 1, 0);
            ObserverMerchantScreenPayloads.ItemState carrots =
                    new ObserverMerchantScreenPayloads.ItemState("minecraft:carrot", 22, 0);
            ObserverMerchantScreenPayloads.ItemState empty =
                    new ObserverMerchantScreenPayloads.ItemState("", 0, 0);

            ObserverMerchantScreenPayloads.MerchantRelay open = new ObserverMerchantScreenPayloads.MerchantRelay(
                    targetId,
                    ObserverMerchantScreenPayloads.PROTOCOL_VERSION,
                    1L,
                    true,
                    ObserverNativeScreenPayloads.FAMILY_MERCHANT,
                    ObserverMerchantScreenPayloads.VARIANT_VANILLA,
                    "net.minecraft.client.gui.screens.inventory.MerchantScreen",
                    "Observer Merchant Test",
                    1,
                    3,
                    72,
                    150,
                    true,
                    true,
                    List.of(
                            new ObserverMerchantScreenPayloads.OfferState(
                                    0, carrots, empty, emeralds, 2, 16, 2, false),
                            new ObserverMerchantScreenPayloads.OfferState(
                                    1, emeralds, book, enchantedBook, 12, 12, 5, true)
                    )
            );

            context.runOnClient(minecraft -> {
                applySession(true, targetId, ObserverNativeScreenPayloads.CAPABILITY_MERCHANT);
                invoke(ObserverNativeMerchantScreenClient.class, "acceptRelay",
                        new Class<?>[]{ObserverMerchantScreenPayloads.MerchantRelay.class}, open);
            });

            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("NativeMerchantMirrorScreen"), 100);
            context.waitFor(minecraft -> getStaticLong(ObserverNativeMerchantScreenClient.class, "extractedFrames") > 0L, 100);
            if (!"vanilla_merchant".equals(getStaticObject(ObserverNativeMerchantScreenClient.class, "remoteVariant"))) {
                throw new AssertionError("Merchant variant was not reconstructed");
            }
            if (getStaticInt(ObserverNativeMerchantScreenClient.class, "remoteSelectedOffer") != 1) {
                throw new AssertionError("Selected merchant offer was not reconstructed");
            }
            persistForCi(context.takeScreenshot("observer-ui-native-merchant-screen"),
                    "observer-ui-native-merchant-screen.png");

            ObserverMerchantScreenPayloads.MerchantRelay close = new ObserverMerchantScreenPayloads.MerchantRelay(
                    targetId,
                    ObserverMerchantScreenPayloads.PROTOCOL_VERSION,
                    2L,
                    false,
                    ObserverNativeScreenPayloads.FAMILY_MERCHANT,
                    "",
                    "",
                    "",
                    0,
                    0,
                    0,
                    0,
                    false,
                    false,
                    List.of()
            );
            context.runOnClient(minecraft -> {
                invoke(ObserverNativeMerchantScreenClient.class, "acceptRelay",
                        new Class<?>[]{ObserverMerchantScreenPayloads.MerchantRelay.class}, close);
                applySession(false, new UUID(0L, 0L), 0L);
            });
            context.waitForScreen(null);
        }
    }

    private static void applySession(boolean active, UUID targetId, long capabilities) {
        invoke(ObserverNativeClient.class, "applySession",
                new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId, active ? "MerchantTarget" : "",
                        ObserverNativePayloads.PROTOCOL_VERSION, capabilities));
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

    private static long getStaticLong(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.getLong(null);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private static int getStaticInt(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private static Object getStaticObject(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException error) {
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
            throw new RuntimeException("Failed to persist merchant screenshot", error);
        }
    }
}
