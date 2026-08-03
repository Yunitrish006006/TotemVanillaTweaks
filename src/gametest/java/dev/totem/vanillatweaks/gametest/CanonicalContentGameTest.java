package dev.totem.vanillatweaks.gametest;

import dev.totem.vanillatweaks.content.VanillaTweaksContent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;

/** Covers canonical Totem content ownership and its transferred gameplay values. */
public final class CanonicalContentGameTest {
    @GameTest(maxTicks = 40)
    public void retainedContentUsesCanonicalTotemRegistrations(GameTestHelper helper) {
        Item canonicalPufferfish = item("totem:vanilla_tweaks/cooked_pufferfish");
        Item canonicalRottenFlesh = item("totem:vanilla_tweaks/smoked_rotten_flesh");
        Item canonicalOre = item("totem:vanilla_tweaks/gravel_iron_ore");

        assertFood(helper, canonicalPufferfish, 0.4F, "canonical cooked pufferfish");
        assertFood(helper, canonicalRottenFlesh, 0.1F, "canonical smoked rotten flesh");
        require(helper, canonicalOre != Items.AIR, "Canonical gravel iron ore is not registered");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void canonicalCookingRecipesPreserveTransferredValues(GameTestHelper helper) {
        assertCookingRecipe(
                helper,
                "totem:vanilla_tweaks/cooked_pufferfish_from_smoker",
                Items.PUFFERFISH,
                item("totem:vanilla_tweaks/cooked_pufferfish"),
                0.35F,
                100
        );
        assertCookingRecipe(
                helper,
                "totem:vanilla_tweaks/smoked_rotten_flesh_from_smoker",
                Items.ROTTEN_FLESH,
                item("totem:vanilla_tweaks/smoked_rotten_flesh"),
                0.35F,
                100
        );
        assertCookingRecipe(
                helper,
                "totem:vanilla_tweaks/iron_ingot_from_smelting_gravel_iron_ore",
                item("totem:vanilla_tweaks/gravel_iron_ore"),
                Items.IRON_INGOT,
                0.25F,
                200
        );
        assertCookingRecipe(
                helper,
                "totem:vanilla_tweaks/iron_ingot_from_blasting_gravel_iron_ore",
                item("totem:vanilla_tweaks/gravel_iron_ore"),
                Items.IRON_INGOT,
                0.25F,
                100
        );
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void gravelIronOreLootTableReturnsRawIronForTheCorrectTool(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos relativePos = new BlockPos(1, 2, 1);
        BlockPos absolutePos = helper.absolutePos(relativePos);
        helper.setBlock(relativePos, VanillaTweaksContent.GRAVEL_IRON_ORE);

        try {
            player.snapTo(absolutePos.getX() + 0.5D, absolutePos.getY() + 1.0D, absolutePos.getZ() + 0.5D, 0.0F, 0.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
            require(helper, player.getMainHandItem().isCorrectToolForDrops(helper.getLevel().getBlockState(absolutePos)),
                    "An iron shovel was not recognized as the correct tool for canonical gravel iron ore");
            require(helper, Block.getDrops(
                    helper.getLevel().getBlockState(absolutePos),
                    helper.getLevel(),
                    absolutePos,
                    null,
                    player,
                    player.getMainHandItem()
            ).stream().anyMatch(drop -> drop.is(Items.RAW_IRON) && drop.getCount() >= 1),
                    "Canonical gravel iron ore did not return raw iron from its mining loot table");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    private static void assertFood(GameTestHelper helper, Item item, float expectedSaturationModifier, String description) {
        FoodProperties food = new ItemStack(item).get(DataComponents.FOOD);
        FoodProperties expected = new FoodProperties.Builder()
                .nutrition(2)
                .saturationModifier(expectedSaturationModifier)
                .build();
        require(helper, food != null && food.nutrition() == 2,
                description + " did not preserve two hunger points");
        require(helper, food != null && Float.compare(food.saturation(), expected.saturation()) == 0,
                description + " did not preserve saturation modifier " + expectedSaturationModifier);
    }

    private static void assertCookingRecipe(
            GameTestHelper helper,
            String id,
            Item ingredient,
            Item expectedResult,
            float expectedExperience,
            int expectedCookingTime
    ) {
        RecipeHolder<?> holder = helper.getLevel().recipeAccess()
                .byKey(ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.parse(id)))
                .orElseThrow(() -> helper.assertionException("Missing recipe " + id));
        require(helper, holder.value() instanceof AbstractCookingRecipe,
                "Recipe " + id + " is not a cooking recipe");
        AbstractCookingRecipe recipe = (AbstractCookingRecipe) holder.value();
        SingleRecipeInput input = new SingleRecipeInput(new ItemStack(ingredient));
        require(helper, recipe.matches(input, helper.getLevel()), "Recipe " + id + " did not accept its source item");
        require(helper, recipe.assemble(input).is(expectedResult), "Recipe " + id + " did not emit the expected result");
        require(helper, Float.compare(recipe.experience(), expectedExperience) == 0,
                "Recipe " + id + " changed its experience value");
        require(helper, recipe.cookingTime() == expectedCookingTime,
                "Recipe " + id + " changed its cooking time");
    }

    private static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        if (item == null) {
            throw new IllegalStateException("Missing item " + id);
        }
        return item;
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
