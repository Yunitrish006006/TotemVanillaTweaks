package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverCrafterScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNexusDeathNodeAdminPayloads;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverNexusDeathNodeAdminPayloadsTest {
    @Test
    void deathNodeAdminUsesDistinctVersionedCapabilityWithoutConfirmationToken() {
        assertEquals(1, ObserverNexusDeathNodeAdminPayloads.PROTOCOL_VERSION);
        assertEquals(1L << 20, ObserverNexusDeathNodeAdminPayloads.CAPABILITY);
        assertNotEquals(ObserverCrafterScreenPayloads.CAPABILITY, ObserverNexusDeathNodeAdminPayloads.CAPABILITY);
        assertEquals("nexus_death_node_admin", ObserverNexusDeathNodeAdminPayloads.FAMILY_ID);
        assertTrue(ObserverNexusDeathNodeAdminPayloads.AdminState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverNexusDeathNodeAdminPayloads.AdminRelay.TYPE.id().getPath().endsWith("_v1"));

        for (Class<?> type : new Class<?>[]{
                ObserverNexusDeathNodeAdminPayloads.AdminState.class,
                ObserverNexusDeathNodeAdminPayloads.AdminRelay.class
        }) {
            assertFalse(Arrays.stream(type.getRecordComponents())
                    .map(RecordComponent::getName)
                    .anyMatch(name -> name.toLowerCase().contains("token")),
                    "Observer death-node admin transport must not contain confirmation tokens");
        }
    }
}
