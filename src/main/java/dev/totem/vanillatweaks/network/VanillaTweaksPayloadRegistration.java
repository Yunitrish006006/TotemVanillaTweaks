package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.inventory.ContainerSortService;
import dev.totem.vanillatweaks.observer.ObserverNativeSessionManager;
import dev.totem.vanillatweaks.observer.ObserverSessionManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class VanillaTweaksPayloadRegistration {
    private VanillaTweaksPayloadRegistration() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(SortBackpackPayload.TYPE, SortBackpackPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                SortBackpackPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        ContainerSortService.sortOpenContainer(context.player(), payload.target()))
        );

        PayloadTypeRegistry.serverboundPlay().register(ObserverPayloads.ScreenState.TYPE, ObserverPayloads.ScreenState.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ObserverPayloads.Stop.TYPE, ObserverPayloads.Stop.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverPayloads.ScreenRelay.TYPE, ObserverPayloads.ScreenRelay.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(ObserverNativePayloads.NativeViewState.TYPE, ObserverNativePayloads.NativeViewState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverNativePayloads.NativeControl.TYPE, ObserverNativePayloads.NativeControl.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverNativePayloads.NativeSession.TYPE, ObserverNativePayloads.NativeSession.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverNativePayloads.NativeViewRelay.TYPE, ObserverNativePayloads.NativeViewRelay.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(ObserverNativeScreenPayloads.ContainerState.TYPE, ObserverNativeScreenPayloads.ContainerState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverNativeScreenPayloads.ContainerRelay.TYPE, ObserverNativeScreenPayloads.ContainerRelay.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ObserverNativeScreenPayloads.FurnaceState.TYPE, ObserverNativeScreenPayloads.FurnaceState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverNativeScreenPayloads.FurnaceRelay.TYPE, ObserverNativeScreenPayloads.FurnaceRelay.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ObserverBookScreenPayloads.BookState.TYPE, ObserverBookScreenPayloads.BookState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverBookScreenPayloads.BookRelay.TYPE, ObserverBookScreenPayloads.BookRelay.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ObserverCraftingScreenPayloads.CraftingState.TYPE, ObserverCraftingScreenPayloads.CraftingState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverCraftingScreenPayloads.CraftingRelay.TYPE, ObserverCraftingScreenPayloads.CraftingRelay.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ObserverMerchantScreenPayloads.MerchantState.TYPE, ObserverMerchantScreenPayloads.MerchantState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverMerchantScreenPayloads.MerchantRelay.TYPE, ObserverMerchantScreenPayloads.MerchantRelay.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ObserverAnvilScreenPayloads.AnvilState.TYPE, ObserverAnvilScreenPayloads.AnvilState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverAnvilScreenPayloads.AnvilRelay.TYPE, ObserverAnvilScreenPayloads.AnvilRelay.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ObserverEnchantingScreenPayloads.EnchantingState.TYPE, ObserverEnchantingScreenPayloads.EnchantingState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverEnchantingScreenPayloads.EnchantingRelay.TYPE, ObserverEnchantingScreenPayloads.EnchantingRelay.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ObserverPayloads.ScreenState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverSessionManager.acceptScreenState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ObserverPayloads.Stop.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverSessionManager.acceptStop(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(ObserverNativePayloads.NativeViewState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverNativeSessionManager.acceptViewState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ObserverNativeScreenPayloads.ContainerState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverNativeSessionManager.acceptContainerState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ObserverNativeScreenPayloads.FurnaceState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverNativeSessionManager.acceptFurnaceState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ObserverBookScreenPayloads.BookState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverNativeSessionManager.acceptBookState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ObserverCraftingScreenPayloads.CraftingState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverNativeSessionManager.acceptCraftingState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ObserverMerchantScreenPayloads.MerchantState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverNativeSessionManager.acceptMerchantState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ObserverAnvilScreenPayloads.AnvilState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverNativeSessionManager.acceptAnvilState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ObserverEnchantingScreenPayloads.EnchantingState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverNativeSessionManager.acceptEnchantingState(context.player(), payload)));
    }
}
