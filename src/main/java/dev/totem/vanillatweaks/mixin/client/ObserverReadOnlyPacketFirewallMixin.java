package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverReadOnlyPacketFirewall;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ObserverReadOnlyPacketFirewallMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void totem$suppressObserverUiActions(Packet<?> packet, CallbackInfo ci) {
        if (ObserverReadOnlyPacketFirewall.suppress(packet)) ci.cancel();
    }
}
