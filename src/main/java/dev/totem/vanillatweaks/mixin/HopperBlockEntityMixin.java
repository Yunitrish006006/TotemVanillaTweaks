package dev.totem.vanillatweaks.mixin;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    private static final int FURNACE_RESULT_SLOT = 2;

    @Inject(method = "tryTakeInItemFromSlot", at = @At("RETURN"))
    private static void deadrecall$popFurnaceExperienceWhenHopperTakesResult(
            Hopper hopper,
            Container source,
            int slot,
            Direction direction,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())
                || slot != FURNACE_RESULT_SLOT
                || !(source instanceof AbstractFurnaceBlockEntity furnace)) {
            return;
        }

        Level level = furnace.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 orbPos = new Vec3(hopper.getLevelX(), hopper.getLevelY() + 0.25D, hopper.getLevelZ());
        List<?> awardedRecipes = furnace.getRecipesToAwardAndPopExperience(serverLevel, orbPos);
        if (!awardedRecipes.isEmpty()) {
            ((AbstractFurnaceBlockEntityAccessor) furnace).deadrecall$getRecipesUsed().clear();
            furnace.setChanged();
        }
    }
}
