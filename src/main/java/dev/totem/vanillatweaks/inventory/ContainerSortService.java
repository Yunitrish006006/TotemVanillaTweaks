package dev.totem.vanillatweaks.inventory;

import dev.totem.vanillatweaks.network.SortBackpackPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Server-authoritative sorting for the container or player side of an open menu. */
public final class ContainerSortService {
    private static final int PLAYER_HOTBAR_SLOT_COUNT = 9;
    private static final Set<Class<?>> PLAYER_SORT_MENUS = Set.of(
            InventoryMenu.class, CraftingMenu.class, FurnaceMenu.class, BlastFurnaceMenu.class,
            SmokerMenu.class, AnvilMenu.class, MerchantMenu.class, BrewingStandMenu.class,
            BeaconMenu.class, CartographyTableMenu.class, CrafterMenu.class, EnchantmentMenu.class,
            GrindstoneMenu.class, HorseInventoryMenu.class, LoomMenu.class, SmithingMenu.class,
            StonecutterMenu.class
    );

    private ContainerSortService() {
    }

    public static boolean sortOpenContainer(ServerPlayer player, SortBackpackPayload.Target target) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || !menu.stillValid(player)) {
            return false;
        }

        if (target == SortBackpackPayload.Target.PLAYER) {
            return sortPlayerInventorySlots(menu, player);
        }

        // Slot layout alone cannot distinguish storage from recipe inputs/results, or
        // custom containers whose setItem serializes/compacts the entire inventory.
        if (!isStorageMenu(menu)) {
            return false;
        }

        int topSlotCount = findTopSlotCount(menu, player);
        if (topSlotCount <= 0) {
            return false;
        }

        boolean sorted = sortSlotRange(menu, player, 0, topSlotCount);
        if (sorted) {
            menu.broadcastChanges();
        }
        return sorted;
    }

    private static boolean sortPlayerInventorySlots(AbstractContainerMenu menu, ServerPlayer player) {
        // A custom menu may track an inventory ItemStack (e.g. an open backpack).
        // Replacing that stack would detach its persisted contents from the menu.
        if (!isStorageMenu(menu) && !PLAYER_SORT_MENUS.contains(menu.getClass())) {
            return false;
        }
        List<Integer> playerSlots = new ArrayList<>();
        int nonEquipmentSlotCount = player.getInventory().getNonEquipmentItems().size();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int containerSlot = slot.getContainerSlot();
            if (slot.container == player.getInventory()
                    && containerSlot >= PLAYER_HOTBAR_SLOT_COUNT
                    && containerSlot < nonEquipmentSlotCount) {
                playerSlots.add(i);
            }
        }

        if (playerSlots.isEmpty()) {
            return false;
        }

        List<ItemStack> stacks = new ArrayList<>(playerSlots.size());
        for (int slotIndex : playerSlots) {
            ItemStack stack = menu.getSlot(slotIndex).getItem();
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }

        if (stacks.isEmpty()) {
            return false;
        }

        if (!applySortedStacks(menu, player, playerSlots, stacks)) {
            return false;
        }
        menu.broadcastChanges();
        return true;
    }

    private static boolean isStorageMenu(AbstractContainerMenu menu) {
        Class<?> type = menu.getClass();
        return type == ChestMenu.class || type == HopperMenu.class
                || type == DispenserMenu.class || type == ShulkerBoxMenu.class;
    }

    private static int findTopSlotCount(AbstractContainerMenu menu, ServerPlayer player) {
        int count = 0;
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory()) {
                break;
            }
            count++;
        }
        return count;
    }

    private static boolean sortSlotRange(AbstractContainerMenu menu, ServerPlayer player, int startInclusive, int endExclusive) {
        List<Integer> slotIndexes = new ArrayList<>();
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = startInclusive; i < endExclusive; i++) {
            slotIndexes.add(i);
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }

        if (stacks.isEmpty()) {
            return false;
        }

        return applySortedStacks(menu, player, slotIndexes, stacks);
    }

    private static boolean applySortedStacks(
            AbstractContainerMenu menu,
            ServerPlayer player,
            List<Integer> targetSlots,
            List<ItemStack> stacks
    ) {
        stacks.sort((left, right) -> {
            String leftId = BuiltInRegistries.ITEM.getKey(left.getItem()).toString();
            String rightId = BuiltInRegistries.ITEM.getKey(right.getItem()).toString();
            int compare = leftId.compareTo(rightId);
            if (compare != 0) {
                return compare;
            }
            return Integer.compare(right.getCount(), left.getCount());
        });

        List<ItemStack> compacted = compactStacks(stacks);
        if (compacted.size() > targetSlots.size()) {
            return false;
        }
        // Validate the entire proposal before the first write. A late rejection must
        // never leave half the inventory overwritten or bypass a restricted slot.
        for (int i = 0; i < targetSlots.size(); i++) {
            Slot slot = menu.getSlot(targetSlots.get(i));
            ItemStack proposed = i < compacted.size() ? compacted.get(i) : ItemStack.EMPTY;
            if (!slot.isActive() || !slot.mayPickup(player)) {
                return false;
            }
            if (!proposed.isEmpty() && (!slot.mayPlace(proposed)
                    || !slot.container.canPlaceItem(slot.getContainerSlot(), proposed)
                    || proposed.getCount() > slot.getMaxStackSize(proposed)
                    || proposed.getCount() > slot.container.getMaxStackSize(proposed))) {
                return false;
            }
        }
        for (int i = 0; i < targetSlots.size(); i++) {
            ItemStack stack = i < compacted.size() ? compacted.get(i).copy() : ItemStack.EMPTY;
            menu.getSlot(targetSlots.get(i)).setByPlayer(stack);
        }
        return true;
    }

    private static List<ItemStack> compactStacks(List<ItemStack> stacks) {
        List<ItemStack> compacted = new ArrayList<>();
        for (ItemStack stack : stacks) {
            ItemStack remaining = stack.copy();
            if (!compacted.isEmpty()) {
                ItemStack last = compacted.get(compacted.size() - 1);
                if (ItemStack.isSameItemSameComponents(last, remaining)) {
                    int movable = Math.min(last.getMaxStackSize() - last.getCount(), remaining.getCount());
                    if (movable > 0) {
                        last.grow(movable);
                        remaining.shrink(movable);
                    }
                }
            }
            while (!remaining.isEmpty()) {
                int take = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                ItemStack next = remaining.copy();
                next.setCount(take);
                compacted.add(next);
                remaining.shrink(take);
            }
        }
        return compacted;
    }
}
