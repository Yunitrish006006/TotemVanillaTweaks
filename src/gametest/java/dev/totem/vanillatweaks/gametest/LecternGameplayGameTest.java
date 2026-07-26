package dev.totem.vanillatweaks.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.AssignProfessionFromJobSite;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class LecternGameplayGameTest {
    private static final BlockPos LECTERN_POS = new BlockPos(2, 2, 2);

    @GameTest(maxTicks = 40)
    public void replacementRecipeMatchesWoodFamiliesAndMixedSlabs(GameTestHelper helper) {
        assertCraftsLectern(helper, List.of(Items.OAK_SLAB, Items.OAK_SLAB, Items.OAK_SLAB, Items.OAK_SLAB), "oak slabs");
        assertCraftsLectern(helper, List.of(Items.BAMBOO_SLAB, Items.BAMBOO_SLAB, Items.BAMBOO_SLAB, Items.BAMBOO_SLAB), "bamboo slabs");
        assertCraftsLectern(helper, List.of(Items.CRIMSON_SLAB, Items.CRIMSON_SLAB, Items.CRIMSON_SLAB, Items.CRIMSON_SLAB), "crimson slabs");
        assertCraftsLectern(helper, List.of(Items.WARPED_SLAB, Items.WARPED_SLAB, Items.WARPED_SLAB, Items.WARPED_SLAB), "warped slabs");
        assertCraftsLectern(helper, List.of(Items.OAK_SLAB, Items.BAMBOO_SLAB, Items.CRIMSON_SLAB, Items.WARPED_SLAB), "mixed wooden slabs");

        CraftingInput legacyBookshelfInput = craftingInput(
                List.of(Items.OAK_SLAB, Items.OAK_SLAB, Items.OAK_SLAB, Items.OAK_SLAB),
                Items.BOOKSHELF
        );
        Optional<RecipeHolder<CraftingRecipe>> legacyMatch = helper.getLevel().recipeAccess()
                .getRecipeFor(RecipeType.CRAFTING, legacyBookshelfInput, helper.getLevel());
        require(helper, legacyMatch.isEmpty() || !legacyMatch.get().value().assemble(legacyBookshelfInput).is(Items.LECTERN),
                "The overwritten minecraft:lectern recipe still accepted the legacy bookshelf ingredient");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(maxTicks = 40)
    public void lecternAcceptsBookPulsesRedstoneAndSupportsReadingMenu(GameTestHelper helper) {
        helper.setBlock(LECTERN_POS, Blocks.LECTERN);
        ServerLevel level = helper.getLevel();
        BlockPos absolutePos = helper.absolutePos(LECTERN_POS);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(absolutePos.getX() + 0.5D, absolutePos.getY() + 1.0D, absolutePos.getZ() + 0.5D, 0.0F, 0.0F);

        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(List.of(
                Filterable.passThrough("page one"),
                Filterable.passThrough("page two"),
                Filterable.passThrough("page three")
        )));

        BlockState initialState = level.getBlockState(absolutePos);
        require(helper, LecternBlock.tryPlaceBook(player, level, absolutePos, initialState, book),
                "The lectern rejected a writable book");
        require(helper, level.getBlockState(absolutePos).getValue(LecternBlock.HAS_BOOK),
                "Placing a book did not set LecternBlock.HAS_BOOK");
        require(helper, level.getBlockEntity(absolutePos) instanceof LecternBlockEntity,
                "The placed lectern did not have a LecternBlockEntity");

        LecternBlockEntity lectern = (LecternBlockEntity) level.getBlockEntity(absolutePos);
        require(helper, lectern != null && lectern.hasBook(), "The lectern block entity did not retain the book");
        require(helper, lectern.getBook().get(DataComponents.WRITABLE_BOOK_CONTENT) != null,
                "The lectern did not preserve writable-book page Components");
        require(helper, lectern.getRedstoneSignal() == 1,
                "A three-page book should produce comparator strength 1 on page zero");

        LecternMenu menu = (LecternMenu) lectern.createMenu(1, player.getInventory(), player);
        require(helper, menu != null && menu.getBook().is(Items.WRITABLE_BOOK),
                "The lectern reading menu did not expose the placed book");
        require(helper, menu.clickMenuButton(player, LecternMenu.BUTTON_PAGE_JUMP_RANGE_START + 2),
                "The lectern menu rejected a page jump");
        require(helper, menu.getPage() == 2 && lectern.getPage() == 2,
                "The lectern menu did not update the authoritative page");
        require(helper, lectern.getRedstoneSignal() == 15,
                "The last page of a three-page book should produce comparator strength 15");
        require(helper, level.getBlockState(absolutePos).getValue(LecternBlock.POWERED),
                "Changing page did not create the lectern redstone pulse");
        require(helper, level.getSignal(absolutePos, Direction.NORTH) == 15,
                "The powered lectern did not emit redstone strength 15");

        helper.runAtTickTime(3, () -> {
            try {
                require(helper, !level.getBlockState(absolutePos).getValue(LecternBlock.POWERED),
                        "The lectern redstone pulse remained powered after its scheduled two-tick reset");
                require(helper, level.getSignal(absolutePos, Direction.NORTH) == 0,
                        "The lectern continued emitting direct redstone after the pulse reset");
                require(helper, lectern.getRedstoneSignal() == 15,
                        "Resetting the direct pulse incorrectly changed the comparator page signal");

                require(helper, menu.clickMenuButton(player, LecternMenu.BUTTON_TAKE_BOOK),
                        "The lectern menu rejected taking the book");
                require(helper, !level.getBlockState(absolutePos).getValue(LecternBlock.HAS_BOOK),
                        "Taking the book did not clear LecternBlock.HAS_BOOK");
                require(helper, !lectern.hasBook(), "Taking the book did not clear the block entity book slot");
                require(helper, countItem(player, Items.WRITABLE_BOOK) == 1,
                        "The removed writable book was not returned to the player exactly once");
                helper.succeed();
            } finally {
                player.discard();
            }
        });
    }

    @GameTest(maxTicks = 40)
    public void unemployedVillagerClaimsLecternAndBecomesLibrarian(GameTestHelper helper) {
        helper.setBlock(LECTERN_POS.below(), Blocks.STONE);
        helper.setBlock(LECTERN_POS, Blocks.LECTERN);
        helper.setBlock(LECTERN_POS.west().below(), Blocks.STONE);

        Villager villager = spawnVillager(helper, LECTERN_POS.west());
        require(helper, villager.getVillagerData().profession().is(VillagerProfession.NONE),
                "The fixture villager did not start unemployed");
        ServerLevel level = helper.getLevel();
        BlockPos lecternPos = helper.absolutePos(LECTERN_POS);
        Optional<BlockPos> claimed = level.getPoiManager().take(
                holder -> holder.is(PoiTypes.LIBRARIAN),
                (holder, pos) -> pos.equals(lecternPos),
                villager.blockPosition(),
                4
        );
        require(helper, claimed.isPresent() && claimed.get().equals(lecternPos),
                "The lectern was not registered as an available librarian POI");

        GlobalPos jobSite = GlobalPos.of(level.dimension(), lecternPos);
        villager.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, jobSite);
        require(helper, AssignProfessionFromJobSite.create().tryStart(level, villager, level.getGameTime()),
                "Vanilla profession assignment rejected the claimed lectern POI");
        require(helper, villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                        .filter(jobSite::equals)
                        .isPresent(),
                "The villager did not retain the lectern as its job site");
        require(helper, villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN),
                "The villager did not become a librarian after claiming the lectern");
        helper.succeed();
    }

    private static void assertCraftsLectern(GameTestHelper helper, List<Item> slabs, String description) {
        CraftingInput input = craftingInput(slabs, Items.BOOK);
        Optional<RecipeHolder<CraftingRecipe>> match = helper.getLevel().recipeAccess()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        require(helper, match.isPresent(), "No crafting recipe matched " + description);
        RecipeHolder<CraftingRecipe> recipe = match.get();
        require(helper, "minecraft:lectern".equals(recipe.id().identifier().toString()),
                "The matching recipe for " + description + " was " + recipe.id().identifier());
        ItemStack result = recipe.value().assemble(input);
        require(helper, result.is(Items.LECTERN) && result.getCount() == 1,
                "The recipe for " + description + " did not produce exactly one lectern");
    }

    private static CraftingInput craftingInput(List<Item> slabs, Item centerIngredient) {
        if (slabs.size() != 4) {
            throw new IllegalArgumentException("Expected exactly four slab items");
        }
        List<ItemStack> slots = new ArrayList<>(List.of(
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
        ));
        slots.set(0, new ItemStack(slabs.get(0)));
        slots.set(1, new ItemStack(slabs.get(1)));
        slots.set(2, new ItemStack(slabs.get(2)));
        slots.set(4, new ItemStack(centerIngredient));
        slots.set(7, new ItemStack(slabs.get(3)));
        return CraftingInput.of(3, 3, slots);
    }

    @SuppressWarnings("unchecked")
    private static Villager spawnVillager(GameTestHelper helper, BlockPos relativePos) {
        EntityType<?> rawType = BuiltInRegistries.ENTITY_TYPE.getValue(
                Identifier.fromNamespaceAndPath("minecraft", "villager")
        );
        require(helper, rawType != null, "Missing minecraft:villager entity type");
        return helper.spawn((EntityType<Villager>) rawType, relativePos);
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
