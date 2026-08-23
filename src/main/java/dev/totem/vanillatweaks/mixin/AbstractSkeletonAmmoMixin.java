package dev.totem.vanillatweaks.mixin;

import dev.totem.vanillatweaks.skeleton.SkeletonAmmo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes skeleton archery finite while preserving vanilla melee/ranged goal selection. */
@Mixin(AbstractSkeleton.class)
public abstract class AbstractSkeletonAmmoMixin {
    @Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
    private void totem$blockRangedAttackWhenEmpty(LivingEntity target, float power, CallbackInfo ci) {
        AbstractSkeleton skeleton = (AbstractSkeleton) (Object) this;
        if (!SkeletonAmmo.hasAmmo(skeleton)) {
            skeleton.reassessWeaponGoal();
            ci.cancel();
        }
    }

    @Inject(method = "performRangedAttack", at = @At("TAIL"))
    private void totem$consumeArrowAfterShot(LivingEntity target, float power, CallbackInfo ci) {
        SkeletonAmmo.consumeArrow((AbstractSkeleton) (Object) this);
    }

    @Inject(method = "canUseNonMeleeWeapon", at = @At("HEAD"), cancellable = true)
    private void totem$disableBowGoalWhenEmpty(ItemStack item, CallbackInfoReturnable<Boolean> cir) {
        AbstractSkeleton skeleton = (AbstractSkeleton) (Object) this;
        if (!SkeletonAmmo.hasAmmo(skeleton)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getArrow", at = @At("RETURN"))
    private void totem$allowSkeletonArrowPickup(
            ItemStack projectile,
            float power,
            ItemStack firingWeapon,
            CallbackInfoReturnable<AbstractArrow> cir
    ) {
        AbstractArrow arrow = cir.getReturnValue();
        if (arrow != null) {
            arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        }
    }
}
