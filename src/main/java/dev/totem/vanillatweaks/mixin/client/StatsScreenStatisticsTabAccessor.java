package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the list owned by a private StatsScreen statistics tab. */
@Mixin(targets = "net.minecraft.client.gui.screens.achievement.StatsScreen$StatisticsTab")
public interface StatsScreenStatisticsTabAccessor {
    @Accessor("list") AbstractSelectionList<?> totem$getList();
}
