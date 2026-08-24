package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(BookSignScreen.class)
public interface BookSignScreenAccessor {
    @Accessor("pages")
    List<String> totemVanillaTweaks$getPages();

    @Accessor("titleValue")
    String totemVanillaTweaks$getTitleValue();

    @Accessor("ownerText")
    Component totemVanillaTweaks$getOwnerText();
}
