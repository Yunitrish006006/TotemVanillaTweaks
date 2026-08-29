package dev.totem.vanillatweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Narrow, client-only firewall for packets that can mutate UI-owned server state. */
public final class ObserverReadOnlyPacketFirewall {
    private static final ThreadLocal<Integer> TRANSITION_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final Map<Class<?>, AtomicLong> SUPPRESSED = new ConcurrentHashMap<>();

    private ObserverReadOnlyPacketFirewall() { }

    public static void beginScreenTransition(Screen previous, Screen next) {
        if (readOnly(previous) || readOnly(next)) {
            TRANSITION_DEPTH.set(TRANSITION_DEPTH.get() + 1);
        }
    }

    public static void endScreenTransition(Screen previous, Screen next) {
        if (!readOnly(previous) && !readOnly(next)) return;
        int depth = TRANSITION_DEPTH.get() - 1;
        if (depth <= 0) TRANSITION_DEPTH.remove();
        else TRANSITION_DEPTH.set(depth);
    }

    public static boolean suppress(Packet<?> packet) {
        Screen current = Minecraft.getInstance().gui.screen();
        if (!readOnly(current) && TRANSITION_DEPTH.get() <= 0) return false;
        if (!isUiActionPacket(packet)) return false;
        SUPPRESSED.computeIfAbsent(packet.getClass(), ignored -> new AtomicLong()).incrementAndGet();
        return true;
    }

    public static boolean isUiActionPacket(Packet<?> packet) {
        if (packet instanceof ServerboundClientCommandPacket command) {
            return command.getAction() == ServerboundClientCommandPacket.Action.REQUEST_STATS;
        }
        return packet instanceof ServerboundContainerClickPacket
                || packet instanceof ServerboundContainerClosePacket
                || packet instanceof ServerboundContainerButtonClickPacket
                || packet instanceof ServerboundContainerSlotStateChangedPacket
                || packet instanceof ServerboundRenameItemPacket
                || packet instanceof ServerboundSelectTradePacket
                || packet instanceof ServerboundSetBeaconPacket
                || packet instanceof ServerboundEditBookPacket
                || packet instanceof ServerboundSignUpdatePacket
                || packet instanceof ServerboundSeenAdvancementsPacket
                || packet instanceof ServerboundPlaceRecipePacket
                || packet instanceof ServerboundRecipeBookChangeSettingsPacket
                || packet instanceof ServerboundRecipeBookSeenRecipePacket;
    }

    public static long suppressedCount(Class<? extends Packet<?>> packetClass) {
        AtomicLong count = SUPPRESSED.get(packetClass);
        return count == null ? 0L : count.get();
    }

    public static long suppressedMutationPacketTotal() {
        return SUPPRESSED.values().stream().mapToLong(AtomicLong::get).sum();
    }

    static boolean readOnly(Screen screen) {
        return ObserverOwnedScreenCoordinator.isReadOnlyObserverScreen(screen);
    }
}
