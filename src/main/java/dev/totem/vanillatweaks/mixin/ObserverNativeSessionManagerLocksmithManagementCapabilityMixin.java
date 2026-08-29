package dev.totem.vanillatweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.totem.vanillatweaks.network.ObserverLocksmithManagementPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverOwnedScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverOwnedScreenProtocols;
import dev.totem.vanillatweaks.observer.ObserverLocksmithManagementRelayManager;
import dev.totem.vanillatweaks.observer.ObserverNativeSessionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Adds TotemLocksmith management semantics to Observer negotiation and cleanup. */
@Mixin(value = ObserverNativeSessionManager.class, remap = false)
public abstract class ObserverNativeSessionManagerLocksmithManagementCapabilityMixin {
    @ModifyReturnValue(method = "negotiatedScreenCapabilities", at = @At("RETURN"))
    private static long totem$includeLocksmithManagement(long original, ServerPlayer observer) {
        if (!ServerPlayNetworking.canSend(observer, ObserverOwnedScreenPayloads.Relay.TYPE)
                || !ObserverNativeSessionManager.ownedProviderAdvertises(observer,
                ObserverLocksmithManagementPayloads.FAMILY_ID,
                ObserverOwnedScreenProtocols.expected(ObserverLocksmithManagementPayloads.FAMILY_ID))) return original;
        return ObserverNativeScreenPayloads.sanitizeCapabilities(original | ObserverLocksmithManagementPayloads.CAPABILITY);
    }

    @Inject(method = "clearTargetSequences", at = @At("TAIL"))
    private static void totem$clearLocksmithManagementSequence(UUID targetId, CallbackInfo ci) {
        ObserverLocksmithManagementRelayManager.clearTarget(targetId);
    }
}
