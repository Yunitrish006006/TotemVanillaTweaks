package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.inventory.ContainerSortService;
import dev.totem.vanillatweaks.network.SortBackpackPayload;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ContainerSortGameTest {
    @GameTest(maxTicks = 40)
    public void containerSideSortsByIdentifierAndCompactsMatchingStacks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SimpleContainer container = new SimpleContainer(27);
        container.setItem(0, new ItemStack(Items.DIRT, 32));
        container.setItem(1, new ItemStack(Items.APPLE, 1));
        container.setItem(2, new ItemStack(Items.DIRT, 40));
        container.setItem(3, new ItemStack(Items.DIAMOND, 2));
        player.containerMenu = ChestMenu.threeRows(1, player.getInventory(), container);

        try {
            require(helper, ContainerSortService.sortOpenContainer(
                    player,
                    SortBackpackPayload.Target.CONTAINER
            ), "Container sorting reported no change");
            assertStack(helper, container.getItem(0), Items.APPLE, 1, "first sorted slot");
            assertStack(helper, container.getItem(1), Items.DIAMOND, 2, "second sorted slot");
            assertStack(helper, container.getItem(2), Items.DIRT, 64, "first compacted dirt stack");
            assertStack(helper, container.getItem(3), Items.DIRT, 8, "second compacted dirt stack");
            require(helper, container.getItem(4).isEmpty(), "Sorting did not clear trailing slots");
            helper.succeed();
        } finally {
            player.closeContainer();
            player.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void playerSideSortsMainInventoryWithoutChangingHotbar(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SimpleContainer container = new SimpleContainer(27);
        player.containerMenu = ChestMenu.threeRows(2, player.getInventory(), container);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        player.getInventory().setItem(9, new ItemStack(Items.DIRT, 32));
        player.getInventory().setItem(10, new ItemStack(Items.APPLE, 1));
        player.getInventory().setItem(11, new ItemStack(Items.DIRT, 40));

        try {
            require(helper, ContainerSortService.sortOpenContainer(
                    player,
                    SortBackpackPayload.Target.PLAYER
            ), "Player inventory sorting reported no change");
            assertStack(helper, player.getInventory().getItem(0), Items.DIAMOND, 3, "hotbar slot");
            assertStack(helper, player.getInventory().getItem(9), Items.APPLE, 1, "first main inventory slot");
            assertStack(helper, player.getInventory().getItem(10), Items.DIRT, 64, "first compacted dirt stack");
            assertStack(helper, player.getInventory().getItem(11), Items.DIRT, 8, "second compacted dirt stack");
            require(helper, container.isEmpty(), "Player-side sorting modified the open container");
            helper.succeed();
        } finally {
            player.closeContainer();
            player.discard();
        }
    }

    private static void assertStack(
            GameTestHelper helper,
            ItemStack stack,
            Item item,
            int count,
            String description
    ) {
        require(helper, stack.is(item) && stack.getCount() == count,
                description + " was " + stack + " instead of " + count + " " + item);
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
