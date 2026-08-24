package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives enchanting semantics priority over the generic container adapter. */
@Mixin(value = ObserverNativeScreenClient.class, remap = false)
public abstract class ObserverNativeScreenClientEnchantingPriorityMixin {
    @Shadow
    private static void closeTargetContainer(boolean canSend) {
        throw new AssertionError();
    }

    @Shadow
    private static void closeTargetFurnace(boolean canSend) {
        throw new AssertionError();
    }

    @Inject(method = "tickTarget", at = @At("HEAD"), cancellable = true)
    private static void totem$preferEnchantingFamily(Minecraft minecraft, CallbackInfo ci) {
        if (!ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_ENCHANTING)
                || !(minecraft.gui.screen() instanceof EnchantmentScreen)) {
            return;
        }
        closeTargetFurnace(ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_FURNACE));
        closeTargetContainer(ObserverNativeClient.targetSupportsScreen(
                ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS
        ));
        ci.cancel();
    }
}
