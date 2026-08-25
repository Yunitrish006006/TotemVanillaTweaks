package dev.totem.vanillatweaks.mixin;

import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNexusScreenPayloads;
import dev.totem.vanillatweaks.observer.ObserverNativeSessionManager;
import dev.totem.vanillatweaks.observer.ObserverNexusRelayManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** Adds optional TotemNexus support to standard Observer capability negotiation and cleanup. */
@Mixin(value = ObserverNativeSessionManager.class, remap = false)
public abstract class ObserverNativeSessionManagerNexusCapabilityMixin {
    @Inject(method = "negotiatedScreenCapabilities", at = @At("RETURN"), cancellable = true)
    private static void totem$includeNexusCapability(ServerPlayer observer, CallbackInfoReturnable<Long> cir) {
        if (!ServerPlayNetworking.canSend(observer, ObserverNexusScreenPayloads.NexusRelay.TYPE)) return;
        cir.setReturnValue(ObserverNativeScreenPayloads.sanitizeCapabilities(
                cir.getReturnValue() | ObserverNativeScreenPayloads.CAPABILITY_NEXUS));
    }

    @Inject(method = "clearTargetSequences", at = @At("TAIL"))
    private static void totem$clearNexusSequence(UUID targetId, CallbackInfo ci) {
        ObserverNexusRelayManager.clearTarget(targetId);
    }
}
