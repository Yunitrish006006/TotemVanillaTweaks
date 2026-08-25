package dev.totem.vanillatweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverSmithingScreenPayloads;
import dev.totem.vanillatweaks.observer.ObserverNativeSessionManager;
import dev.totem.vanillatweaks.observer.ObserverSmithingRelayManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Adds Smithing semantic support to Observer capability negotiation and cleanup. */
@Mixin(value = ObserverNativeSessionManager.class, remap = false)
public abstract class ObserverNativeSessionManagerSmithingCapabilityMixin {
    @ModifyReturnValue(method = "negotiatedScreenCapabilities", at = @At("RETURN"))
    private static long totem$includeSmithingCapability(long original, ServerPlayer observer) {
        if (!ServerPlayNetworking.canSend(observer, ObserverSmithingScreenPayloads.SmithingRelay.TYPE)) return original;
        return ObserverNativeScreenPayloads.sanitizeCapabilities(original | ObserverSmithingScreenPayloads.CAPABILITY);
    }

    @Inject(method = "clearTargetSequences", at = @At("TAIL"))
    private static void totem$clearSmithingSequence(UUID targetId, CallbackInfo ci) {
        ObserverSmithingRelayManager.clearTarget(targetId);
    }
}
