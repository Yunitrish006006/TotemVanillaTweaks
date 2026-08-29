package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only semantic access to Stonecutter viewport state. */
@Mixin(StonecutterScreen.class)
public interface StonecutterScreenAccessor {
    @Accessor("scrollOffs") float totem$getScrollOffs();
    @Accessor("scrollOffs") void totem$setScrollOffs(float value);
    @Accessor("startIndex") int totem$getStartIndex();
    @Accessor("startIndex") void totem$setStartIndex(int value);
    @Accessor("displayRecipes") boolean totem$getDisplayRecipes();
    @Accessor("displayRecipes") void totem$setDisplayRecipes(boolean value);
}
