package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverSmithingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStonecutterScreenPayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverStonecutterPayloadsTest {
    @Test
    void stonecutterUsesDistinctVersionedExtensionCapability() {
        assertEquals(1, ObserverStonecutterScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1L << 13, ObserverStonecutterScreenPayloads.CAPABILITY);
        assertNotEquals(ObserverSmithingScreenPayloads.CAPABILITY, ObserverStonecutterScreenPayloads.CAPABILITY);
        assertEquals("stonecutter", ObserverStonecutterScreenPayloads.FAMILY_ID);
        assertTrue(ObserverStonecutterScreenPayloads.StonecutterState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverStonecutterScreenPayloads.StonecutterRelay.TYPE.id().getPath().endsWith("_v1"));
    }
}
