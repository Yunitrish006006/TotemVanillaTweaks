package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the currently selected vanilla advancements tab. */
@Mixin(AdvancementsScreen.class)
public interface AdvancementsScreenAccessor {
    @Accessor("selectedTab") AdvancementTab totem$getSelectedTab();
    @Accessor("advancements") ClientAdvancements totem$getAdvancements();
}
