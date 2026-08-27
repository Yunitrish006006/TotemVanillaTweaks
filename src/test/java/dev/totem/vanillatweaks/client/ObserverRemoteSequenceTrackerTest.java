package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.network.ObserverAdvancementsScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverBeaconScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverBrewingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverCartographyScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverCrafterScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverGrindstoneScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverLocksmithManagementPayloads;
import dev.totem.vanillatweaks.network.ObserverLoomScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNexusDeathNodeAdminPayloads;
import dev.totem.vanillatweaks.network.ObserverSignScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverSmithingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStatsScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStonecutterScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverVillagersWoodcutterPayloads;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObserverRemoteSequenceTrackerTest {
    private static final List<String> ALL_SEMANTIC_FAMILIES = List.of(
            ObserverNativeScreenPayloads.FAMILY_CONTAINER_SLOTS,
            ObserverNativeScreenPayloads.FAMILY_FURNACE,
            ObserverNativeScreenPayloads.FAMILY_BOOK,
            ObserverNativeScreenPayloads.FAMILY_CRAFTING,
            ObserverNativeScreenPayloads.FAMILY_MERCHANT,
            ObserverNativeScreenPayloads.FAMILY_ANVIL,
            ObserverNativeScreenPayloads.FAMILY_ENCHANTING,
            ObserverNativeScreenPayloads.FAMILY_REMNANT_BACKPACK,
            ObserverNativeScreenPayloads.FAMILY_AUTOMATA_COPPER_GOLEM,
            ObserverNativeScreenPayloads.FAMILY_NEXUS,
            ObserverVillagersWoodcutterPayloads.FAMILY_ID,
            ObserverBrewingScreenPayloads.FAMILY_ID,
            ObserverSmithingScreenPayloads.FAMILY_ID,
            ObserverStonecutterScreenPayloads.FAMILY_ID,
            ObserverGrindstoneScreenPayloads.FAMILY_ID,
            ObserverLoomScreenPayloads.FAMILY_ID,
            ObserverCartographyScreenPayloads.FAMILY_ID,
            ObserverBeaconScreenPayloads.FAMILY_ID,
            ObserverSignScreenPayloads.FAMILY_ID,
            ObserverCrafterScreenPayloads.FAMILY_ID,
            ObserverNexusDeathNodeAdminPayloads.FAMILY_ID,
            ObserverLocksmithManagementPayloads.FAMILY_ID,
            ObserverAdvancementsScreenPayloads.FAMILY_ID,
            ObserverStatsScreenPayloads.FAMILY_ID
    );

    @AfterEach
    void reset() {
        ObserverRemoteSequenceTracker.beginSession();
    }

    @Test
    void everySemanticFamilyRejectsSameTargetStalePackets() {
        UUID target = UUID.randomUUID();
        assertEquals(24, ALL_SEMANTIC_FAMILIES.size());
        assertEquals(24, Set.copyOf(ALL_SEMANTIC_FAMILIES).size());

        for (String family : ALL_SEMANTIC_FAMILIES) {
            ObserverRemoteSequenceTracker.beginSession();
            assertTrue(ObserverRemoteSequenceTracker.accept(family, target, 100L), family);
            assertFalse(ObserverRemoteSequenceTracker.accept(family, target, 100L), family);
            assertFalse(ObserverRemoteSequenceTracker.accept(family, target, 99L), family);
            assertTrue(ObserverRemoteSequenceTracker.accept(family, target, 101L), family);
        }
    }

    @Test
    void everySemanticFamilyAcceptsLowerSequenceForDifferentTargetAndReconnect() {
        UUID firstTarget = UUID.randomUUID();
        UUID secondTarget = UUID.randomUUID();

        for (String family : ALL_SEMANTIC_FAMILIES) {
            ObserverRemoteSequenceTracker.beginSession();
            assertTrue(ObserverRemoteSequenceTracker.accept(family, firstTarget, 100L), family);
            assertTrue(ObserverRemoteSequenceTracker.accept(family, secondTarget, 1L), family);
            assertFalse(ObserverRemoteSequenceTracker.accept(family, secondTarget, 1L), family);

            ObserverRemoteSequenceTracker.beginSession();
            assertTrue(ObserverRemoteSequenceTracker.accept(family, secondTarget, 1L), family);
        }
    }

    @Test
    void familiesHaveIndependentSequenceStreams() {
        UUID target = UUID.randomUUID();
        ObserverRemoteSequenceTracker.beginSession();
        for (String family : ALL_SEMANTIC_FAMILIES) {
            assertTrue(ObserverRemoteSequenceTracker.accept(family, target, 1L), family);
        }
    }
}
