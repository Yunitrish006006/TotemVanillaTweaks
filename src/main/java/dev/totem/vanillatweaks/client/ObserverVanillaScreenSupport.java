package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Small shared mechanics for genuine vanilla observer Screen/Menu instances. */
final class ObserverVanillaScreenSupport {
    private ObserverVanillaScreenSupport() { }

    static Inventory detachedInventory() {
        var player = Minecraft.getInstance().player;
        if (player == null) throw new IllegalStateException("Observer player is unavailable");
        return new Inventory(player, new EntityEquipment());
    }

    static void applyMenu(AbstractContainerMenu menu, List<ObserverNativeScreenPayloads.SlotState> remoteSlots) {
        List<ItemStack> items = new ArrayList<>(menu.slots.size());
        for (int i = 0; i < menu.slots.size(); i++) items.add(ItemStack.EMPTY);
        for (ObserverNativeScreenPayloads.SlotState slot : remoteSlots) {
            if (slot.index() >= 0 && slot.index() < items.size()) {
                items.set(slot.index(), ObserverNativeScreenClient.itemStack(slot));
            }
        }
        menu.initializeContents(0, items, menu.getCarried());
    }

    static void stopObserving() {
        if (ObserverNativeClient.observerSessionActive()) ClientPlayNetworking.send(new ObserverPayloads.Stop());
    }
}
