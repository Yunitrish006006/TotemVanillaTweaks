package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.bookshelf.BookshelfInventoryRule;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class BookshelfInventoryRuleGameTest {
    @GameTest(maxTicks = 20)
    public void survivalBookshelvesBecomeThreeBooksEach(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.getInventory().setItem(0, new ItemStack(Items.BOOKSHELF, 2));

            BookshelfInventoryRule.replaceVanillaBookshelfInInventory(player);

            require(helper, countItem(player, Items.BOOKSHELF) == 0,
                    "The survival inventory still contained an ordinary bookshelf");
            require(helper, countItem(player, Items.BOOK) == 6,
                    "Two ordinary bookshelves did not become exactly six books");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    private static int countItem(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
