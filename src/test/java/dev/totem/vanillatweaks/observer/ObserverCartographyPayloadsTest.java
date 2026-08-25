package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverCartographyScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverLoomScreenPayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverCartographyPayloadsTest {
    @Test
    void cartographyUsesDistinctVersionedExtensionCapability() {
        assertEquals(1, ObserverCartographyScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1L << 16, ObserverCartographyScreenPayloads.CAPABILITY);
        assertNotEquals(ObserverLoomScreenPayloads.CAPABILITY, ObserverCartographyScreenPayloads.CAPABILITY);
        assertEquals("cartography", ObserverCartographyScreenPayloads.FAMILY_ID);
        assertTrue(ObserverCartographyScreenPayloads.CartographyState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverCartographyScreenPayloads.CartographyRelay.TYPE.id().getPath().endsWith("_v1"));
    }
}
