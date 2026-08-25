package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverVillagersWoodcutterPayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverVillagersWoodcutterPayloadsTest {
    @Test
    void woodcutterUsesVersionedOptionalSemanticIdentifiers() {
        assertEquals(1, ObserverVillagersWoodcutterPayloads.PROTOCOL_VERSION);
        assertEquals(1L << 10, ObserverVillagersWoodcutterPayloads.CAPABILITY);
        assertEquals("villagers_woodcutter", ObserverVillagersWoodcutterPayloads.FAMILY_ID);
        assertTrue(ObserverVillagersWoodcutterPayloads.WoodcutterState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverVillagersWoodcutterPayloads.WoodcutterRelay.TYPE.id().getPath().endsWith("_v1"));
        assertEquals(0L, ObserverNativeScreenPayloads.KNOWN_CAPABILITIES & ObserverVillagersWoodcutterPayloads.CAPABILITY);
    }
}
