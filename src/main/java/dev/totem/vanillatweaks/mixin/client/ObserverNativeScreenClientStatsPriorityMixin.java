package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.client.ObserverStatsScreenClient;
import dev.totem.vanillatweaks.network.ObserverStatsScreenPayloads;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents generic metadata placeholders from competing with Statistics semantics. */
@Mixin(value = ObserverNativeScreenClient.class, remap = false)
public abstract class ObserverNativeScreenClientStatsPriorityMixin {
    @Inject(method = "isStructuredTargetScreen", at = @At("HEAD"), cancellable = true)
    private static void totem$markStatsStructured(Screen screen, CallbackInfoReturnable<Boolean> cir) {
        if (ObserverNativeClient.targetSupportsScreen(ObserverStatsScreenPayloads.CAPABILITY)
                && ObserverStatsScreenClient.isTargetScreen(screen)) {
            cir.setReturnValue(true);
        }
    }
}
