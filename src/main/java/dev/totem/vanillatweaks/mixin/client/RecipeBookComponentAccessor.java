package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Read-only recipe-book UI state. Search text itself is deliberately never exposed. */
@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {
    @Accessor("tabButtons") List<RecipeBookTabButton> totem$getTabButtons();
    @Accessor("selectedTab") RecipeBookTabButton totem$getSelectedTab();
    @Accessor("selectedTab") void totem$setSelectedTab(RecipeBookTabButton value);
    @Accessor("filterButton") CycleButton<Boolean> totem$getFilterButton();
    @Accessor("searchBox") EditBox totem$getSearchBox();
    @Accessor("recipeBookPage") RecipeBookPage totem$getRecipeBookPage();
    @Accessor("visible") void totem$setVisible(boolean value);
    @Accessor("widthTooNarrow") void totem$setWidthTooNarrow(boolean value);
    @Invoker("initVisuals") void totem$initVisuals();
}
