package dev.totem.vanillatweaks.mixin;

import dev.totem.vanillatweaks.network.ObserverAutomataCopperGolemPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.observer.ObserverAutomataCopperGolemRelayManager;
import dev.totem.vanillatweaks.observer.ObserverNativeSessionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** Adds optional TotemAutomata support to standard Observer negotiation and cleanup. */
@Mixin(value = ObserverNativeSessionManager.class, remap = false)
public abstract class ObserverNativeSessionManagerAutomataCapabilityMixin {
    @Inject(method = "negotiatedScreenCapabilities", at = @At("RETURN"), cancellable = true)
    private static void totem$includeAutomataCapability(ServerPlayer observer, CallbackInfoReturnable<Long> cir) {
        if (!ServerPlayNetworking.canSend(observer, ObserverAutomataCopperGolemPayloads.CopperGolemRelay.TYPE)) return;
        cir.setReturnValue(ObserverNativeScreenPayloads.sanitizeCapabilities(
                cir.getReturnValue() | ObserverNativeScreenPayloads.CAPABILITY_AUTOMATA_COPPER_GOLEM));
    }

    @Inject(method = "clearTargetSequences", at = @At("TAIL"))
    private static void totem$clearAutomataSequence(UUID targetId, CallbackInfo ci) {
        ObserverAutomataCopperGolemRelayManager.clearTarget(targetId);
    }
}
