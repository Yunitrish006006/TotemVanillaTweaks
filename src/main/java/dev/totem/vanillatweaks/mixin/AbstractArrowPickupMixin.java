package dev.totem.vanillatweaks.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Allows players to recover arrows fired by any vanilla skeleton variant. */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowPickupMixin {
    @Inject(method = "setOwner(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void totem$allowSkeletonArrowPickup(Entity owner, CallbackInfo ci) {
        if (owner instanceof AbstractSkeleton) {
            ((AbstractArrow) (Object) this).pickup = AbstractArrow.Pickup.ALLOWED;
        }
    }
}
