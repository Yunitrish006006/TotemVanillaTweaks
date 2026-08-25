package dev.totem.vanillatweaks.mixin;

import dev.totem.vanillatweaks.network.ObserverBeaconScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverBrewingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverCartographyScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverCrafterScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverGrindstoneScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverLocksmithManagementPayloads;
import dev.totem.vanillatweaks.network.ObserverLoomScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNexusDeathNodeAdminPayloads;
import dev.totem.vanillatweaks.network.ObserverSignScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverSmithingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStonecutterScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverVillagersWoodcutterPayloads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends the built-in semantic capability sanitizer with post-v2 semantic families. */
@Mixin(value = ObserverNativeScreenPayloads.class, remap = false)
public abstract class ObserverNativeScreenPayloadsExtensionCapabilityMixin {
    @Inject(method = "sanitizeCapabilities", at = @At("HEAD"), cancellable = true)
    private static void totem$includeExtensionCapabilities(long capabilities, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(capabilities & (ObserverNativeScreenPayloads.KNOWN_CAPABILITIES
                | ObserverVillagersWoodcutterPayloads.CAPABILITY
                | ObserverBrewingScreenPayloads.CAPABILITY
                | ObserverSmithingScreenPayloads.CAPABILITY
                | ObserverStonecutterScreenPayloads.CAPABILITY
                | ObserverGrindstoneScreenPayloads.CAPABILITY
                | ObserverLoomScreenPayloads.CAPABILITY
                | ObserverCartographyScreenPayloads.CAPABILITY
                | ObserverBeaconScreenPayloads.CAPABILITY
                | ObserverSignScreenPayloads.CAPABILITY
                | ObserverCrafterScreenPayloads.CAPABILITY
                | ObserverNexusDeathNodeAdminPayloads.CAPABILITY
                | ObserverLocksmithManagementPayloads.CAPABILITY));
    }
}
