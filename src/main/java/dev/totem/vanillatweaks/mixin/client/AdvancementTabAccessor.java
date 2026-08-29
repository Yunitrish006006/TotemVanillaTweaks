package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the current advancements viewport pan. */
@Mixin(AdvancementTab.class)
public interface AdvancementTabAccessor {
    @Accessor("scrollX") double totem$getScrollX();
    @Accessor("scrollY") double totem$getScrollY();
    @Accessor("scrollX") void totem$setScrollX(double value);
    @Accessor("scrollY") void totem$setScrollY(double value);
}
