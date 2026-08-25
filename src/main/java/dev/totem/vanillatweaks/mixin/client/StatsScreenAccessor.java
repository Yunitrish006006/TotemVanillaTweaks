package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatsCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read-only semantic access to the vanilla Statistics screen. */
@Mixin(StatsScreen.class)
public interface StatsScreenAccessor {
    @Accessor("stats") StatsCounter totem$getStats();
    @Accessor("tabManager") TabManager totem$getTabManager();
    @Accessor("tabNavigationBar") MenuTabBar totem$getTabNavigationBar();
    @Accessor("isLoading") boolean totem$isLoading();

    @Invoker("getTranslationKey")
    static String totem$getTranslationKey(Stat<Identifier> stat) {
        throw new AssertionError();
    }
}
