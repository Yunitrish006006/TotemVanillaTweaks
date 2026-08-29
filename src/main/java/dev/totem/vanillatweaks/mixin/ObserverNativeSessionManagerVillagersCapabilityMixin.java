package dev.totem.vanillatweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverVillagersWoodcutterPayloads;
import dev.totem.vanillatweaks.network.ObserverOwnedScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverOwnedScreenProtocols;
import dev.totem.vanillatweaks.observer.ObserverNativeSessionManager;
import dev.totem.vanillatweaks.observer.ObserverVillagersWoodcutterRelayManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Adds optional TotemVillagers Woodcutter support to Observer negotiation and cleanup. */
@Mixin(value = ObserverNativeSessionManager.class, remap = false)
public abstract class ObserverNativeSessionManagerVillagersCapabilityMixin {
    @ModifyReturnValue(method = "negotiatedScreenCapabilities", at = @At("RETURN"))
    private static long totem$includeWoodcutterCapability(long original, ServerPlayer observer) {
        boolean familyAvailable = FabricLoader.getInstance().isModLoaded("totem-villagers")
                || Boolean.getBoolean("totem.observer.e2e.enabled");
        if (!familyAvailable || !ServerPlayNetworking.canSend(observer, ObserverOwnedScreenPayloads.Relay.TYPE)
                || !ObserverNativeSessionManager.ownedProviderAdvertises(observer,
                ObserverVillagersWoodcutterPayloads.FAMILY_ID,
                ObserverOwnedScreenProtocols.expected(ObserverVillagersWoodcutterPayloads.FAMILY_ID))) {
            return original;
        }
        return ObserverNativeScreenPayloads.sanitizeCapabilities(
                original | ObserverVillagersWoodcutterPayloads.CAPABILITY);
    }

    @Inject(method = "clearTargetSequences", at = @At("TAIL"))
    private static void totem$clearWoodcutterSequence(UUID targetId, CallbackInfo ci) {
        ObserverVillagersWoodcutterRelayManager.clearTarget(targetId);
    }
}
