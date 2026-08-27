package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverSmithingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStonecutterScreenPayloads;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverStonecutterPayloadsTest {
    @Test
    void stonecutterUsesDistinctVersionedExtensionCapability() {
        assertEquals(2, ObserverStonecutterScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1L << 13, ObserverStonecutterScreenPayloads.CAPABILITY);
        assertNotEquals(ObserverSmithingScreenPayloads.CAPABILITY, ObserverStonecutterScreenPayloads.CAPABILITY);
        assertEquals("stonecutter", ObserverStonecutterScreenPayloads.FAMILY_ID);
        assertTrue(ObserverStonecutterScreenPayloads.StonecutterState.TYPE.id().getPath().endsWith("_v2"));
        assertTrue(ObserverStonecutterScreenPayloads.StonecutterRelay.TYPE.id().getPath().endsWith("_v2"));
        assertEquals(512, ObserverStonecutterScreenPayloads.MAX_RECIPES);
    }

    @Test
    void validatorRequiresVisibleOutputsAndCoherentViewport() throws Exception {
        List<ObserverStonecutterScreenPayloads.RecipeState> recipes = List.of(
                new ObserverStonecutterScreenPayloads.RecipeState(
                        0, "minecraft:stone_bricks", "minecraft:stone_bricks", 1, 0));
        List<ObserverNativeScreenPayloads.SlotState> slots = new ArrayList<>();
        for (int i = 0; i < 38; i++) {
            slots.add(new ObserverNativeScreenPayloads.SlotState(i, 8, 8,
                    i == 0 ? "minecraft:stone" : i == 1 ? "minecraft:stone_bricks" : "",
                    i <= 1 ? 1 : 0, 0));
        }
        var state = new ObserverStonecutterScreenPayloads.StonecutterState(
                ObserverStonecutterScreenPayloads.PROTOCOL_VERSION, 1L, true,
                ObserverStonecutterScreenPayloads.FAMILY_ID,
                ObserverStonecutterScreenPayloads.SCREEN_CLASS, "Stonecutter",
                0, 1, 0, 0.0F, true, true, true, recipes, List.copyOf(slots));
        Method valid = ObserverStonecutterRelayManager.class.getDeclaredMethod(
                "valid", ObserverStonecutterScreenPayloads.StonecutterState.class);
        valid.setAccessible(true);
        assertTrue((boolean) valid.invoke(null, state));
    }
}
