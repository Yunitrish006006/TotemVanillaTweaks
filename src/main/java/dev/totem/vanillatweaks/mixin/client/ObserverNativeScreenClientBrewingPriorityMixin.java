package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverBrewingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives Brewing semantics priority over generic container/metadata adapters. */
@Mixin(value = ObserverNativeScreenClient.class, remap = false)
public abstract class ObserverNativeScreenClientBrewingPriorityMixin {
    @Shadow private static void closeTargetContainer(boolean canSend) { throw new AssertionError(); }
    @Shadow private static void closeTargetFurnace(boolean canSend) { throw new AssertionError(); }

    @Inject(method = "tickTarget", at = @At("HEAD"), cancellable = true)
    private static void totem$preferBrewingFamily(Minecraft minecraft, CallbackInfo ci) {
        if (!supportsBrewing(minecraft.gui.screen())) return;
        closeTargetFurnace(ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_FURNACE));
        closeTargetContainer(ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS));
        ci.cancel();
    }

    @Inject(method = "isStructuredTargetScreen", at = @At("HEAD"), cancellable = true)
    private static void totem$markBrewingStructured(Screen screen, CallbackInfoReturnable<Boolean> cir) {
        if (supportsBrewing(screen)) cir.setReturnValue(true);
    }

    private static boolean supportsBrewing(Screen screen) {
        return ObserverNativeClient.targetSupportsScreen(ObserverBrewingScreenPayloads.CAPABILITY)
                && screen != null && ObserverBrewingScreenPayloads.SCREEN_CLASS.equals(screen.getClass().getName());
    }
}
