package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverHorseScreenPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server validation and session-bound relay for mount inventory semantics. */
public final class ObserverHorseRelayManager {
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();
    private static final Set<String> TYPES = Set.of("minecraft:horse", "minecraft:donkey", "minecraft:mule",
            "minecraft:llama", "minecraft:trader_llama", "minecraft:skeleton_horse", "minecraft:zombie_horse");

    private ObserverHorseRelayManager() { }

    public static void accept(ServerPlayer target, ObserverHorseScreenPayloads.HorseState state) {
        if (!valid(state)) return;
        UUID targetId = target.getUUID();
        long previous = LAST.getOrDefault(targetId, -1L);
        if (state.sequence() <= previous) return;
        var observers = ObserverNativeSessionManager.observerIdsForTarget(targetId, ObserverHorseScreenPayloads.CAPABILITY);
        if (observers.isEmpty()) return;
        LAST.put(targetId, state.sequence());
        var relay = ObserverHorseScreenPayloads.relay(targetId, state);
        for (UUID observerId : observers) {
            ServerPlayer observer = target.level().getServer().getPlayerList().getPlayer(observerId);
            if (observer != null && observer.isSpectator()
                    && ServerPlayNetworking.canSend(observer, ObserverHorseScreenPayloads.HorseRelay.TYPE)) {
                ServerPlayNetworking.send(observer, relay);
            }
        }
    }

    static boolean valid(ObserverHorseScreenPayloads.HorseState state) {
        if (state.protocolVersion() != ObserverHorseScreenPayloads.PROTOCOL_VERSION || state.sequence() < 0
                || !ObserverHorseScreenPayloads.FAMILY_ID.equals(state.familyId())) return false;
        if (!state.open()) return state.screenClass().isEmpty() && state.title().isEmpty()
                && state.entityId() == -1 && state.entityUuid().equals(new UUID(0L, 0L))
                && state.entityType().isEmpty() && state.columns() == 0 && state.slots().isEmpty();
        if (!ObserverHorseScreenPayloads.SCREEN_CLASS.equals(state.screenClass()) || state.entityId() < 0
                || state.entityUuid().equals(new UUID(0L, 0L)) || !TYPES.contains(state.entityType())
                || state.columns() < 0 || state.columns() > ObserverHorseScreenPayloads.MAX_COLUMNS
                || state.slots().size() != 38 + 3 * state.columns()) return false;
        if ((state.entityType().equals("minecraft:horse") || state.entityType().equals("minecraft:skeleton_horse")
                || state.entityType().equals("minecraft:zombie_horse")) && state.columns() != 0) return false;
        if ((state.entityType().equals("minecraft:donkey") || state.entityType().equals("minecraft:mule"))
                && state.columns() != 0 && state.columns() != 5) return false;
        for (int i = 0; i < state.slots().size(); i++) {
            var slot = state.slots().get(i);
            if (slot.index() != i || slot.stack().getCount() < 0 || slot.stack().getCount() > 99) return false;
        }
        return true;
    }

    public static void clearTarget(UUID target) { LAST.remove(target); }
}
