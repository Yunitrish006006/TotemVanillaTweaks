package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverGrindstoneScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives Grindstone semantics priority over generic container/metadata adapters. */
@Mixin(value = ObserverNativeScreenClient.class, remap = false)
public abstract class ObserverNativeScreenClientGrindstonePriorityMixin {
    @Shadow private static void closeTargetContainer(boolean canSend) { throw new AssertionError(); }
    @Shadow private static void closeTargetFurnace(boolean canSend) { throw new AssertionError(); }

    @Inject(method = "tickTarget", at = @At("HEAD"), cancellable = true)
    private static void totem$preferGrindstoneFamily(Minecraft minecraft, CallbackInfo ci) {
        if (!supportsGrindstone(minecraft.gui.screen())) return;
        closeTargetFurnace(ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_FURNACE));
        closeTargetContainer(ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS));
        ci.cancel();
    }

    @Inject(method = "isStructuredTargetScreen", at = @At("HEAD"), cancellable = true)
    private static void totem$markGrindstoneStructured(Screen screen, CallbackInfoReturnable<Boolean> cir) {
        if (supportsGrindstone(screen)) cir.setReturnValue(true);
    }

    private static boolean supportsGrindstone(Screen screen) {
        return ObserverNativeClient.targetSupportsScreen(ObserverGrindstoneScreenPayloads.CAPABILITY)
                && screen != null && ObserverGrindstoneScreenPayloads.SCREEN_CLASS.equals(screen.getClass().getName());
    }
}
