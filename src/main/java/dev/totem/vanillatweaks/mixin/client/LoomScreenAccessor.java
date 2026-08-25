package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.screens.inventory.LoomScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only semantic access to Loom viewport state. */
@Mixin(LoomScreen.class)
public interface LoomScreenAccessor {
    @Accessor("startRow") int totem$getStartRow();
    @Accessor("displayPatterns") boolean totem$getDisplayPatterns();
    @Accessor("hasMaxPatterns") boolean totem$getHasMaxPatterns();
}
