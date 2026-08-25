package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverCrafterScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverSignScreenPayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverCrafterPayloadsTest {
    @Test
    void crafterUsesDistinctVersionedExtensionCapability() {
        assertEquals(1, ObserverCrafterScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1L << 19, ObserverCrafterScreenPayloads.CAPABILITY);
        assertNotEquals(ObserverSignScreenPayloads.CAPABILITY, ObserverCrafterScreenPayloads.CAPABILITY);
        assertEquals("crafter", ObserverCrafterScreenPayloads.FAMILY_ID);
        assertTrue(ObserverCrafterScreenPayloads.CrafterState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverCrafterScreenPayloads.CrafterRelay.TYPE.id().getPath().endsWith("_v1"));
    }
}
