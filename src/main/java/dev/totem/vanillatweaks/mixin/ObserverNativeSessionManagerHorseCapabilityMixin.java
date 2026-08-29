package dev.totem.vanillatweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.totem.vanillatweaks.network.ObserverHorseScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.observer.ObserverHorseRelayManager;
import dev.totem.vanillatweaks.observer.ObserverNativeSessionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = ObserverNativeSessionManager.class, remap = false)
public abstract class ObserverNativeSessionManagerHorseCapabilityMixin {
    @ModifyReturnValue(method = "negotiatedScreenCapabilities", at = @At("RETURN"))
    private static long totem$includeHorse(long original, ServerPlayer observer) {
        if (!ServerPlayNetworking.canSend(observer, ObserverHorseScreenPayloads.HorseRelay.TYPE)) return original;
        return ObserverNativeScreenPayloads.sanitizeCapabilities(original | ObserverHorseScreenPayloads.CAPABILITY);
    }

    @Inject(method = "clearTargetSequences", at = @At("TAIL"))
    private static void totem$clearHorse(UUID targetId, CallbackInfo ci) {
        ObserverHorseRelayManager.clearTarget(targetId);
    }
}
