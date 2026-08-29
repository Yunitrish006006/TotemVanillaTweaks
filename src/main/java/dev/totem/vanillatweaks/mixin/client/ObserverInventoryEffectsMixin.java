package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverNativeCraftingScreenClient;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;

/** Keeps the production InventoryScreen renderer bound to target effect semantics. */
@Mixin(EffectsInInventory.class)
abstract class ObserverInventoryEffectsMixin {
    @Shadow @Final private AbstractContainerScreen<?> screen;

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"))
    private Collection<MobEffectInstance> totem$useRemoteEffects(LocalPlayer player) {
        return ObserverNativeCraftingScreenClient.activeEffectsFor(screen, player.getActiveEffects());
    }
}
