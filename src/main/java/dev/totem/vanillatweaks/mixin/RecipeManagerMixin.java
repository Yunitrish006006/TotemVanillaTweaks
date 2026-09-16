package dev.totem.vanillatweaks.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.stream.Stream;

@Mixin(RecipeMap.class)
public abstract class RecipeManagerMixin {

    @Unique
    private static final Identifier totem$vanillaBookshelfRecipe = Identifier.withDefaultNamespace("bookshelf");

    /**
     * 在配方套用時移除原版書櫃配方（minecraft:bookshelf）。
     */
    @Redirect(
            method = "create",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/HolderLookup;listElements()Ljava/util/stream/Stream;")
    )
    private static Stream<Holder.Reference<Recipe<?>>> totem$removeVanillaBookshelfRecipe(HolderLookup<Recipe<?>> recipes) {
        return recipes.listElements().filter(holder -> !holder.key().identifier().equals(totem$vanillaBookshelfRecipe));
    }
}
