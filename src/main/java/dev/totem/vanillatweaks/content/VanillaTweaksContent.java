package dev.totem.vanillatweaks.content;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Canonical Totem ownership for the retained vanilla-adjacent content. */
public final class VanillaTweaksContent {
    public static final Identifier COOKED_PUFFERFISH_ID = totemId("vanilla_tweaks/cooked_pufferfish");
    public static final Identifier SMOKED_ROTTEN_FLESH_ID = totemId("vanilla_tweaks/smoked_rotten_flesh");
    public static final Identifier GRAVEL_IRON_ORE_ID = totemId("vanilla_tweaks/gravel_iron_ore");

    public static final Item COOKED_PUFFERFISH = registerFood(COOKED_PUFFERFISH_ID, 0.4F);
    public static final Item SMOKED_ROTTEN_FLESH = registerFood(SMOKED_ROTTEN_FLESH_ID, 0.1F);
    public static final Block GRAVEL_IRON_ORE = registerGravelIronOreBlock(GRAVEL_IRON_ORE_ID);
    public static final Item GRAVEL_IRON_ORE_ITEM = registerBlockItem(GRAVEL_IRON_ORE_ID, GRAVEL_IRON_ORE);

    private static boolean initialized;

    private VanillaTweaksContent() {
    }

    public static synchronized void register() {
        if (initialized) {
            return;
        }
        registerCreativeOutputs();
        initialized = true;
    }

    private static Item registerFood(Identifier id, float saturationModifier) {
        return registerItem(id, new Item(itemProperties(id).food(foodProperties(saturationModifier))));
    }

    private static Block registerGravelIronOreBlock(Identifier id) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        DropExperienceBlock block = new DropExperienceBlock(
                UniformInt.of(0, 2),
                BlockBehaviour.Properties.of()
                        .setId(key)
                        .strength(2.4F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.GRAVEL)
        );
        return Registry.register(BuiltInRegistries.BLOCK, key, block);
    }

    private static Item registerBlockItem(Identifier id, Block block) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item registered = Registry.register(
                BuiltInRegistries.ITEM,
                key,
                new BlockItem(block, itemProperties(id).useBlockDescriptionPrefix())
        );
        ((BlockItem) registered).registerBlocks(Item.BY_BLOCK, registered);
        return registered;
    }

    private static Item registerItem(Identifier id, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, id), item);
    }

    private static Item.Properties itemProperties(Identifier id) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id));
    }

    private static FoodProperties foodProperties(float saturationModifier) {
        return new FoodProperties.Builder()
                .nutrition(2)
                .saturationModifier(saturationModifier)
                .build();
    }

    private static void registerCreativeOutputs() {
        CreativeModeTabEvents.MODIFY_OUTPUT_ALL.register((tab, output) -> {
            Identifier id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (Identifier.withDefaultNamespace("food_and_drinks").equals(id)) {
                output.accept(COOKED_PUFFERFISH);
                output.accept(SMOKED_ROTTEN_FLESH);
            } else if (Identifier.withDefaultNamespace("functional_blocks").equals(id)) {
                output.accept(GRAVEL_IRON_ORE_ITEM);
            }
        });
    }

    private static Identifier totemId(String path) {
        return Identifier.fromNamespaceAndPath("totem", path);
    }
}
