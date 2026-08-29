package dev.totem.vanillatweaks.client;

import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import dev.totem.vanillatweaks.mixin.client.AbstractMountInventoryScreenAccessor;
import dev.totem.vanillatweaks.network.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Target capture and genuine vanilla HorseInventoryScreen reconstruction. */
public final class ObserverHorseScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static long targetSequence, lastSnapshotNanos, extractedFrames;
    private static boolean targetOpen, remoteOpen, suppressStop;
    private static ObserverHorseScreenPayloads.HorseRelay remote;
    private static long lastSendErrorNanos;

    private ObserverHorseScreenClient() { }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ObserverHorseScreenPayloads.HorseRelay.TYPE,
                (payload, context) -> context.client().execute(() -> accept(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverHorseScreenClient::tick);
    }

    private static void tick(Minecraft minecraft) {
        tickTarget(minecraft);
        if (!ObserverNativeClient.observerSessionActive()) { remoteOpen = false; closeObserverScreen(); }
        else if (remoteOpen) ensureObserverScreen();
    }

    private static void tickTarget(Minecraft minecraft) {
        if (!ObserverNativeClient.targetStateEnabled() || minecraft.player == null || minecraft.level == null
                || !ObserverNativeClient.targetSupportsScreen(ObserverHorseScreenPayloads.CAPABILITY)
                || !(minecraft.gui.screen() instanceof HorseInventoryScreen screen)
                || screen instanceof ObserverHorseInventoryScreen) {
            closeTarget(); return;
        }
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        if (!ClientPlayNetworking.canSend(ObserverHorseScreenPayloads.HorseState.TYPE)) return;
        ObserverHorseScreenPayloads.HorseState state = captureTargetState(screen, ++targetSequence);
        if (state == null) return;
        try {
            ClientPlayNetworking.send(state);
        } catch (RuntimeException encodeFailure) {
            if (now - lastSendErrorNanos >= 5_000_000_000L) {
                lastSendErrorNanos = now;
                dev.totem.vanillatweaks.TotemVanillaTweaks.LOGGER.warn(
                        "Horse Observer encode/send failed; closing its relay state", encodeFailure);
            }
            closeTarget();
            return;
        }
        targetOpen = true; lastSnapshotNanos = now;
    }

    static ObserverHorseScreenPayloads.HorseState captureTargetState(HorseInventoryScreen screen, long sequence) {
        AbstractMountInventoryScreenAccessor accessor = (AbstractMountInventoryScreenAccessor) (Object) screen;
        if (!(accessor.totem$getMount() instanceof AbstractHorse mount)) return null;
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(mount.getType()).toString();
        List<ObserverHorseScreenPayloads.HorseSlotState> slots = captureSlots(screen.getMenu());
        return new ObserverHorseScreenPayloads.HorseState(
                ObserverHorseScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverHorseScreenPayloads.FAMILY_ID, ObserverHorseScreenPayloads.SCREEN_CLASS,
                screen.getTitle().getString(), mount.getId(), mount.getUUID(), type,
                accessor.totem$getInventoryColumns(), slots);
    }

    private static List<ObserverHorseScreenPayloads.HorseSlotState> captureSlots(HorseInventoryMenu menu) {
        List<ObserverHorseScreenPayloads.HorseSlotState> result = new ArrayList<>(menu.slots.size());
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index); ItemStack stack = slot.getItem();
            result.add(new ObserverHorseScreenPayloads.HorseSlotState(index, slot.x, slot.y, stack));
        }
        return List.copyOf(result);
    }

    private static void closeTarget() {
        if (!targetOpen) return;
        targetOpen = false; lastSnapshotNanos = 0;
        if (ClientPlayNetworking.canSend(ObserverHorseScreenPayloads.HorseState.TYPE))
            ClientPlayNetworking.send(ObserverHorseScreenPayloads.closed(++targetSequence));
    }

    private static void accept(ObserverHorseScreenPayloads.HorseRelay payload) {
        UUID target = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive() || target == null || !target.equals(payload.targetId())
                || !ObserverNativeClient.observerSupportsScreen(ObserverHorseScreenPayloads.CAPABILITY)
                || !ObserverHorseRelayManagerClient.valid(payload)
                || !ObserverRemoteSequenceTracker.accept(ObserverHorseScreenPayloads.FAMILY_ID,
                payload.targetId(), payload.sequence())) return;
        if (!payload.open()) { remoteOpen = false; remote = null; closeObserverScreen(); return; }
        remote = payload; remoteOpen = true;
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        ensureObserverScreen();
    }

    private static void ensureObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (remote == null || minecraft.player == null || minecraft.level == null) return;
        boolean compatible = minecraft.gui.screen() instanceof ObserverHorseInventoryScreen existing
                && existing.entityUuid.equals(remote.entityUuid()) && existing.entityType.equals(remote.entityType())
                && existing.columns == remote.columns();
        if (!compatible) {
            AbstractHorse mount = resolveMount(minecraft, remote);
            var inventory = ObserverVanillaScreenSupport.detachedInventory();
            var storage = new SimpleContainer(2 + remote.columns() * 3);
            var menu = new HorseInventoryMenu(-1, inventory, storage, mount, remote.columns());
            suppressStop = true;
            try { minecraft.setScreenAndShow(new ObserverHorseInventoryScreen(menu, inventory, mount,
                    remote.columns(), remote.entityUuid(), remote.entityType())); }
            finally { suppressStop = false; }
        }
        if (minecraft.gui.screen() instanceof ObserverHorseInventoryScreen screen) applyMenu(screen.getMenu(), remote.slots());
    }

    private static AbstractHorse resolveMount(Minecraft minecraft, ObserverHorseScreenPayloads.HorseRelay state) {
        Entity tracked = minecraft.level.getEntity(state.entityId());
        if (tracked instanceof AbstractHorse horse && horse.getUUID().equals(state.entityUuid())
                && BuiltInRegistries.ENTITY_TYPE.getKey(horse.getType()).toString().equals(state.entityType())) return horse;
        var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(state.entityType()));
        Entity created = type == null ? null : type.create(minecraft.level, EntitySpawnReason.LOAD);
        if (!(created instanceof AbstractHorse horse)) throw new IllegalArgumentException("Invalid Observer horse type");
        horse.setUUID(state.entityUuid());
        // Vanilla's inventory renderer resolves item models with the entity id even
        // though this read-only projection is deliberately never added to the level.
        // Keep it in a disjoint negative range so it cannot shadow a tracked entity.
        horse.setId(-1 - (state.entityUuid().hashCode() & Integer.MAX_VALUE));
        horse.setCustomName(Component.literal(state.title()));
        return horse;
    }

    private static void applyMenu(HorseInventoryMenu menu,
                                  List<ObserverHorseScreenPayloads.HorseSlotState> slots) {
        for (var remoteSlot : slots) {
            if (remoteSlot.index() >= 0 && remoteSlot.index() < menu.slots.size())
                menu.getSlot(remoteSlot.index()).set(remoteSlot.stack());
        }
    }

    private static final class ObserverHorseRelayManagerClient {
        private static boolean valid(ObserverHorseScreenPayloads.HorseRelay state) {
            if (state.protocolVersion() != ObserverHorseScreenPayloads.PROTOCOL_VERSION || state.sequence() < 0
                    || !ObserverHorseScreenPayloads.FAMILY_ID.equals(state.familyId())) return false;
            if (!state.open()) return state.slots().isEmpty();
            if (!ObserverHorseScreenPayloads.SCREEN_CLASS.equals(state.screenClass()) || state.entityId() < 0
                    || state.columns() < 0 || state.columns() > ObserverHorseScreenPayloads.MAX_COLUMNS
                    || state.slots().size() != 38 + 3 * state.columns()) return false;
            for (int index = 0; index < state.slots().size(); index++) {
                var slot = state.slots().get(index);
                if (slot.index() != index || slot.stack().getCount() < 0 || slot.stack().getCount() > 99) return false;
            }
            return true;
        }
    }

    private static void closeObserverScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ObserverHorseInventoryScreen)) return;
        suppressStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressStop = false; }
    }

    private static final class ObserverHorseInventoryScreen extends HorseInventoryScreen implements ObserverReadOnlyScreen {
        private final UUID entityUuid; private final String entityType; private final int columns;
        private ObserverHorseInventoryScreen(HorseInventoryMenu menu, net.minecraft.world.entity.player.Inventory inventory,
                                             AbstractHorse mount, int columns, UUID entityUuid, String entityType) {
            super(menu, inventory, mount, columns);
            this.entityUuid = entityUuid; this.entityType = entityType; this.columns = columns;
        }
        @Override public boolean totem$isObserverReadOnly() { return true; }
        @Override public void onClose() { if (!suppressStop) ObserverVanillaScreenSupport.stopObserving(); }
        @Override public void removed() { }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float tick) {
            super.extractRenderState(graphics, x, y, tick); extractedFrames++;
        }
    }
}
