package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverAdvancementsScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStatsScreenPayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverStatsPayloadsTest {
    @Test
    void statsUsesDistinctVersionedCapability() {
        assertEquals(1, ObserverStatsScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1L << 23, ObserverStatsScreenPayloads.CAPABILITY);
        assertNotEquals(ObserverAdvancementsScreenPayloads.CAPABILITY, ObserverStatsScreenPayloads.CAPABILITY);
        assertEquals("stats", ObserverStatsScreenPayloads.FAMILY_ID);
        assertTrue(ObserverStatsScreenPayloads.StatsState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverStatsScreenPayloads.StatsRelay.TYPE.id().getPath().endsWith("_v1"));
        assertEquals(192, ObserverStatsScreenPayloads.MAX_GENERAL_ROWS);
        assertEquals(256, ObserverStatsScreenPayloads.MAX_ITEM_ROWS);
        assertEquals(160, ObserverStatsScreenPayloads.MAX_MOB_ROWS);
    }
}
