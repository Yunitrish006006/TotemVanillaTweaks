package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only semantic access to Stonecutter viewport state. */
@Mixin(StonecutterScreen.class)
public interface StonecutterScreenAccessor {
    @Accessor("scrollOffs") float totem$getScrollOffs();
    @Accessor("startIndex") int totem$getStartIndex();
    @Accessor("displayRecipes") boolean totem$getDisplayRecipes();
}
