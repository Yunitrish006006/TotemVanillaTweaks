package dev.totem.vanillatweaks.mixin;

import dev.totem.vanillatweaks.item.ConcretePowderItemHardening;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ConcretePowderItemEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void deadrecall$hardenConcretePowderInWater(CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        if (!(itemEntity.level() instanceof ServerLevel)) {
            return;
        }
        ConcretePowderItemHardening.tryHarden(itemEntity);
    }
}
