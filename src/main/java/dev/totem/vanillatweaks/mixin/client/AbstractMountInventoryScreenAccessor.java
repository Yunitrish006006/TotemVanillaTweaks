package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractMountInventoryScreen.class)
public interface AbstractMountInventoryScreenAccessor {
    @Accessor("mount") LivingEntity totem$getMount();
    @Accessor("inventoryColumns") int totem$getInventoryColumns();
}
