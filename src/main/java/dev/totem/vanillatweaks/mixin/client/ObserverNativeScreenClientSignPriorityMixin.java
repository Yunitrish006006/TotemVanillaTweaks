package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverSignScreenPayloads;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents metadata placeholders from competing with the Sign semantic family. */
@Mixin(value = ObserverNativeScreenClient.class, remap = false)
public abstract class ObserverNativeScreenClientSignPriorityMixin {
    @Inject(method = "isStructuredTargetScreen", at = @At("HEAD"), cancellable = true)
    private static void totem$markSignStructured(Screen screen, CallbackInfoReturnable<Boolean> cir) {
        if (ObserverNativeClient.targetSupportsScreen(ObserverSignScreenPayloads.CAPABILITY)
                && screen instanceof AbstractSignEditScreen) {
            cir.setReturnValue(true);
        }
    }
}
