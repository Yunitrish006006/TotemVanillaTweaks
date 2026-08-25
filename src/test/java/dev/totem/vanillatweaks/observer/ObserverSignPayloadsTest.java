package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverBeaconScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverSignScreenPayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverSignPayloadsTest {
    @Test
    void signUsesDistinctVersionedExtensionCapability() {
        assertEquals(1, ObserverSignScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1L << 18, ObserverSignScreenPayloads.CAPABILITY);
        assertNotEquals(ObserverBeaconScreenPayloads.CAPABILITY, ObserverSignScreenPayloads.CAPABILITY);
        assertEquals("sign", ObserverSignScreenPayloads.FAMILY_ID);
        assertEquals(4, ObserverSignScreenPayloads.LINE_COUNT);
        assertTrue(ObserverSignScreenPayloads.SignState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverSignScreenPayloads.SignRelay.TYPE.id().getPath().endsWith("_v1"));
    }
}
