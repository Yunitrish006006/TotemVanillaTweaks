package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BookViewScreen.class)
public interface BookViewScreenAccessor {
    @Accessor("bookAccess")
    BookViewScreen.BookAccess totemVanillaTweaks$getBookAccess();

    @Accessor("currentPage")
    int totemVanillaTweaks$getCurrentPage();
}
