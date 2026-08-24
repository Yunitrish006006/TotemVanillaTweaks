package dev.totem.vanillatweaks.e2e;

import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import dev.totem.vanillatweaks.observer.ObserverSessionManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/** Test-only coordinator for the dedicated-server + two-client Observer View E2E. */
public final class ObserverE2eCommon implements ModInitializer {
    private static final String TARGET_NAME = "Target";
    private static final String OBSERVER_NAME = "Observer";
    private static final int FIRST_CLIENT_TIMEOUT_TICKS = 20 * 90;
    private static final int SECOND_CLIENT_TIMEOUT_TICKS = 20 * 90;

    private static boolean started;
    private static boolean cleanedUp;
    private static int ticks;
    private static int firstClientSeenTick = -1;
    private static UUID targetId;
    private static UUID observerId;

    @Override
    public void onInitialize() {
        if (!enabled()) {
            return;
        }
        ServerTickEvents.END_SERVER_TICK.register(ObserverE2eCommon::tickServer);
    }

    private static void tickServer(MinecraftServer server) {
        if (cleanedUp) {
            return;
        }
        ticks++;
        try {
            if (!started) {
                ServerPlayer target = findPlayer(server, TARGET_NAME);
                ServerPlayer observer = findPlayer(server, OBSERVER_NAME);
                boolean oneClientPresent = target != null || observer != null;

                if (oneClientPresent && firstClientSeenTick < 0) {
                    firstClientSeenTick = ticks;
                    marker(
                            "server-first-client-seen.txt",
                            "tick=" + ticks + "\ntargetPresent=" + (target != null)
                                    + "\nobserverPresent=" + (observer != null) + "\n"
                    );
                }

                if (target == null || observer == null) {
                    int timeoutTick = firstClientSeenTick < 0
                            ? FIRST_CLIENT_TIMEOUT_TICKS
                            : firstClientSeenTick + SECOND_CLIENT_TIMEOUT_TICKS;
                    if (ticks > timeoutTick) {
                        fail(
                                "server",
                                "Timed out waiting for Target and Observer clients to join; targetPresent="
                                        + (target != null) + ", observerPresent=" + (observer != null)
                        );
                        cleanedUp = true;
                    }
                    return;
                }

                if (!supportsObserverPayloads(target, observer)) {
                    int payloadDeadline = firstClientSeenTick < 0
                            ? ticks + SECOND_CLIENT_TIMEOUT_TICKS
                            : firstClientSeenTick + SECOND_CLIENT_TIMEOUT_TICKS;
                    if (ticks > payloadDeadline) {
                        fail("server", "Connected clients never advertised all protocol-native Observer payloads");
                        cleanedUp = true;
                    }
                    return;
                }

                setSpectator(observer);
                int result = invokeProductionStart(observer, target);
                if (result != 1) {
                    throw new AssertionError("ObserverSessionManager.start returned " + result);
                }

                targetId = target.getUUID();
                observerId = observer.getUUID();
                if (!targetId.equals(targetMap().get(observerId))) {
                    throw new AssertionError("Production start did not register observer -> target session");
                }

                started = true;
                marker("server-session-started.txt",
                        "observer=" + observerId + "\ntarget=" + targetId
                                + "\nprotocol=" + ObserverNativePayloads.PROTOCOL_VERSION
                                + "\nframebuffer_transport=false\n");
                return;
            }

            boolean sessionPresent = observerId != null && targetMap().containsKey(observerId);
            if (!sessionPresent) {
                marker("server-cleanup-ok.txt", "Observer Stop removed protocol-native session state.\n");
                cleanedUp = true;
            }
        } catch (Throwable error) {
            fail("server", error.toString());
            cleanedUp = true;
        }
    }

    private static boolean supportsObserverPayloads(ServerPlayer target, ServerPlayer observer) {
        return ServerPlayNetworking.canSend(target, ObserverNativePayloads.NativeControl.TYPE)
                && ServerPlayNetworking.canSend(observer, ObserverNativePayloads.NativeSession.TYPE)
                && ServerPlayNetworking.canSend(observer, ObserverNativePayloads.NativeViewRelay.TYPE)
                && ServerPlayNetworking.canSend(observer, ObserverNativeScreenPayloads.ContainerRelay.TYPE)
                && ServerPlayNetworking.canSend(observer, ObserverPayloads.ScreenRelay.TYPE);
    }

    private static int invokeProductionStart(ServerPlayer observer, ServerPlayer target) {
        try {
            Method method = ObserverSessionManager.class.getDeclaredMethod(
                    "start",
                    CommandSourceStack.class,
                    ServerPlayer.class,
                    ServerPlayer.class
            );
            method.setAccessible(true);
            return (Integer) method.invoke(
                    null,
                    observer.createCommandSourceStack(),
                    observer,
                    target
            );
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Failed to invoke ObserverSessionManager.start", error);
        }
    }

    private static void setSpectator(ServerPlayer player) {
        try {
            Method method = ServerPlayer.class.getMethod("setGameMode", GameType.class);
            Object result = method.invoke(player, GameType.SPECTATOR);
            if (!player.isSpectator()) {
                throw new IllegalStateException("setGameMode did not put Observer into spectator mode: " + result);
            }
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Failed to put Observer into spectator mode", error);
        }
    }

    private static ServerPlayer findPlayer(MinecraftServer server, String name) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (name.equals(player.getGameProfile().name())) {
                return player;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, UUID> targetMap() {
        return (Map<UUID, UUID>) staticField(ObserverSessionManager.class, "TARGET_BY_OBSERVER");
    }

    private static Object staticField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Missing test-observed field " + owner.getSimpleName() + "." + name, error);
        }
    }

    private static boolean enabled() {
        return Boolean.getBoolean("totem.observer.e2e.enabled");
    }

    static Path resultsDir() {
        String configured = System.getProperty("totem.observer.e2e.results", "build/e2e/results");
        return Path.of(configured).toAbsolutePath();
    }

    static void marker(String fileName, String content) {
        try {
            Path directory = resultsDir();
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(fileName), content, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new RuntimeException("Failed to write E2E marker " + fileName, error);
        }
    }

    static void fail(String role, String message) {
        marker("failure-" + role + ".txt", message + "\n");
    }
}
