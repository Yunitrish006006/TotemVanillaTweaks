package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverCraftingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverLoomScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStonecutterScreenPayloads;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ObserverUiBehaviorPayloadCodecTest {
    @Test
    void craftingV2RoundTripsRecipeBookAndBoundedEffectSemanticsWithoutSearchDraft() {
        String privateDraft = "never-transmit-this-recipe-search-draft";
        var state = new ObserverCraftingScreenPayloads.CraftingState(
                ObserverCraftingScreenPayloads.PROTOCOL_VERSION, 9L, true,
                ObserverNativeScreenPayloads.FAMILY_CRAFTING,
                ObserverCraftingScreenPayloads.VARIANT_PLAYER_2X2,
                "net.minecraft.client.gui.screens.inventory.InventoryScreen", "Inventory",
                176, 166, 10, 20, 2, 2, 0,
                true, false, true, true, "search:crafting", 1, 3, true,
                List.of(new ObserverCraftingScreenPayloads.EffectState(
                        "minecraft:speed", 1, 1_200, false, true, true)),
                List.of(new ObserverNativeScreenPayloads.SlotState(
                        0, 154, 28, "minecraft:crafting_table", 1, 0)));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ObserverCraftingScreenPayloads.CraftingState.CODEC.encode(buffer, state);
            byte[] encoded = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), encoded);
            assertFalse(new String(encoded, StandardCharsets.UTF_8).contains(privateDraft));
            assertEquals(state, ObserverCraftingScreenPayloads.CraftingState.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void loomV2RoundTripsPatternAssetsScrollAndResultLayers() {
        var state = new ObserverLoomScreenPayloads.LoomState(
                ObserverLoomScreenPayloads.PROTOCOL_VERSION, 10L, true,
                ObserverLoomScreenPayloads.FAMILY_ID, ObserverLoomScreenPayloads.SCREEN_CLASS, "Loom",
                0, 1, 1.0F, true, false, true, 0,
                List.of(new ObserverLoomScreenPayloads.PatternState(
                        "minecraft:stripe_bottom", "minecraft:stripe_bottom")),
                List.of(new ObserverLoomScreenPayloads.BannerLayerState("minecraft:stripe_bottom", 14)),
                List.of(new ObserverNativeScreenPayloads.SlotState(
                        3, 143, 57, "minecraft:white_banner", 1, 0)));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ObserverLoomScreenPayloads.LoomState.CODEC.encode(buffer, state);
            assertEquals(state, ObserverLoomScreenPayloads.LoomState.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void stonecutterV2RoundTripsBoundedOutputRecipesAndViewport() {
        var state = new ObserverStonecutterScreenPayloads.StonecutterState(
                ObserverStonecutterScreenPayloads.PROTOCOL_VERSION, 11L, true,
                ObserverStonecutterScreenPayloads.FAMILY_ID,
                ObserverStonecutterScreenPayloads.SCREEN_CLASS, "Stonecutter",
                0, 1, 0, 0.0F, true, true, true,
                List.of(new ObserverStonecutterScreenPayloads.RecipeState(
                        0, "minecraft:stone_bricks", "minecraft:stone_bricks", 1, 0)),
                List.of(new ObserverNativeScreenPayloads.SlotState(
                        1, 143, 33, "minecraft:stone_bricks", 1, 0)));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ObserverStonecutterScreenPayloads.StonecutterState.CODEC.encode(buffer, state);
            assertEquals(state, ObserverStonecutterScreenPayloads.StonecutterState.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
