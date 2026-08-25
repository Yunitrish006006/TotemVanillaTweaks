package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverAdvancementsScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverLocksmithManagementPayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverAdvancementsPayloadsTest {
    @Test
    void advancementsUsesDistinctVersionedCapability() {
        assertEquals(1, ObserverAdvancementsScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1L << 22, ObserverAdvancementsScreenPayloads.CAPABILITY);
        assertNotEquals(ObserverLocksmithManagementPayloads.CAPABILITY, ObserverAdvancementsScreenPayloads.CAPABILITY);
        assertEquals("advancements", ObserverAdvancementsScreenPayloads.FAMILY_ID);
        assertTrue(ObserverAdvancementsScreenPayloads.AdvancementsState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverAdvancementsScreenPayloads.AdvancementsRelay.TYPE.id().getPath().endsWith("_v1"));
        assertEquals(32, ObserverAdvancementsScreenPayloads.MAX_TABS);
        assertEquals(256, ObserverAdvancementsScreenPayloads.MAX_NODES);
    }
}
