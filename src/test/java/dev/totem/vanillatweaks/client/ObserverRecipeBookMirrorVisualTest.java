package dev.totem.vanillatweaks.client;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ObserverRecipeBookMirrorVisualTest {
    @Test
    void knownSemanticTabIdsUseVanillaTranslatedPlayerFacingLabels() {
        assertEquals(Component.translatable("container.crafting").getString(),
                ObserverNativeCraftingScreenClient.recipeBookTabLabel("search:crafting"));
        assertEquals(Component.translatable("itemGroup.buildingBlocks").getString(),
                ObserverNativeCraftingScreenClient.recipeBookTabLabel("minecraft:crafting_building_blocks"));
        assertEquals(Component.translatable("itemGroup.redstone").getString(),
                ObserverNativeCraftingScreenClient.recipeBookTabLabel("minecraft:crafting_redstone"));
    }

    @Test
    void unknownRegistryTabIsHumanizedInsteadOfRenderedAsDebugSyntax() {
        String label = ObserverNativeCraftingScreenClient.recipeBookTabLabel("example:magic_parts");
        assertEquals("Magic Parts", label);
        assertFalse(label.contains(":"));
        assertFalse(label.contains("_"));
    }
}
