package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.stats.StatType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read-only access to the active item-statistics sort state. */
@Mixin(targets = "net.minecraft.client.gui.screens.achievement.StatsScreen$ItemStatisticsList")
public interface StatsScreenItemStatisticsListAccessor {
    @Accessor("sortColumn") StatType<?> totem$getSortColumn();
    @Accessor("sortOrder") int totem$getSortOrder();
    @Accessor("sortColumn") void totem$setSortColumn(StatType<?> column);
    @Accessor("sortOrder") void totem$setSortOrder(int order);
    @Invoker("sortByColumn") void totem$sortByColumn(StatType<?> column);
}
