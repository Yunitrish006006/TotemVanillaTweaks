package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Allows the observer-only InventoryScreen subclass to install a detached vanilla InventoryMenu. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenMenuAccessor {
    @Mutable @Accessor("menu") void totem$setMenu(AbstractContainerMenu menu);
}
