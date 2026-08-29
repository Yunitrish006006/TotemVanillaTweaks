package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops every local mouse mutation before any read-only production Screen can receive it. */
@Mixin(MouseHandler.class)
public abstract class ObserverReadOnlyMouseFirewallMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void totem$blockObserverButton(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (ObserverOwnedScreenCoordinator.isReadOnlyObserverScreen(minecraft.gui.screen())) ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void totem$blockObserverScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ObserverOwnedScreenCoordinator.isReadOnlyObserverScreen(minecraft.gui.screen())) ci.cancel();
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void totem$blockObserverMove(long window, double x, double y, CallbackInfo ci) {
        if (ObserverOwnedScreenCoordinator.isReadOnlyObserverScreen(minecraft.gui.screen())) ci.cancel();
    }
}
