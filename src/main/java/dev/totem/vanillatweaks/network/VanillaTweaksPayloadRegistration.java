package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.inventory.ContainerSortService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class VanillaTweaksPayloadRegistration {
    private VanillaTweaksPayloadRegistration() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(
                SortBackpackPayload.TYPE,
                SortBackpackPayload.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(
                SortBackpackPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        ContainerSortService.sortOpenContainer(context.player(), payload.target()))
        );
    }
}
