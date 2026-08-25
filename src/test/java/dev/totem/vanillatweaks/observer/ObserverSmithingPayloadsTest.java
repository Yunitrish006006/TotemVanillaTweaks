package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverBrewingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverSmithingScreenPayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverSmithingPayloadsTest {
    @Test
    void smithingUsesDistinctVersionedExtensionCapability() {
        assertEquals(1, ObserverSmithingScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1L << 12, ObserverSmithingScreenPayloads.CAPABILITY);
        assertNotEquals(ObserverBrewingScreenPayloads.CAPABILITY, ObserverSmithingScreenPayloads.CAPABILITY);
        assertEquals("smithing", ObserverSmithingScreenPayloads.FAMILY_ID);
        assertTrue(ObserverSmithingScreenPayloads.SmithingState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverSmithingScreenPayloads.SmithingRelay.TYPE.id().getPath().endsWith("_v1"));
    }
}
