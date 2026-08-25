package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the current client-side Sign editor state. */
@Mixin(AbstractSignEditScreen.class)
public interface AbstractSignEditScreenAccessor {
    @Accessor("messages")
    String[] totem$getMessages();

    @Accessor("isFrontText")
    boolean totem$isFrontText();

    @Accessor("line")
    int totem$getLine();

    @Accessor("text")
    SignText totem$getText();
}
