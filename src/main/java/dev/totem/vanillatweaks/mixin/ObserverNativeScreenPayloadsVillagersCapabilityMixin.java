package dev.totem.vanillatweaks.mixin;

import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverVillagersWoodcutterPayloads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends the built-in semantic capability sanitizer with optional TotemVillagers families. */
@Mixin(value = ObserverNativeScreenPayloads.class, remap = false)
public abstract class ObserverNativeScreenPayloadsVillagersCapabilityMixin {
    @Inject(method = "sanitizeCapabilities", at = @At("HEAD"), cancellable = true)
    private static void totem$includeVillagersCapabilities(long capabilities, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(capabilities & (ObserverNativeScreenPayloads.KNOWN_CAPABILITIES
                | ObserverVillagersWoodcutterPayloads.CAPABILITY));
    }
}
