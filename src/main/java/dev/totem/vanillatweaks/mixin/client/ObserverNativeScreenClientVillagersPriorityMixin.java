package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverVillagersWoodcutterPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives TotemVillagers Woodcutter semantics priority over generic container/metadata adapters. */
@Mixin(value = ObserverNativeScreenClient.class, remap = false)
public abstract class ObserverNativeScreenClientVillagersPriorityMixin {
    @Shadow
    private static void closeTargetContainer(boolean canSend) {
        throw new AssertionError();
    }

    @Shadow
    private static void closeTargetFurnace(boolean canSend) {
        throw new AssertionError();
    }

    @Inject(method = "tickTarget", at = @At("HEAD"), cancellable = true)
    private static void totem$preferWoodcutterFamily(Minecraft minecraft, CallbackInfo ci) {
        if (!supportsWoodcutter(minecraft.gui.screen())) return;
        closeTargetFurnace(ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_FURNACE));
        closeTargetContainer(ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS));
        ci.cancel();
    }

    private static boolean supportsWoodcutter(Screen screen) {
        return ObserverNativeClient.targetSupportsScreen(ObserverVillagersWoodcutterPayloads.CAPABILITY)
                && screen != null && ObserverVillagersWoodcutterPayloads.SCREEN_CLASS.equals(screen.getClass().getName());
    }
}
