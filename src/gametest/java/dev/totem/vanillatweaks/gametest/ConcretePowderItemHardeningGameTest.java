package dev.totem.vanillatweaks.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ConcretePowderItemHardeningGameTest {
    private static final BlockPos ITEM_POS = new BlockPos(2, 2, 2);
    private static final Component TEST_NAME = Component.literal("DeadRecall concrete powder GameTest");
    private static final int STRESS_ENTITY_COUNT = 512;

    @GameTest(maxTicks = 40)
    public void sourceWaterHardensAndPreservesEntityState(GameTestHelper helper) {
        helper.setBlock(ITEM_POS.below(), Blocks.STONE);
        helper.setBlock(ITEM_POS, Blocks.WATER);

        ItemStack powder = new ItemStack(item("red_concrete_powder"), 64);
        powder.set(DataComponents.CUSTOM_NAME, TEST_NAME);

        Vec3 initialVelocity = new Vec3(0.04, 0.01, -0.03);
        ItemEntity entity = spawn(helper, ITEM_POS, powder, initialVelocity);
        entity.setPickUpDelay(40);

        int entityId = entity.getId();
        int initialAge = entity.getAge();
        Vec3 initialPosition = entity.position();

        helper.runAtTickTime(5, () -> {
            require(helper, entity.isAlive(), "The original ItemEntity was discarded");
            Entity trackedEntity = helper.getLevel().getEntity(entityId);
            require(helper, trackedEntity == entity, "Hardening replaced the ItemEntity instead of its ItemStack");
            require(helper, entity.getItem().is(item("red_concrete")), "Red concrete powder did not harden in source water");
            require(helper, entity.getItem().getCount() == 64, "Hardening changed the stack count");
            require(helper, TEST_NAME.equals(entity.getItem().get(DataComponents.CUSTOM_NAME)), "Hardening did not preserve the custom name component");
            require(helper, entity.hasPickUpDelay(), "Hardening cleared the pickup delay");
            require(helper, entity.getAge() > initialAge, "The original ItemEntity did not continue ticking");
            require(helper, entity.position().distanceTo(initialPosition) < 2.0, "Hardening moved the ItemEntity unexpectedly far");
            require(helper, entity.getDeltaMovement().lengthSqr() > 0.0, "Hardening reset ItemEntity velocity");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void flowingWaterHardensConcretePowder(GameTestHelper helper) {
        helper.setBlock(ITEM_POS.below(), Blocks.STONE);
        helper.setBlock(
                ITEM_POS,
                Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 4)
        );

        ItemEntity entity = spawn(
                helper,
                ITEM_POS,
                new ItemStack(item("light_blue_concrete_powder"), 8),
                Vec3.ZERO
        );

        helper.runAtTickTime(5, () -> {
            BlockPos absolutePos = helper.absolutePos(ITEM_POS);
            require(helper, !helper.getLevel().getFluidState(absolutePos).isSource(), "The flowing-water fixture became a source block");
            require(helper, entity.getItem().is(item("light_blue_concrete")), "Concrete powder did not harden in flowing water");
            require(helper, entity.getItem().getCount() == 8, "Flowing-water hardening changed the stack count");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void nearbyWaterWithoutContactDoesNotHarden(GameTestHelper helper) {
        BlockPos glassPos = ITEM_POS.below();
        BlockPos waterPos = glassPos.below();
        helper.setBlock(waterPos.below(), Blocks.STONE);
        helper.setBlock(waterPos, Blocks.WATER);
        helper.setBlock(glassPos, Blocks.GLASS);

        ItemEntity entity = spawn(
                helper,
                ITEM_POS,
                new ItemStack(item("yellow_concrete_powder"), 12),
                Vec3.ZERO
        );
        entity.setNoGravity(true);

        helper.runAtTickTime(10, () -> {
            require(helper, entity.getItem().is(item("yellow_concrete_powder")), "Concrete powder hardened without touching water");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 80, skyAccess = true)
    public void rainAloneDoesNotHardenConcretePowder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WeatherData weather = level.getServer().getWeatherData();
        setWeather(weather, true);
        helper.setBlock(ITEM_POS.below(), Blocks.STONE);

        ItemEntity entity = spawn(
                helper,
                ITEM_POS,
                new ItemStack(item("green_concrete_powder"), 4),
                Vec3.ZERO
        );
        entity.setNoGravity(true);

        // Server weather fades in over multiple ticks; wait until the rain level crosses
        // the vanilla isRaining() threshold before asserting the item stayed unchanged.
        helper.runAtTickTime(30, () -> {
            try {
                require(helper, level.isRaining(), "Rain fixture did not finish fading in");
                require(helper, entity.getItem().is(item("green_concrete_powder")), "Rain hardened concrete powder without water contact");
                helper.succeed();
            } finally {
                setWeather(weather, false);
            }
        });
    }

    @GameTest(maxTicks = 80)
    public void largeItemEntityBatchHardensWithoutReplacingOrScanning(GameTestHelper helper) {
        helper.setBlock(ITEM_POS.below(), Blocks.STONE);
        helper.setBlock(ITEM_POS, Blocks.WATER);

        List<ItemEntity> entities = new ArrayList<>(STRESS_ENTITY_COUNT);
        for (int index = 0; index < STRESS_ENTITY_COUNT; index++) {
            boolean supportedPowder = index % 2 == 0;
            ItemStack stack = new ItemStack(item(supportedPowder ? "red_concrete_powder" : "stone"));
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("DeadRecall concrete stress " + index));

            ItemEntity entity = spawn(helper, ITEM_POS, stack, Vec3.ZERO);
            entity.setNoGravity(true);
            entities.add(entity);
        }

        helper.runAtTickTime(10, () -> {
            require(helper, entities.size() == STRESS_ENTITY_COUNT, "Stress fixture did not retain every tracked ItemEntity");
            for (int index = 0; index < entities.size(); index++) {
                ItemEntity entity = entities.get(index);
                boolean supportedPowder = index % 2 == 0;

                require(helper, entity.isAlive(), "Stress ItemEntity was discarded at index " + index);
                require(
                        helper,
                        helper.getLevel().getEntity(entity.getId()) == entity,
                        "Stress hardening replaced ItemEntity identity at index " + index
                );
                require(
                        helper,
                        entity.getItem().is(item(supportedPowder ? "red_concrete" : "stone")),
                        supportedPowder
                                ? "Stress powder did not harden at index " + index
                                : "Stress non-powder item changed at index " + index
                );
                require(helper, entity.getItem().getCount() == 1, "Stress conversion changed count at index " + index);
            }
            helper.succeed();
        });
    }

    private static void setWeather(WeatherData weather, boolean raining) {
        weather.setClearWeatherTime(raining ? 0 : 6000);
        weather.setRainTime(raining ? 200 : 0);
        weather.setThunderTime(0);
        weather.setRaining(raining);
        weather.setThundering(false);
    }

    private static ItemEntity spawn(GameTestHelper helper, BlockPos relativePos, ItemStack stack, Vec3 velocity) {
        BlockPos absolutePos = helper.absolutePos(relativePos);
        ItemEntity entity = new ItemEntity(
                helper.getLevel(),
                absolutePos.getX() + 0.5,
                absolutePos.getY() + 0.2,
                absolutePos.getZ() + 0.5,
                stack,
                velocity.x,
                velocity.y,
                velocity.z
        );
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static Item item(String path) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:" + path));
        if (item == null) {
            throw new IllegalStateException("Missing vanilla item minecraft:" + path);
        }
        return item;
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
