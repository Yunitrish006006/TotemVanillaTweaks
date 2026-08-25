package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverBeaconScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverCartographyScreenPayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverBeaconPayloadsTest {
    @Test
    void beaconUsesDistinctVersionedExtensionCapability() {
        assertEquals(1, ObserverBeaconScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1L << 17, ObserverBeaconScreenPayloads.CAPABILITY);
        assertNotEquals(ObserverCartographyScreenPayloads.CAPABILITY, ObserverBeaconScreenPayloads.CAPABILITY);
        assertEquals("beacon", ObserverBeaconScreenPayloads.FAMILY_ID);
        assertTrue(ObserverBeaconScreenPayloads.BeaconState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverBeaconScreenPayloads.BeaconRelay.TYPE.id().getPath().endsWith("_v1"));
    }
}
