package dev.totem.vanillatweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNexusDeathNodeAdminPayloads;
import dev.totem.vanillatweaks.observer.ObserverNativeSessionManager;
import dev.totem.vanillatweaks.observer.ObserverNexusDeathNodeAdminRelayManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Adds Nexus death-node administration to Observer negotiation and cleanup. */
@Mixin(value = ObserverNativeSessionManager.class, remap = false)
public abstract class ObserverNativeSessionManagerNexusDeathNodeAdminCapabilityMixin {
    @ModifyReturnValue(method = "negotiatedScreenCapabilities", at = @At("RETURN"))
    private static long totem$includeNexusDeathNodeAdmin(long original, ServerPlayer observer) {
        if (!ServerPlayNetworking.canSend(observer, ObserverNexusDeathNodeAdminPayloads.AdminRelay.TYPE)) return original;
        return ObserverNativeScreenPayloads.sanitizeCapabilities(original | ObserverNexusDeathNodeAdminPayloads.CAPABILITY);
    }

    @Inject(method = "clearTargetSequences", at = @At("TAIL"))
    private static void totem$clearNexusDeathNodeAdminSequence(UUID targetId, CallbackInfo ci) {
        ObserverNexusDeathNodeAdminRelayManager.clearTarget(targetId);
    }
}
