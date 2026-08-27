package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverGrindstoneScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverLoomScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverLoomPayloadsTest {
    @Test
    void loomUsesDistinctVersionedExtensionCapability() {
        assertEquals(2, ObserverLoomScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1L << 15, ObserverLoomScreenPayloads.CAPABILITY);
        assertNotEquals(ObserverGrindstoneScreenPayloads.CAPABILITY, ObserverLoomScreenPayloads.CAPABILITY);
        assertEquals("loom", ObserverLoomScreenPayloads.FAMILY_ID);
        assertTrue(ObserverLoomScreenPayloads.LoomState.TYPE.id().getPath().endsWith("_v2"));
        assertTrue(ObserverLoomScreenPayloads.LoomRelay.TYPE.id().getPath().endsWith("_v2"));
    }

    @Test
    void validatorRequiresV2MenuOrdinalsAndBoundedBannerSemantics() throws Exception {
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            boolean result = i == 3;
            slots.add(new ObserverNativeScreenPayloads.SlotState(
                    i, 8 + (i % 9) * 18, 8 + (i / 9) * 18,
                    result ? "minecraft:white_banner" : "", result ? 1 : 0, 0));
        }
        ObserverLoomScreenPayloads.LoomState state = new ObserverLoomScreenPayloads.LoomState(
                ObserverLoomScreenPayloads.PROTOCOL_VERSION, 1L, true,
                ObserverLoomScreenPayloads.FAMILY_ID, ObserverLoomScreenPayloads.SCREEN_CLASS,
                "Loom", 0, 0, 0.0F, true, false, true, 0,
                List.of(new ObserverLoomScreenPayloads.PatternState("minecraft:base", "minecraft:base")),
                List.of(new ObserverLoomScreenPayloads.BannerLayerState("minecraft:base", 14)),
                List.copyOf(slots));
        Method valid = ObserverLoomRelayManager.class.getDeclaredMethod(
                "valid", ObserverLoomScreenPayloads.LoomState.class);
        valid.setAccessible(true);
        assertTrue((boolean) valid.invoke(null, state));

        List<ObserverNativeScreenPayloads.SlotState> legacy = new ArrayList<>(slots);
        legacy.set(3, new ObserverNativeScreenPayloads.SlotState(
                0, legacy.get(3).x(), legacy.get(3).y(), "minecraft:white_banner", 1, 0));
        ObserverLoomScreenPayloads.LoomState duplicateIndex = new ObserverLoomScreenPayloads.LoomState(
                state.protocolVersion(), state.sequence(), state.open(), state.familyId(), state.screenClass(),
                state.title(), state.selectedPatternIndex(), state.startRow(), state.scrollOffset(),
                state.displayPatterns(), state.hasMaxPatterns(), state.resultAvailable(), state.resultBaseColorId(),
                state.patterns(), state.resultLayers(), List.copyOf(legacy));
        assertFalse((boolean) valid.invoke(null, duplicateIndex));
    }
}
