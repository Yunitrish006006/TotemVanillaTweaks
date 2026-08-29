package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.screens.inventory.LoomScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only semantic access to Loom viewport state. */
@Mixin(LoomScreen.class)
public interface LoomScreenAccessor {
    @Accessor("scrollOffs") float totem$getScrollOffs();
    @Accessor("scrollOffs") void totem$setScrollOffs(float value);
    @Accessor("startRow") int totem$getStartRow();
    @Accessor("startRow") void totem$setStartRow(int value);
    @Accessor("displayPatterns") boolean totem$getDisplayPatterns();
    @Accessor("displayPatterns") void totem$setDisplayPatterns(boolean value);
    @Accessor("hasMaxPatterns") boolean totem$getHasMaxPatterns();
    @Accessor("hasMaxPatterns") void totem$setHasMaxPatterns(boolean value);
}
