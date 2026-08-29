package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops local keyboard/IME mutation; Escape remains the explicit stop-observing action. */
@Mixin(KeyboardHandler.class)
public abstract class ObserverReadOnlyKeyboardFirewallMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void totem$blockObserverKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (!ObserverOwnedScreenCoordinator.isReadOnlyObserverScreen(minecraft.gui.screen())) return;
        if (action == GLFW.GLFW_PRESS && event.key() == GLFW.GLFW_KEY_ESCAPE) minecraft.gui.screen().onClose();
        ci.cancel();
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void totem$blockObserverCharacter(long window, CharacterEvent event, CallbackInfo ci) {
        if (ObserverOwnedScreenCoordinator.isReadOnlyObserverScreen(minecraft.gui.screen())) ci.cancel();
    }

    @Inject(method = "preeditCallback", at = @At("HEAD"), cancellable = true)
    private void totem$blockObserverPreedit(long window, PreeditEvent event, CallbackInfo ci) {
        if (ObserverOwnedScreenCoordinator.isReadOnlyObserverScreen(minecraft.gui.screen())) ci.cancel();
    }
}
