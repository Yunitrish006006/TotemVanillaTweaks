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
