package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.client.ObserverNexusDeathNodeAdminScreenClient;
import dev.totem.vanillatweaks.network.ObserverNexusDeathNodeAdminPayloads;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents generic metadata placeholders from competing with Nexus death-node admin semantics. */
@Mixin(value = ObserverNativeScreenClient.class, remap = false)
public abstract class ObserverNativeScreenClientNexusDeathNodeAdminPriorityMixin {
    @Inject(method = "isStructuredTargetScreen", at = @At("HEAD"), cancellable = true)
    private static void totem$markNexusDeathNodeAdminStructured(Screen screen, CallbackInfoReturnable<Boolean> cir) {
        if (ObserverNativeClient.targetSupportsScreen(ObserverNexusDeathNodeAdminPayloads.CAPABILITY)
                && ObserverNexusDeathNodeAdminScreenClient.isTargetScreen(screen)) {
            cir.setReturnValue(true);
        }
    }
}
