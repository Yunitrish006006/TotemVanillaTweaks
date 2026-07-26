package dev.totem.vanillatweaks.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public final class ConcretePowderItemHardening {
    private static final Map<Item, Item> HARDENED_ITEMS = Map.ofEntries(
            mapping("white"),
            mapping("light_gray"),
            mapping("gray"),
            mapping("black"),
            mapping("brown"),
            mapping("red"),
            mapping("orange"),
            mapping("yellow"),
            mapping("lime"),
            mapping("green"),
            mapping("cyan"),
            mapping("light_blue"),
            mapping("blue"),
            mapping("purple"),
            mapping("magenta"),
            mapping("pink")
    );

    private ConcretePowderItemHardening() {
    }

    public static Item hardenedItem(Item powderItem) {
        return HARDENED_ITEMS.get(powderItem);
    }

    public static ItemStack harden(ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }

        Item hardenedItem = hardenedItem(stack.getItem());
        if (hardenedItem == null) {
            return stack;
        }
        return stack.transmuteCopy(hardenedItem, stack.getCount());
    }

    public static boolean tryHarden(ItemEntity itemEntity) {
        if (!(itemEntity.level() instanceof ServerLevel)) {
            return false;
        }

        ItemStack current = itemEntity.getItem();
        if (current.isEmpty()) {
            return false;
        }

        Item hardenedItem = hardenedItem(current.getItem());
        if (hardenedItem == null || !itemEntity.isInWater()) {
            return false;
        }

        itemEntity.setItem(current.transmuteCopy(hardenedItem, current.getCount()));
        return true;
    }

    private static Map.Entry<Item, Item> mapping(String color) {
        return Map.entry(
                vanillaItem(color + "_concrete_powder"),
                vanillaItem(color + "_concrete")
        );
    }

    private static Item vanillaItem(String path) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:" + path));
        if (item == null) {
            throw new IllegalStateException("Missing vanilla item minecraft:" + path);
        }
        return item;
    }
}
