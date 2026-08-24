package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.client.ObserverUiClient;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Client smoke test for the framebuffer-free Observer UI bridge.
 * Verifies removed frame surfaces stay absent and an unsupported Screen is represented by local metadata UI only.
 */
public final class ObserverUiClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();

            assertFramebufferSurfaceRemoved();

            context.setScreen(() -> new Screen(Component.literal("Observer Metadata Target")) {
                @Override
                public boolean isPauseScreen() {
                    return false;
                }
            });
            context.waitFor(mc -> mc.gui.screen() != null
                    && "Observer Metadata Target".equals(mc.gui.screen().getTitle().getString()));
            persistForCi(context.takeScreenshot("observer-ui-source-screen"), "observer-ui-source-screen.png");

            context.setScreen(() -> null);
            context.waitForScreen(null);

            UUID targetId = UUID.randomUUID();
            context.runOnClient(minecraft -> {
                invoke(
                        ObserverNativeClient.class,
                        "applySession",
                        new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                        new ObserverNativePayloads.NativeSession(
                                true,
                                targetId,
                                "ObserverSmokeTarget",
                                ObserverNativePayloads.PROTOCOL_VERSION
                        )
                );
                invoke(
                        ObserverNativeScreenClient.class,
                        "applyGenericScreenState",
                        new Class<?>[]{boolean.class, String.class, String.class},
                        true,
                        "gametest.UnsupportedScreen",
                        "Unsupported Metadata Screen"
                );
            });

            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("NativeGenericMirrorScreen"), 100);
            context.waitTicks(2);
            persistForCi(
                    context.takeScreenshot("observer-ui-native-generic-screen"),
                    "observer-ui-native-generic-screen.png"
            );

            context.runOnClient(minecraft -> {
                invoke(
                        ObserverNativeScreenClient.class,
                        "applyGenericScreenState",
                        new Class<?>[]{boolean.class, String.class, String.class},
                        false,
                        "",
                        ""
                );
                invoke(
                        ObserverNativeClient.class,
                        "applySession",
                        new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                        new ObserverNativePayloads.NativeSession(
                                false,
                                new UUID(0L, 0L),
                                "",
                                ObserverNativePayloads.PROTOCOL_VERSION
                        )
                );
            });
            context.waitForScreen(null);
            assertFramebufferSurfaceRemoved();
        }
    }

    private static void assertFramebufferSurfaceRemoved() {
        Set<String> payloadTypes = Arrays.stream(ObserverPayloads.class.getDeclaredClasses())
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());
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

    private static void invoke(
            Class<?> owner,
            String name,
            Class<?>[] parameterTypes,
            Object... args
    ) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            method.invoke(null, args);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Failed to invoke " + owner.getSimpleName() + "." + name, error);
        }
    }

    private static void persistForCi(Path screenshot, String fileName) {
        String workspace = System.getenv("GITHUB_WORKSPACE");
        if (workspace == null || workspace.isBlank()) {
            return;
        }
        try {
            Path destinationDir = Path.of(workspace).resolve("build/client-gametest-screenshots");
            Files.createDirectories(destinationDir);
            Files.copy(screenshot, destinationDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            throw new RuntimeException("Failed to persist client gametest screenshot " + screenshot, error);
        }
    }
}
