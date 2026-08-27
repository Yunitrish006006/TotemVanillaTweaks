package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only recipe-book page position for Observer reconstruction. */
@Mixin(RecipeBookPage.class)
public interface RecipeBookPageAccessor {
    @Accessor("currentPage") int totem$getCurrentPage();
    @Accessor("totalPages") int totem$getTotalPages();
}
