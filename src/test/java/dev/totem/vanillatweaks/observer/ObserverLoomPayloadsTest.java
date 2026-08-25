package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverGrindstoneScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverLoomScreenPayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverLoomPayloadsTest {
    @Test
    void loomUsesDistinctVersionedExtensionCapability() {
        assertEquals(1, ObserverLoomScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1L << 15, ObserverLoomScreenPayloads.CAPABILITY);
        assertNotEquals(ObserverGrindstoneScreenPayloads.CAPABILITY, ObserverLoomScreenPayloads.CAPABILITY);
        assertEquals("loom", ObserverLoomScreenPayloads.FAMILY_ID);
        assertTrue(ObserverLoomScreenPayloads.LoomState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverLoomScreenPayloads.LoomRelay.TYPE.id().getPath().endsWith("_v1"));
    }
}
