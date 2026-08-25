package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverGrindstoneScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStonecutterScreenPayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverGrindstonePayloadsTest {
    @Test
    void grindstoneUsesDistinctVersionedExtensionCapability() {
        assertEquals(1, ObserverGrindstoneScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1L << 14, ObserverGrindstoneScreenPayloads.CAPABILITY);
        assertNotEquals(ObserverStonecutterScreenPayloads.CAPABILITY, ObserverGrindstoneScreenPayloads.CAPABILITY);
        assertEquals("grindstone", ObserverGrindstoneScreenPayloads.FAMILY_ID);
        assertTrue(ObserverGrindstoneScreenPayloads.GrindstoneState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverGrindstoneScreenPayloads.GrindstoneRelay.TYPE.id().getPath().endsWith("_v1"));
    }
}
