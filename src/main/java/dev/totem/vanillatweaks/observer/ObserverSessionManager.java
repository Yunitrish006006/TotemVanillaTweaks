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

/** Server-authoritative observer-to-target relationships for protocol-native Observer View. */
public final class ObserverSessionManager {
    private static final Map<UUID, UUID> TARGET_BY_OBSERVER = new HashMap<>();

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
        if (!ObserverNativeSessionManager.supports(observer, target)) {
            source.sendFailure(Component.literal(
                    "Observer View now requires protocol-native TotemVanillaTweaks clients on both players; framebuffer fallback was removed."
            ));
            return 0;
        }

        stop(observer, false);
        TARGET_BY_OBSERVER.put(observer.getUUID(), target.getUUID());
        observer.setCamera(target);
        if (!ObserverNativeSessionManager.start(observer, target)) {
            TARGET_BY_OBSERVER.remove(observer.getUUID());
            observer.setCamera(null);
            source.sendFailure(Component.literal("Failed to negotiate protocol-native Observer View."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Observing natively for " + target.getGameProfile().name()
        ), false);
        return 1;
    }

    public static int stop(ServerPlayer observer, boolean resetCamera) {
        UUID targetId = TARGET_BY_OBSERVER.remove(observer.getUUID());
        boolean nativeSession = ObserverNativeSessionManager.stop(observer);
        if (resetCamera) {
            observer.setCamera(null);
        }
        return targetId != null || nativeSession ? 1 : 0;
    }

    public static void acceptScreenState(ServerPlayer target, ObserverPayloads.ScreenState payload) {
        forEachObserver(target.level().getServer(), target.getUUID(), observer ->
                ServerPlayNetworking.send(observer, new ObserverPayloads.ScreenRelay(
                        target.getUUID(), payload.open(), payload.screenClass(), payload.title()
                )));
    }

    public static void acceptStop(ServerPlayer observer) {
        stop(observer, true);
    }

    private static void cleanup(MinecraftServer server) {
        for (UUID observerId : new ArrayList<>(TARGET_BY_OBSERVER.keySet())) {
            ServerPlayer observer = server.getPlayerList().getPlayer(observerId);
            UUID targetId = TARGET_BY_OBSERVER.get(observerId);
            ServerPlayer target = targetId == null ? null : server.getPlayerList().getPlayer(targetId);
            if (observer == null) {
                TARGET_BY_OBSERVER.remove(observerId);
                ObserverNativeSessionManager.removeOfflineObserver(server, observerId);
            } else if (!observer.isSpectator() || target == null) {
                stop(observer, true);
            }
        }
    }

    private static void forEachObserver(
            MinecraftServer server,
            UUID targetId,
            java.util.function.Consumer<ServerPlayer> action
    ) {
        for (Map.Entry<UUID, UUID> entry : TARGET_BY_OBSERVER.entrySet()) {
            if (!targetId.equals(entry.getValue())) {
                continue;
            }
            ServerPlayer observer = server.getPlayerList().getPlayer(entry.getKey());
            if (observer != null
                    && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverPayloads.ScreenRelay.TYPE)) {
                action.accept(observer);
            }
        }
    }
}
