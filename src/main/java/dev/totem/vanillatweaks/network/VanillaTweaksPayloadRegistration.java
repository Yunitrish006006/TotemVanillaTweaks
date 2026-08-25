package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.inventory.ContainerSortService;
import dev.totem.vanillatweaks.observer.ObserverAutomataCopperGolemRelayManager;
import dev.totem.vanillatweaks.observer.ObserverBrewingRelayManager;
import dev.totem.vanillatweaks.observer.ObserverCartographyRelayManager;
import dev.totem.vanillatweaks.observer.ObserverGrindstoneRelayManager;
import dev.totem.vanillatweaks.observer.ObserverLoomRelayManager;
import dev.totem.vanillatweaks.observer.ObserverNativeSessionManager;
import dev.totem.vanillatweaks.observer.ObserverNexusRelayManager;
import dev.totem.vanillatweaks.observer.ObserverSessionManager;
import dev.totem.vanillatweaks.observer.ObserverSmithingRelayManager;
import dev.totem.vanillatweaks.observer.ObserverStonecutterRelayManager;
import dev.totem.vanillatweaks.observer.ObserverVillagersWoodcutterRelayManager;
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
        PayloadTypeRegistry.serverboundPlay().register(ObserverRemnantBackpackPayloads.BackpackState.TYPE, ObserverRemnantBackpackPayloads.BackpackState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverRemnantBackpackPayloads.BackpackRelay.TYPE, ObserverRemnantBackpackPayloads.BackpackRelay.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ObserverAutomataCopperGolemPayloads.CopperGolemState.TYPE, ObserverAutomataCopperGolemPayloads.CopperGolemState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverAutomataCopperGolemPayloads.CopperGolemRelay.TYPE, ObserverAutomataCopperGolemPayloads.CopperGolemRelay.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ObserverNexusScreenPayloads.NexusState.TYPE, ObserverNexusScreenPayloads.NexusState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverNexusScreenPayloads.NexusRelay.TYPE, ObserverNexusScreenPayloads.NexusRelay.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ObserverVillagersWoodcutterPayloads.WoodcutterState.TYPE, ObserverVillagersWoodcutterPayloads.WoodcutterState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverVillagersWoodcutterPayloads.WoodcutterRelay.TYPE, ObserverVillagersWoodcutterPayloads.WoodcutterRelay.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ObserverBrewingScreenPayloads.BrewingState.TYPE, ObserverBrewingScreenPayloads.BrewingState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverBrewingScreenPayloads.BrewingRelay.TYPE, ObserverBrewingScreenPayloads.BrewingRelay.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ObserverSmithingScreenPayloads.SmithingState.TYPE, ObserverSmithingScreenPayloads.SmithingState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverSmithingScreenPayloads.SmithingRelay.TYPE, ObserverSmithingScreenPayloads.SmithingRelay.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ObserverStonecutterScreenPayloads.StonecutterState.TYPE, ObserverStonecutterScreenPayloads.StonecutterState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverStonecutterScreenPayloads.StonecutterRelay.TYPE, ObserverStonecutterScreenPayloads.StonecutterRelay.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ObserverGrindstoneScreenPayloads.GrindstoneState.TYPE, ObserverGrindstoneScreenPayloads.GrindstoneState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverGrindstoneScreenPayloads.GrindstoneRelay.TYPE, ObserverGrindstoneScreenPayloads.GrindstoneRelay.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ObserverLoomScreenPayloads.LoomState.TYPE, ObserverLoomScreenPayloads.LoomState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverLoomScreenPayloads.LoomRelay.TYPE, ObserverLoomScreenPayloads.LoomRelay.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ObserverCartographyScreenPayloads.CartographyState.TYPE, ObserverCartographyScreenPayloads.CartographyState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ObserverCartographyScreenPayloads.CartographyRelay.TYPE, ObserverCartographyScreenPayloads.CartographyRelay.CODEC);

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
        ServerPlayNetworking.registerGlobalReceiver(ObserverRemnantBackpackPayloads.BackpackState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverNativeSessionManager.acceptRemnantBackpackState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ObserverAutomataCopperGolemPayloads.CopperGolemState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverAutomataCopperGolemRelayManager.acceptState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ObserverNexusScreenPayloads.NexusState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverNexusRelayManager.acceptState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ObserverVillagersWoodcutterPayloads.WoodcutterState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverVillagersWoodcutterRelayManager.acceptState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ObserverBrewingScreenPayloads.BrewingState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverBrewingRelayManager.acceptState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ObserverSmithingScreenPayloads.SmithingState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverSmithingRelayManager.acceptState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ObserverStonecutterScreenPayloads.StonecutterState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverStonecutterRelayManager.acceptState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ObserverGrindstoneScreenPayloads.GrindstoneState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverGrindstoneRelayManager.acceptState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ObserverLoomScreenPayloads.LoomState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverLoomRelayManager.acceptState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ObserverCartographyScreenPayloads.CartographyState.TYPE,
                (payload, context) -> context.server().execute(() -> ObserverCartographyRelayManager.acceptState(context.player(), payload)));
    }
}
