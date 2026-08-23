package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.client.ObserverUiClient;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import dev.totem.vanillatweaks.observer.ObserverFrameRules;
import dev.totem.vanillatweaks.observer.ObserverSessionManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the production Observer View payload path in both directions over a real integrated-server connection.
 *
 * <p>The test deliberately injects a self-observe relationship on the server, bypassing only the command's
 * self-observe guard. Everything after that point is production networking: target capture -> server receiver ->
 * frame gate -> server relay -> client assembly -> DynamicTexture -> mirror screen -> Stop payload cleanup.
 */
public final class ObserverUiNetworkLoopbackClientGameTest implements FabricClientGameTest {
    private static final Class<?> CLIENT = ObserverUiClient.class;
    private static final Class<?> SERVER = ObserverSessionManager.class;

    @Override
    public void runTest(ClientGameTestContext context) {
        TestSingleplayerContext singleplayer = context.worldBuilder().create();
        UUID playerId = null;
        try {
            context.waitTicks(2);
            singleplayer.getConnection().waitForChunksRender();

            singleplayer.getServer().runCommand("gamemode spectator @a");
            playerId = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = singleplayer.getConnection().getServerPlayer();
                if (!player.isSpectator()) {
                    throw new AssertionError("Loopback observer player did not enter spectator mode");
                }
                assertCanSend(player, ObserverPayloads.Session.TYPE, "Session");
                assertCanSend(player, ObserverPayloads.CaptureControl.TYPE, "CaptureControl");
                assertCanSend(player, ObserverPayloads.ScreenRelay.TYPE, "ScreenRelay");
                assertCanSend(player, ObserverPayloads.FrameRelay.TYPE, "FrameRelay");

                UUID id = player.getUUID();
                targetMap().put(id, id);
                frameGateMap().remove(id);
                ServerPlayNetworking.send(player, new ObserverPayloads.Session(true, id, "ObserverLoopbackTarget"));
                ServerPlayNetworking.send(player, new ObserverPayloads.CaptureControl(
                        true,
                        ObserverFrameRules.MAX_WIDTH,
                        ObserverFrameRules.MAX_HEIGHT,
                        ObserverFrameRules.TARGET_FPS
                ));
                return id;
            });

            UUID expectedTarget = playerId;
            context.waitFor(minecraft -> getClientBoolean("sessionActive")
                    && getClientBoolean("captureEnabled")
                    && expectedTarget.equals(getClientObject("targetId")), 100);

            context.setScreen(() -> new Screen(Component.literal("Observer Network Loopback Source")) {
                @Override
                public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                    graphics.fill(0, 0, width, height, 0xFF172433);
                    graphics.fill(width / 5, height / 4, width * 4 / 5, height * 3 / 4, 0xFF3D6B91);
                    graphics.text(font, Component.literal("Observer network loopback source"), 12, 12, 0xFFFFFFFF);
                }

                @Override
                public boolean isPauseScreen() {
                    return false;
                }
            });
            context.waitFor(minecraft -> minecraft.gui.screen() != null
                    && "Observer Network Loopback Source".equals(minecraft.gui.screen().getTitle().getString()));

            context.waitFor(minecraft -> getClientBoolean("textureRegistered")
                    && getClientLong("lastFrameId") >= 0L
                    && minecraft.gui.screen() != null
                    && minecraft.gui.screen().getClass().getName().contains("ObserverMirrorScreen"), 200);

            boolean serverAcceptedFrame = singleplayer.getServer().computeOnServer(server ->
                    frameGateMap().containsKey(expectedTarget));
            if (!serverAcceptedFrame) {
                throw new AssertionError("Server did not accept a production Observer FrameChunk");
            }
            if (!"ObserverLoopbackTarget".equals(getClientObject("targetName"))) {
                throw new AssertionError("Observer Session payload target name was not applied on the client");
            }
            if (!getClientBoolean("remoteScreenOpen")) {
                throw new AssertionError("Observer ScreenRelay payload did not open the mirror state");
            }
            if (getClientInt("frameWidth") <= 0 || getClientInt("frameHeight") <= 0) {
                throw new AssertionError("Observer FrameRelay payload did not install a decoded frame");
            }

            context.waitTicks(2);
            persistForCi(
                    context.takeScreenshot("observer-ui-network-loopback"),
                    "observer-ui-network-loopback.png"
            );

            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitForScreen(null);
            context.waitFor(minecraft -> !getClientBoolean("sessionActive")
                    && !getClientBoolean("captureEnabled")
                    && !getClientBoolean("textureRegistered"), 100);
            singleplayer.getServer().waitFor(server ->
                    !targetMap().containsKey(expectedTarget) && !frameGateMap().containsKey(expectedTarget), 100);
        } finally {
            cleanupServer(singleplayer, playerId);
            forceClientCleanup(context);
            singleplayer.close();
        }
    }

    private static void assertCanSend(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> type,
                                      String name) {
        if (!ServerPlayNetworking.canSend(player, type)) {
            throw new AssertionError("Connected client does not advertise Observer " + name + " support");
        }
    }

    private static void cleanupServer(TestSingleplayerContext singleplayer, UUID playerId) {
        if (playerId == null) {
            return;
        }
        try {
            singleplayer.getServer().runOnServer(server -> {
                targetMap().remove(playerId);
                frameGateMap().remove(playerId);
            });
        } catch (Throwable ignored) {
        }
    }

    private static void forceClientCleanup(ClientGameTestContext context) {
        try {
            context.runOnClient(minecraft -> {
                setClientBoolean("captureEnabled", false);
                setClientBoolean("sessionActive", false);
                setClientBoolean("remoteScreenOpen", false);
                invokeClient("closeMirrorScreen");
                invokeClient("releaseFrameTexture");
            });
        } catch (Throwable ignored) {
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

    @SuppressWarnings("unchecked")
    private static Map<UUID, UUID> targetMap() {
        return (Map<UUID, UUID>) getServerStatic("TARGET_BY_OBSERVER");
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> frameGateMap() {
        return (Map<UUID, Object>) getServerStatic("FRAME_GATE_BY_TARGET");
    }

    private static Object getServerStatic(String name) {
        try {
            Field field = SERVER.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Missing ObserverSessionManager field: " + name, error);
        }
    }

    private static Field clientField(String name) {
        try {
            Field field = CLIENT.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Missing ObserverUiClient field: " + name, error);
        }
    }

    private static boolean getClientBoolean(String name) {
        try {
            return clientField(name).getBoolean(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static int getClientInt(String name) {
        try {
            return clientField(name).getInt(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static long getClientLong(String name) {
        try {
            return clientField(name).getLong(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static Object getClientObject(String name) {
        try {
            return clientField(name).get(null);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static void setClientBoolean(String name, boolean value) {
        try {
            clientField(name).setBoolean(null, value);
        } catch (IllegalAccessException error) {
            throw new RuntimeException(error);
        }
    }

    private static void invokeClient(String name) {
        try {
            var method = CLIENT.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(null);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Failed to invoke ObserverUiClient." + name, error);
        }
    }
}
