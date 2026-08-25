package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverAutomataCopperGolemPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverAutomataCopperGolemPrivacyTest {
    private static final String SENTINEL_SECRET = "observer-sentinel-secret-9f3d2a";
    private static final String ARBITRARY_TOKEN = "arbitrary-private-text";

    @Test
    void targetTokensPreventEditorSecretsFromEnteringEncodedBytes() {
        var state = state(SENTINEL_SECRET, SENTINEL_SECRET, SENTINEL_SECRET,
                SENTINEL_SECRET, SENTINEL_SECRET, SENTINEL_SECRET);

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ObserverAutomataCopperGolemPayloads.CopperGolemState.CODEC.encode(buffer, state);
            byte[] encoded = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), encoded);
            String wireText = new String(encoded, StandardCharsets.UTF_8);
            assertFalse(wireText.contains(SENTINEL_SECRET), "encoded Observer payload leaked sentinel editor text");
            assertTrue(wireText.contains(ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED));
            assertTrue(wireText.contains(ObserverAutomataCopperGolemPayloads.TOKEN_VALID));

            var decoded = ObserverAutomataCopperGolemPayloads.CopperGolemState.CODEC.decode(buffer);
            assertEquals(ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED, decoded.editorApiUrl());
            assertEquals(ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED, decoded.editorModel());
            assertEquals(ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED, decoded.editorGatheringPrompt());
            assertEquals(ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED, decoded.editorBindingPrompt());
            assertEquals(ObserverAutomataCopperGolemPayloads.TOKEN_VALID, decoded.cacheValueText());
            assertEquals(ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED,
                    decoded.bindings().getFirst().llmPrompt());
        } finally {
            buffer.release();
        }
    }

    @Test
    void relayValidatorRejectsEveryArbitraryEditorToken() {
        String configured = ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED;
        String valid = ObserverAutomataCopperGolemPayloads.TOKEN_VALID;
        assertTrue(ObserverAutomataCopperGolemRelayManager.valid(
                state(configured, configured, configured, configured, valid, configured)));

        assertFalse(ObserverAutomataCopperGolemRelayManager.valid(
                state(ARBITRARY_TOKEN, configured, configured, configured, valid, configured)));
        assertFalse(ObserverAutomataCopperGolemRelayManager.valid(
                state(configured, ARBITRARY_TOKEN, configured, configured, valid, configured)));
        assertFalse(ObserverAutomataCopperGolemRelayManager.valid(
                state(configured, configured, ARBITRARY_TOKEN, configured, valid, configured)));
        assertFalse(ObserverAutomataCopperGolemRelayManager.valid(
                state(configured, configured, configured, ARBITRARY_TOKEN, valid, configured)));
        assertFalse(ObserverAutomataCopperGolemRelayManager.valid(
                state(configured, configured, configured, configured, ARBITRARY_TOKEN, configured)));
        assertFalse(ObserverAutomataCopperGolemRelayManager.valid(
                state(configured, configured, configured, configured, valid, ARBITRARY_TOKEN)));
        assertFalse(ObserverAutomataCopperGolemRelayManager.valid(
                state(ObserverAutomataCopperGolemPayloads.TOKEN_VALID,
                        configured, configured, configured, valid, configured)));
        assertFalse(ObserverAutomataCopperGolemRelayManager.valid(
                state(configured, configured, configured, configured,
                        ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED, configured)));
    }

    @Test
    void relayValidatorRejectsFreeTextDisguisedAsSemanticMetadata() {
        String configured = ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED;
        String valid = ObserverAutomataCopperGolemPayloads.TOKEN_VALID;
        var state = state(configured, configured, configured, configured, valid, configured);
        var invalidBinding = new ObserverAutomataCopperGolemPayloads.BindingState(
                state.bindings().getFirst().dimension(), state.bindings().getFirst().x(),
                state.bindings().getFirst().y(), state.bindings().getFirst().z(),
                "not an identifier", state.bindings().getFirst().itemId(), true, true, true, configured,
                0, 0, List.of(), List.of(), List.of(), List.of());
        var invalid = new ObserverAutomataCopperGolemPayloads.CopperGolemState(
                state.protocolVersion(), state.sequence(), state.open(), state.familyId(), state.screenClass(),
                state.title(), state.running(), state.mode(), state.activity(), state.tab(), state.selectedBinding(),
                state.bindingScroll(), state.bindingDetailVisible(), state.filterTextEntryVisible(),
                state.filterTextEntryAllowed(), state.cacheValueIsTag(), state.targetBlocksVisible(),
                state.fuelItemId(), state.fuelCount(), state.fuelTicks(), state.infiniteFuel(), state.toolItemId(),
                state.toolCount(), state.toolDamage(), state.toolMaxDamage(), state.storageItemId(),
                state.storageCount(), state.editorApiUrl(), state.apiKeyConfigured(), state.editorModel(),
                state.llmActiveCount(), state.editorGatheringPrompt(), state.editorBindingPrompt(),
                state.cacheValueText(), state.sourceContainer(), state.gatheringArea(), state.gatheringManualTargets(),
                state.gatheringLlmEnabled(), state.gatheringLlmCachedBlockIds(), state.gatheringLlmCachedTags(),
                state.gatheringLlmAllowedBlockIds(), state.gatheringLlmDeniedBlockIds(),
                state.gatheringLlmAllowedTags(), state.gatheringLlmDeniedTags(), List.of(invalidBinding), state.slots());
        assertFalse(ObserverAutomataCopperGolemRelayManager.valid(invalid));
    }

    private static ObserverAutomataCopperGolemPayloads.CopperGolemState state(
            String editorApiUrl, String editorModel, String editorGatheringPrompt,
            String editorBindingPrompt, String cacheValueText, String bindingPrompt) {
        var binding = new ObserverAutomataCopperGolemPayloads.BindingState(
                "minecraft:overworld", 10, 64, -5, "minecraft:chest", "minecraft:chest",
                true, true, true, bindingPrompt, 3, 1,
                List.of("minecraft:iron_ingot"), List.of("minecraft:dirt"), List.of("c:ingots"), List.of());
        return new ObserverAutomataCopperGolemPayloads.CopperGolemState(
                ObserverAutomataCopperGolemPayloads.PROTOCOL_VERSION,
                1L,
                true,
                ObserverNativeScreenPayloads.FAMILY_AUTOMATA_COPPER_GOLEM,
                ObserverAutomataCopperGolemPayloads.SCREEN_CLASS,
                ObserverAutomataCopperGolemPayloads.SCREEN_TITLE,
                true,
                "sorting",
                "sorting",
                "bindings",
                0,
                0,
                true,
                true,
                true,
                false,
                false,
                "minecraft:coal",
                8,
                1200,
                false,
                "minecraft:iron_pickaxe",
                1,
                12,
                250,
                "minecraft:chest",
                1,
                editorApiUrl,
                true,
                editorModel,
                1,
                editorGatheringPrompt,
                editorBindingPrompt,
                cacheValueText,
                binding,
                null,
                List.of(),
                false,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(binding),
                List.of());
    }
}
