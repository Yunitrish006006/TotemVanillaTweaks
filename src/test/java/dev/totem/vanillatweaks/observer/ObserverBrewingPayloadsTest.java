package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverBrewingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverVillagersWoodcutterPayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverBrewingPayloadsTest {
    @Test
    void brewingUsesDistinctVersionedExtensionCapability() {
        assertEquals(1, ObserverBrewingScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1L << 11, ObserverBrewingScreenPayloads.CAPABILITY);
        assertNotEquals(ObserverVillagersWoodcutterPayloads.CAPABILITY, ObserverBrewingScreenPayloads.CAPABILITY);
        assertEquals("brewing", ObserverBrewingScreenPayloads.FAMILY_ID);
        assertTrue(ObserverBrewingScreenPayloads.BrewingState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverBrewingScreenPayloads.BrewingRelay.TYPE.id().getPath().endsWith("_v1"));
    }
}
