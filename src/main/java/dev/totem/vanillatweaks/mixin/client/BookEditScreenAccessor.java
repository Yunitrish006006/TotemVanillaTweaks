package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(BookEditScreen.class)
public interface BookEditScreenAccessor {
    @Accessor("pages")
    List<String> totemVanillaTweaks$getPages();

    @Accessor("currentPage")
    int totemVanillaTweaks$getCurrentPage();

    @Accessor("currentPage")
    void totemVanillaTweaks$setCurrentPage(int page);

    @Invoker("updatePageContent")
    void totemVanillaTweaks$updatePageContent();
}
