package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverObservedPlayerIdentity;
import dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keeps Mojang's InventoryScreen renderer while substituting the observed player identity. */
@Mixin(InventoryScreen.class)
public abstract class ObserverInventoryScreenIdentityMixin {
    @ModifyArg(method = "extractBackground", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"), index = 9)
    private LivingEntity totem$renderObservedPlayer(LivingEntity original) {
        return ObserverOwnedScreenCoordinator.isReadOnlyObserverScreen(this)
                ? ObserverObservedPlayerIdentity.resolve(original) : original;
    }
}
