package dev.totem.vanillatweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.totem.vanillatweaks.network.ObserverLoomScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.observer.ObserverLoomRelayManager;
import dev.totem.vanillatweaks.observer.ObserverNativeSessionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Adds Loom semantic support to Observer negotiation and cleanup. */
@Mixin(value = ObserverNativeSessionManager.class, remap = false)
public abstract class ObserverNativeSessionManagerLoomCapabilityMixin {
    @ModifyReturnValue(method = "negotiatedScreenCapabilities", at = @At("RETURN"))
    private static long totem$includeLoomCapability(long original, ServerPlayer observer) {
        if (!ServerPlayNetworking.canSend(observer, ObserverLoomScreenPayloads.LoomRelay.TYPE)) return original;
        return ObserverNativeScreenPayloads.sanitizeCapabilities(original | ObserverLoomScreenPayloads.CAPABILITY);
    }

    @Inject(method = "clearTargetSequences", at = @At("TAIL"))
    private static void totem$clearLoomSequence(UUID targetId, CallbackInfo ci) {
        ObserverLoomRelayManager.clearTarget(targetId);
    }
}
