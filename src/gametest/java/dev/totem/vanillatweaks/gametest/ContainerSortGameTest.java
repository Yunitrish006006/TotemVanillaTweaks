package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.inventory.ContainerSortService;
import dev.totem.vanillatweaks.network.SortBackpackPayload;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
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
        ItemStack namedApple = new ItemStack(Items.APPLE, 1);
        namedApple.set(DataComponents.CUSTOM_NAME, Component.literal("Keep player components"));
        player.getInventory().setItem(10, namedApple.copy());
        player.getInventory().setItem(11, new ItemStack(Items.DIRT, 40));

        try {
            require(helper, ContainerSortService.sortOpenContainer(
                    player,
                    SortBackpackPayload.Target.PLAYER
            ), "Player inventory sorting reported no change");
            assertStack(helper, player.getInventory().getItem(0), Items.DIAMOND, 3, "hotbar slot");
            assertStack(helper, player.getInventory().getItem(9), Items.APPLE, 1, "first main inventory slot");
            require(helper, ItemStack.matches(namedApple, player.getInventory().getItem(9)),
                    "Player sorting did not preserve item components");
            assertStack(helper, player.getInventory().getItem(10), Items.DIRT, 64, "first compacted dirt stack");
            assertStack(helper, player.getInventory().getItem(11), Items.DIRT, 8, "second compacted dirt stack");
            require(helper, container.isEmpty(), "Player-side sorting modified the open container");
            helper.succeed();
        } finally {
            player.closeContainer();
            player.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void craftingRejectionPreservesRecipeAndNormalIngredientConsumption(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CraftingMenu menu = new CraftingMenu(3, player.getInventory());
        player.containerMenu = menu;
        try {
            menu.getInputGridSlots().getFirst().setByPlayer(new ItemStack(Items.OAK_LOG, 2));
            // The NULL level access used by this isolated menu does not recompute recipes.
            // Finish placement runs the real server recipe lookup and result calculation.
            menu.finishPlacingRecipe(helper.getLevel(), null);
            assertStack(helper, menu.getResultSlot().getItem(), Items.OAK_PLANKS, 4, "recipe preview");
            List<ItemStack> before = snapshot(menu);
            require(helper, !ContainerSortService.sortOpenContainer(player, SortBackpackPayload.Target.CONTAINER),
                    "Crafting menu allowed container sorting");
            assertUnchanged(helper, menu, before);
            player.getInventory().setItem(9, new ItemStack(Items.DIRT));
            player.getInventory().setItem(10, new ItemStack(Items.APPLE));
            require(helper, ContainerSortService.sortOpenContainer(player, SortBackpackPayload.Target.PLAYER),
                    "Workbench player inventory sorting was rejected");
            assertStack(helper, menu.getInputGridSlots().getFirst().getItem(), Items.OAK_LOG, 2, "recipe input");
            assertStack(helper, menu.getResultSlot().getItem(), Items.OAK_PLANKS, 4, "recipe preview after player sort");
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            assertStack(helper, menu.getCarried(), Items.OAK_PLANKS, 4, "crafted output");
            assertStack(helper, menu.getInputGridSlots().getFirst().getItem(), Items.OAK_LOG, 1, "consumed ingredient");
            helper.succeed();
        } finally {
            player.closeContainer();
            player.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void functionalMenusRejectWithoutMovingInputsOrResults(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            for (AbstractContainerMenu menu : List.of(player.inventoryMenu,
                    new FurnaceMenu(4, player.getInventory()), new AnvilMenu(5, player.getInventory()))) {
                player.containerMenu = menu;
                menu.getSlot(0).setByPlayer(new ItemStack(Items.IRON_INGOT, 3));
                menu.getSlot(1).setByPlayer(new ItemStack(Items.COAL, 2));
                List<ItemStack> before = snapshot(menu);
                require(helper, !ContainerSortService.sortOpenContainer(player, SortBackpackPayload.Target.CONTAINER),
                        "Functional menu allowed container sorting: " + menu.getClass());
                assertUnchanged(helper, menu, before);
                player.closeContainer();
            }
            helper.succeed();
        } finally {
            player.closeContainer();
            player.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void storageSortPreservesDistinctComponentsAndCounts(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SimpleContainer container = new SimpleContainer(27);
        ItemStack named = new ItemStack(Items.DIRT, 7);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep me"));
        container.setItem(0, named.copy());
        container.setItem(1, new ItemStack(Items.DIRT, 3));
        container.setItem(2, new ItemStack(Items.APPLE, 2));
        player.containerMenu = ChestMenu.threeRows(6, player.getInventory(), container);
        try {
            require(helper, ContainerSortService.sortOpenContainer(player, SortBackpackPayload.Target.CONTAINER),
                    "Storage sorting failed");
            assertStack(helper, container.getItem(0), Items.APPLE, 2, "sorted apple");
            require(helper, ItemStack.matches(named, container.getItem(1)), "Named stack changed or merged");
            assertStack(helper, container.getItem(2), Items.DIRT, 3, "unnamed dirt");
            helper.succeed();
        } finally {
            player.closeContainer();
            player.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void restrictedDestinationsRejectBeforeAnyWrite(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            for (int restriction = 0; restriction < 5; restriction++) {
                final int mode = restriction;
                SimpleContainer container = new SimpleContainer(27) {
                    @Override
                    public boolean canPlaceItem(int slot, ItemStack stack) {
                        return mode != 0 || slot != 1 || !stack.is(Items.DIRT);
                    }
                };
                container.setItem(0, new ItemStack(Items.DIRT, 10));
                container.setItem(1, new ItemStack(Items.APPLE, 1));
                ChestMenu menu = ChestMenu.threeRows(7, player.getInventory(), container);
                if (mode != 0) {
                    menu.slots.set(1, new Slot(container, 1, 0, 0) {
                        @Override public boolean mayPlace(ItemStack stack) { return mode != 1; }
                        @Override public boolean mayPickup(Player owner) { return mode != 2; }
                        @Override public boolean isActive() { return mode != 3; }
                        @Override public int getMaxStackSize(ItemStack stack) { return mode == 4 ? 1 : super.getMaxStackSize(stack); }
                    });
                }
                player.containerMenu = menu;
                List<ItemStack> before = snapshot(menu);
                require(helper, !ContainerSortService.sortOpenContainer(player, SortBackpackPayload.Target.CONTAINER),
                        "Restricted destination accepted sorting: " + mode);
                assertUnchanged(helper, menu, before);
                player.closeContainer();
            }
            helper.succeed();
        } finally {
            player.closeContainer();
            player.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void invalidAndCustomMenusRejectBothTargets(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            SimpleContainer invalid = new SimpleContainer(27) {
                @Override public boolean stillValid(Player owner) { return false; }
            };
            SimpleContainer custom = new SimpleContainer(27);
            for (AbstractContainerMenu menu : List.of(ChestMenu.threeRows(8, player.getInventory(), invalid),
                    new ChestMenu(MenuType.GENERIC_9x3, 9, player.getInventory(), custom, 3) {})) {
                player.containerMenu = menu;
                menu.getSlot(0).setByPlayer(new ItemStack(Items.DIRT, 3));
                menu.getSlot(1).setByPlayer(new ItemStack(Items.APPLE, 2));
                ItemStack tracked = new ItemStack(Items.LEATHER, 1);
                player.getInventory().setItem(9, tracked);
                player.getInventory().setItem(10, new ItemStack(Items.APPLE));
                List<ItemStack> before = snapshot(menu);
                for (SortBackpackPayload.Target target : SortBackpackPayload.Target.values()) {
                    require(helper, !ContainerSortService.sortOpenContainer(player, target),
                            "Invalid/custom menu accepted " + target);
                    assertUnchanged(helper, menu, before);
                    require(helper, player.getInventory().getItem(9) == tracked,
                            "Sorting replaced the stack tracked by a custom menu");
                }
                player.closeContainer();
            }
            helper.succeed();
        } finally {
            player.closeContainer();
            player.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void supportedStorageFamiliesSortAndKeepCarriedStack(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            for (AbstractContainerMenu menu : List.of(new HopperMenu(10, player.getInventory()),
                    new DispenserMenu(11, player.getInventory()), new ShulkerBoxMenu(12, player.getInventory()))) {
                player.containerMenu = menu;
                menu.getSlot(0).setByPlayer(new ItemStack(Items.DIRT, 32));
                menu.getSlot(1).setByPlayer(new ItemStack(Items.APPLE, 2));
                menu.getSlot(2).setByPlayer(new ItemStack(Items.DIRT, 40));
                ItemStack carried = new ItemStack(Items.DIAMOND, 3);
                menu.setCarried(carried);
                require(helper, ContainerSortService.sortOpenContainer(player, SortBackpackPayload.Target.CONTAINER),
                        "Supported storage rejected sorting: " + menu.getClass());
                assertStack(helper, menu.getSlot(0).getItem(), Items.APPLE, 2, "storage apple");
                assertStack(helper, menu.getSlot(1).getItem(), Items.DIRT, 64, "storage merged dirt");
                assertStack(helper, menu.getSlot(2).getItem(), Items.DIRT, 8, "storage remaining dirt");
                require(helper, menu.getCarried() == carried && carried.getCount() == 3,
                        "Sorting modified the carried stack");
                menu.setCarried(ItemStack.EMPTY);
                player.closeContainer();
            }
            helper.succeed();
        } finally {
            player.closeContainer();
            player.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void vanillaPlayerSortingPreservesFunctionalSlotsAndCarriedStack(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            for (AbstractContainerMenu menu : List.of(player.inventoryMenu,
                    new FurnaceMenu(13, player.getInventory()), new AnvilMenu(14, player.getInventory()))) {
                player.containerMenu = menu;
                menu.getSlot(1).setByPlayer(new ItemStack(Items.IRON_INGOT, 3));
                List<ItemStack> before = snapshot(menu);
                player.getInventory().setItem(9, new ItemStack(Items.DIRT, 32));
                player.getInventory().setItem(10, new ItemStack(Items.APPLE, 2));
                player.getInventory().setItem(11, new ItemStack(Items.DIRT, 40));
                ItemStack carried = new ItemStack(Items.DIAMOND, 3);
                menu.setCarried(carried);
                require(helper, ContainerSortService.sortOpenContainer(player, SortBackpackPayload.Target.PLAYER),
                        "Vanilla player sorting rejected: " + menu.getClass());
                assertStack(helper, player.getInventory().getItem(9), Items.APPLE, 2, "player apple");
                assertStack(helper, player.getInventory().getItem(10), Items.DIRT, 64, "player merged dirt");
                assertStack(helper, player.getInventory().getItem(11), Items.DIRT, 8, "player remaining dirt");
                for (int i = 0; i < menu.slots.size(); i++) {
                    if (menu.getSlot(i).container != player.getInventory()) {
                        require(helper, ItemStack.matches(before.get(i), menu.getSlot(i).getItem()),
                                "Player sorting changed functional slot " + i);
                    }
                }
                require(helper, menu.getCarried() == carried && carried.getCount() == 3,
                        "Player sorting modified the carried stack");
                menu.setCarried(ItemStack.EMPTY);
                player.getInventory().clearContent();
                player.closeContainer();
            }
            helper.succeed();
        } finally {
            player.closeContainer();
            player.discard();
        }
    }

    private static List<ItemStack> snapshot(AbstractContainerMenu menu) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Slot slot : menu.slots) {
            stacks.add(slot.getItem().copy());
        }
        return stacks;
    }

    private static void assertUnchanged(GameTestHelper helper, AbstractContainerMenu menu, List<ItemStack> before) {
        for (int i = 0; i < before.size(); i++) {
            require(helper, ItemStack.matches(before.get(i), menu.getSlot(i).getItem()), "Rejection changed slot " + i);
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
