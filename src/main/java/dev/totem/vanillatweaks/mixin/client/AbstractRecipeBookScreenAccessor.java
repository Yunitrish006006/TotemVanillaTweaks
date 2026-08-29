package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only semantic access to the recipe-book mode attached to a container screen. */
@Mixin(AbstractRecipeBookScreen.class)
public interface AbstractRecipeBookScreenAccessor {
    @Accessor("recipeBookComponent") RecipeBookComponent<?> totem$getRecipeBookComponent();
    @Accessor("widthTooNarrow") boolean totem$getWidthTooNarrow();
    @Accessor("widthTooNarrow") void totem$setWidthTooNarrow(boolean value);
}
