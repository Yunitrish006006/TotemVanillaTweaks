package dev.totem.vanillatweaks.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import dev.totem.vanillatweaks.client.ObserverUiClient;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import dev.totem.vanillatweaks.observer.ObserverFrameRules;
import dev.totem.vanillatweaks.observer.ObserverSessionManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the production Observer View payload path in both directions over a real integrated-server connection.
 *
 * <p>The test deliberately injects a self-observe relationship on the server, bypassing only the command's
 * self-observe guard. A real target and observer normally have independent client state. Because this loopback has
 * only one client process, automatic capture ticks are disabled after CaptureControl is verified; otherwise the
 * mirror screen itself would immediately be reported as ScreenState(false) by the target side. The test then sends
 * ScreenState and a frame through the production encoder/network stack and verifies the complete server relay,
 * client assembly, DynamicTexture render, Stop payload, and cleanup path.
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
            singleplayer.getClientLevel().waitForChunksRender();

            singleplayer.getServer().runCommand("gamemode spectator @a");
            playerId = singleplayer.getServer().computeOnServer(server -> {
                if (server.getPlayerList().getPlayers().size() != 1) {
                    throw new AssertionError("Loopback test expected exactly one connected player");
                }
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
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

            // A one-process loopback cannot leave the automatic target capture tick enabled after the mirror opens:
            // ObserverMirrorScreen is intentionally excluded from capture, which would feed ScreenState(false) back
            // into this same observer. Real target/observer clients do not share this state.
            context.runOnClient(minecraft -> setClientBoolean("captureEnabled", false));
            context.runOnClient(minecraft -> {
                ClientPlayNetworking.send(new ObserverPayloads.ScreenState(
                        true,
                        "gametest.ObserverNetworkLoopbackSource",
                        "Observer Network Loopback Source"
                ));
                encodeAndSendTestFrame(minecraft);
            });

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

            // Restore the target-side enabled state immediately before stopping, so the server's real
            // CaptureControl(false) response must be observed for this test to finish.
            context.runOnClient(minecraft -> setClientBoolean("captureEnabled", true));
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);

            context.waitFor(minecraft -> !getClientBoolean("sessionActive"), 100);
            context.waitFor(minecraft -> !getClientBoolean("captureEnabled"), 100);
            context.waitFor(minecraft -> !getClientBoolean("textureRegistered"), 100);
            context.waitForScreen(null);

            boolean serverCleanedUp = singleplayer.getServer().computeOnServer(server ->
                    !targetMap().containsKey(expectedTarget) && !frameGateMap().containsKey(expectedTarget));
            if (!serverCleanedUp) {
                throw new AssertionError("Observer Stop payload did not clean server session/frame state");
            }
        } finally {
            cleanupServer(singleplayer, playerId);
            forceClientCleanup(context);
            singleplayer.close();
        }
    }

    private static void encodeAndSendTestFrame(Minecraft minecraft) {
        try (NativeImage image = new NativeImage(160, 90, false)) {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    boolean checker = ((x / 10) + (y / 10)) % 2 == 0;
                    image.setPixel(x, y, checker ? 0xFF3D6B91 : 0xFF172433);
                }
            }
            Method method = CLIENT.getDeclaredMethod("encodeAndSendFrame", Minecraft.class, NativeImage.class);
            method.setAccessible(true);
            method.invoke(null, minecraft, image);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            throw new RuntimeException("Production Observer frame encoder failed", cause);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Failed to invoke ObserverUiClient.encodeAndSendFrame", error);
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
            Method method = CLIENT.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(null);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Failed to invoke ObserverUiClient." + name, error);
        }
    }
}
