package dev.totem.vanillatweaks.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Unique
    private static final Identifier deadrecall$vanillaBookshelfRecipe = Identifier.withDefaultNamespace("bookshelf");

    /**
     * 在配方套用時移除原版書櫃配方（minecraft:bookshelf）。
     */
    @ModifyVariable(
            method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private RecipeMap deadrecall$removeVanillaBookshelfRecipe(RecipeMap recipes) {
        List<RecipeHolder<?>> filtered = recipes.values().stream()
                .filter(holder -> !deadrecall$isVanillaBookshelfRecipe(holder))
                .toList();
        return RecipeMap.create(filtered);
    }

    @Unique
    private boolean deadrecall$isVanillaBookshelfRecipe(RecipeHolder<?> holder) {
        return holder.id().identifier().equals(deadrecall$vanillaBookshelfRecipe);
    }
}
