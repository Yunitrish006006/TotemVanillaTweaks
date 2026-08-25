package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverLocksmithManagementPayloads;
import dev.totem.vanillatweaks.network.ObserverNexusDeathNodeAdminPayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverLocksmithManagementPayloadsTest {
    @Test
    void locksmithManagementUsesDistinctVersionedCapability() {
        assertEquals(1, ObserverLocksmithManagementPayloads.PROTOCOL_VERSION);
        assertEquals(1L << 21, ObserverLocksmithManagementPayloads.CAPABILITY);
        assertNotEquals(ObserverNexusDeathNodeAdminPayloads.CAPABILITY, ObserverLocksmithManagementPayloads.CAPABILITY);
        assertEquals("locksmith_management", ObserverLocksmithManagementPayloads.FAMILY_ID);
        assertTrue(ObserverLocksmithManagementPayloads.ManagementState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverLocksmithManagementPayloads.ManagementRelay.TYPE.id().getPath().endsWith("_v1"));
        assertEquals(32, ObserverLocksmithManagementPayloads.MAX_ROWS);
    }
}
