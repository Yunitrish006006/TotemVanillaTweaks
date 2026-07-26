package dev.totem.vanillatweaks.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Method;

/** Ensures hopper extraction consumes a furnace's stored recipe experience. */
public final class HopperFurnaceExperienceGameTest {
    private static final BlockPos HOPPER_POS = new BlockPos(2, 2, 2);

    @GameTest(maxTicks = 80)
    public void hopperExtractingFurnaceOutputSpawnsStoredExperience(GameTestHelper helper) {
        helper.setBlock(HOPPER_POS, Blocks.HOPPER);
        helper.setBlock(HOPPER_POS.above(), Blocks.FURNACE);

        ServerLevel level = helper.getLevel();
        Object blockEntity = level.getBlockEntity(helper.absolutePos(HOPPER_POS.above()));
        if (!(blockEntity instanceof AbstractFurnaceBlockEntity furnace)) {
            throw helper.assertionException("Furnace fixture did not create an AbstractFurnaceBlockEntity");
        }
        Object hopperEntity = level.getBlockEntity(helper.absolutePos(HOPPER_POS));
        if (!(hopperEntity instanceof HopperBlockEntity hopper)) {
            throw helper.assertionException("Hopper fixture did not create a HopperBlockEntity");
        }

        RecipeHolder<?> clayRecipe = level.recipeAccess()
                .getRecipeFor(
                        RecipeType.SMELTING,
                        new SingleRecipeInput(new ItemStack(Items.CLAY_BALL)),
                        level
                )
                .orElseThrow(() -> helper.assertionException("Missing clay smelting recipe"));
        furnace.setItem(2, new ItemStack(Items.BRICK, 64));
        for (int index = 0; index < 64; index++) {
            furnace.setRecipeUsed(clayRecipe);
        }

        require(helper, extractFurnaceResult(hopper, furnace), "Hopper did not extract the furnace output");
        require(helper, furnace.getItem(2).getCount() == 63, "Hopper extraction changed the furnace result by more than one item");
        AABB searchArea = new AABB(helper.absolutePos(HOPPER_POS)).inflate(3.0D);
        int experience = level.getEntitiesOfClass(ExperienceOrb.class, searchArea).stream()
                .mapToInt(ExperienceOrb::getValue)
                .sum();
        require(helper, experience > 0, "Hopper extraction did not spawn stored furnace experience");
        helper.succeed();
    }

    private static boolean extractFurnaceResult(Hopper hopper, AbstractFurnaceBlockEntity furnace) {
        try {
            Method method = HopperBlockEntity.class.getDeclaredMethod(
                    "tryTakeInItemFromSlot",
                    Hopper.class,
                    Container.class,
                    int.class,
                    Direction.class
            );
            method.setAccessible(true);
            return (boolean) method.invoke(null, hopper, furnace, 2, Direction.DOWN);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not invoke the vanilla hopper extraction path", exception);
        }
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
