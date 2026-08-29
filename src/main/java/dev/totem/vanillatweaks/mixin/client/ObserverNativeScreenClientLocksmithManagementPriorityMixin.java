package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverStructuredScreenPolicy;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverLocksmithManagementPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents generic container/metadata relays from competing with Locksmith management semantics. */
@Mixin(value = ObserverNativeScreenClient.class, remap = false)
public abstract class ObserverNativeScreenClientLocksmithManagementPriorityMixin {
    @Shadow private static void closeTargetContainer(boolean canSend) { throw new AssertionError(); }
    @Shadow private static void closeTargetFurnace(boolean canSend) { throw new AssertionError(); }

    @Inject(method = "tickTarget", at = @At("HEAD"), cancellable = true)
    private static void totem$preferLocksmithManagement(Minecraft minecraft, CallbackInfo ci) {
        if (!supportsLocksmith(minecraft.gui.screen())) return;
        closeTargetFurnace(ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_FURNACE));
        closeTargetContainer(ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS));
        ci.cancel();
    }

    private static boolean supportsLocksmith(Screen screen) {
        return ObserverNativeClient.targetSupportsScreen(ObserverLocksmithManagementPayloads.CAPABILITY)
                && ObserverStructuredScreenPolicy.suppressGenericMetadata(
                screen == null ? "" : screen.getClass().getName(), ObserverLocksmithManagementPayloads.CAPABILITY);
    }
}
