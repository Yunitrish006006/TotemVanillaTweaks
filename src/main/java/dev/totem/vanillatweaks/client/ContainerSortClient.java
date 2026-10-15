package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.network.SortBackpackPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

public final class ContainerSortClient {
    private ContainerSortClient() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean requestSortForScreen(AbstractContainerScreen<?> screen, Slot hoveredSlot) {
        if (screen instanceof dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen readOnly
                && readOnly.totem$isObserverReadOnly()) return true;
        for (java.util.function.BiFunction provider : net.fabricmc.loader.api.FabricLoader.getInstance()
                .getEntrypoints("totem:container_sort_client_v1", java.util.function.BiFunction.class)) {
            if (Boolean.TRUE.equals(provider.apply(screen, hoveredSlot))) return true;
        }
        var target = resolveTarget(screen, hoveredSlot, Minecraft.getInstance());
        if (target == null) return false;
        requestSort(Minecraft.getInstance(), target);
        return true;
    }

    public static void requestSort(Minecraft minecraft, SortBackpackPayload.Target target) {
        if (minecraft.player != null && ClientPlayNetworking.canSend(SortBackpackPayload.TYPE)) {
            ClientPlayNetworking.send(new SortBackpackPayload(target));
        }
    }

    public static SortBackpackPayload.Target resolveTarget(
            AbstractContainerScreen<?> screen,
            Slot hoveredSlot,
            Minecraft minecraft
    ) {
        if (screen.getMenu() instanceof InventoryMenu) {
            return SortBackpackPayload.Target.PLAYER;
        }
        if (hoveredSlot == null) {
            return null;
        }
        if (minecraft.player != null && hoveredSlot.container == minecraft.player.getInventory()) {
            return SortBackpackPayload.Target.PLAYER;
        }
        return SortBackpackPayload.Target.CONTAINER;
    }
}
