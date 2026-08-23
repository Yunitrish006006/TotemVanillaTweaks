package dev.totem.vanillatweaks.observer;

import com.mojang.brigadier.CommandDispatcher;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative observer-to-target relationships and UI frame relay. */
public final class ObserverSessionManager {
    private static final UUID EMPTY_TARGET = new UUID(0L, 0L);
    private static final long MIN_NEW_FRAME_INTERVAL_NANOS = 250_000_000L;
    private static final Map<UUID, UUID> TARGET_BY_OBSERVER = new HashMap<>();
    private static final Map<UUID, FrameGate> FRAME_GATE_BY_TARGET = new HashMap<>();

    private ObserverSessionManager() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> registerCommand(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(ObserverSessionManager::cleanup);
    }

    private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("observeui")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("stop")
                        .executes(context -> stop(context.getSource().getPlayerOrException(), true)))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> start(
                                context.getSource(),
                                context.getSource().getPlayerOrException(),
                                EntityArgument.getPlayer(context, "target")
                        ))));
    }

    private static int start(CommandSourceStack source, ServerPlayer observer, ServerPlayer target) {
        if (!observer.isSpectator()) {
            source.sendFailure(Component.literal("/observeui can only be used while in Spectator mode."));
            return 0;
        }
        if (observer == target) {
            source.sendFailure(Component.literal("You cannot observe your own UI."));
            return 0;
        }
        if (!ServerPlayNetworking.canSend(observer, ObserverPayloads.Session.TYPE)) {
            source.sendFailure(Component.literal("The observer client does not support Totem Observer View."));
            return 0;
        }
        if (!ServerPlayNetworking.canSend(target, ObserverPayloads.CaptureControl.TYPE)) {
            source.sendFailure(Component.literal("The target client does not support Totem Observer View."));
            return 0;
        }

        stop(observer, false);
        TARGET_BY_OBSERVER.put(observer.getUUID(), target.getUUID());
        observer.setCamera(target);
        ServerPlayNetworking.send(observer,
                new ObserverPayloads.Session(true, target.getUUID(), target.getGameProfile().name()));
        if (observerCount(target.getUUID()) == 1) {
            FRAME_GATE_BY_TARGET.remove(target.getUUID());
            ServerPlayNetworking.send(target, new ObserverPayloads.CaptureControl(
                    true,
                    ObserverFrameRules.MAX_WIDTH,
                    ObserverFrameRules.MAX_HEIGHT,
                    ObserverFrameRules.TARGET_FPS
            ));
        }
        source.sendSuccess(() -> Component.literal("Observing UI for " + target.getGameProfile().name()), false);
        return 1;
    }

    public static int stop(ServerPlayer observer, boolean resetCamera) {
        UUID targetId = TARGET_BY_OBSERVER.remove(observer.getUUID());
        if (targetId == null) {
            if (resetCamera) {
                observer.setCamera(null);
            }
            sendInactive(observer);
            return 0;
        }

        if (resetCamera) {
            observer.setCamera(null);
        }
        sendInactive(observer);
        if (observerCount(targetId) == 0) {
            FRAME_GATE_BY_TARGET.remove(targetId);
            MinecraftServer server = observer.level().getServer();
            ServerPlayer target = server.getPlayerList().getPlayer(targetId);
            if (target != null && ServerPlayNetworking.canSend(target, ObserverPayloads.CaptureControl.TYPE)) {
                ServerPlayNetworking.send(target, new ObserverPayloads.CaptureControl(false, 0, 0, 0));
            }
        }
        return 1;
    }

    public static void acceptScreenState(ServerPlayer target, ObserverPayloads.ScreenState payload) {
        forEachObserver(target.level().getServer(), target.getUUID(), observer ->
                ServerPlayNetworking.send(observer, new ObserverPayloads.ScreenRelay(
                        target.getUUID(), payload.open(), payload.screenClass(), payload.title()
                )));
    }

    public static void acceptFrameChunk(ServerPlayer target, ObserverPayloads.FrameChunk payload) {
        if (!ObserverFrameRules.validChunk(
                payload.chunkIndex(), payload.chunkCount(), payload.frameWidth(), payload.frameHeight(),
                payload.sourceWidth(), payload.sourceHeight(), payload.data().length)
                || observerCount(target.getUUID()) == 0
                || !acceptFrameId(target.getUUID(), payload.frameId())) {
            return;
        }
        forEachObserver(target.level().getServer(), target.getUUID(), observer ->
                ServerPlayNetworking.send(observer, new ObserverPayloads.FrameRelay(
                        target.getUUID(), payload.frameId(), payload.chunkIndex(), payload.chunkCount(),
                        payload.frameWidth(), payload.frameHeight(), payload.sourceWidth(), payload.sourceHeight(),
                        payload.mouseX(), payload.mouseY(), payload.data()
                )));
    }

    public static void acceptStop(ServerPlayer observer) {
        stop(observer, true);
    }

    private static boolean acceptFrameId(UUID targetId, long frameId) {
        long now = System.nanoTime();
        FrameGate gate = FRAME_GATE_BY_TARGET.get(targetId);
        if (gate == null) {
            FRAME_GATE_BY_TARGET.put(targetId, new FrameGate(frameId, now));
            return true;
        }
        if (frameId == gate.frameId()) {
            return true;
        }
        if (frameId < gate.frameId() || now - gate.acceptedAtNanos() < MIN_NEW_FRAME_INTERVAL_NANOS) {
            return false;
        }
        FRAME_GATE_BY_TARGET.put(targetId, new FrameGate(frameId, now));
        return true;
    }

    private static void cleanup(MinecraftServer server) {
        for (UUID observerId : new ArrayList<>(TARGET_BY_OBSERVER.keySet())) {
            ServerPlayer observer = server.getPlayerList().getPlayer(observerId);
            UUID targetId = TARGET_BY_OBSERVER.get(observerId);
            ServerPlayer target = targetId == null ? null : server.getPlayerList().getPlayer(targetId);
            if (observer == null) {
                removeOfflineObserver(server, observerId, targetId);
            } else if (!observer.isSpectator() || target == null) {
                stop(observer, true);
            }
        }
    }

    private static void removeOfflineObserver(MinecraftServer server, UUID observerId, UUID targetId) {
        TARGET_BY_OBSERVER.remove(observerId);
        if (targetId != null && observerCount(targetId) == 0) {
            FRAME_GATE_BY_TARGET.remove(targetId);
            ServerPlayer target = server.getPlayerList().getPlayer(targetId);
            if (target != null && ServerPlayNetworking.canSend(target, ObserverPayloads.CaptureControl.TYPE)) {
                ServerPlayNetworking.send(target, new ObserverPayloads.CaptureControl(false, 0, 0, 0));
            }
        }
    }

    private static int observerCount(UUID targetId) {
        int count = 0;
        for (UUID value : TARGET_BY_OBSERVER.values()) {
            if (targetId.equals(value)) {
                count++;
            }
        }
        return count;
    }

    private static void forEachObserver(MinecraftServer server, UUID targetId, java.util.function.Consumer<ServerPlayer> action) {
        for (Map.Entry<UUID, UUID> entry : TARGET_BY_OBSERVER.entrySet()) {
            if (!targetId.equals(entry.getValue())) {
                continue;
            }
            ServerPlayer observer = server.getPlayerList().getPlayer(entry.getKey());
            if (observer != null && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverPayloads.FrameRelay.TYPE)) {
                action.accept(observer);
            }
        }
    }

    private static void sendInactive(ServerPlayer observer) {
        if (ServerPlayNetworking.canSend(observer, ObserverPayloads.Session.TYPE)) {
            ServerPlayNetworking.send(observer, new ObserverPayloads.Session(false, EMPTY_TARGET, ""));
        }
    }

    private record FrameGate(long frameId, long acceptedAtNanos) {
    }
}
