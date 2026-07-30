package dev.totem.vanillatweaks.bookshelf;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Keeps ordinary bookshelves out of survival inventories while preserving their three books. */
public final class BookshelfInventoryRule {
    private static final int REPLACE_INTERVAL_TICKS = 20;
    private static int replaceTicker;

    private BookshelfInventoryRule() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            replaceTicker++;
            if (replaceTicker < REPLACE_INTERVAL_TICKS) {
                return;
            }
            replaceTicker = 0;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.getAbilities().instabuild) {
                    replaceVanillaBookshelfInInventory(player);
                }
            }
        });
    }

    public static void replaceVanillaBookshelfInInventory(ServerPlayer player) {
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(Items.BOOKSHELF)) {
                continue;
            }

            int booksToGive = stack.getCount() * 3;
            player.getInventory().setItem(slot, ItemStack.EMPTY);
            while (booksToGive > 0) {
                int batch = Math.min(booksToGive, Items.BOOK.getDefaultMaxStackSize());
                ItemStack books = new ItemStack(Items.BOOK, batch);
                if (!player.getInventory().add(books)) {
                    player.drop(books, false);
                }
                booksToGive -= batch;
            }
            changed = true;
        }

        if (changed) {
            player.getInventory().setChanged();
        }
    }
}
