package dev.totem.vanillatweaks.observer;

import dev.totem.core.api.v1.client.observer.ObserverScreenSnapshot;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverOwnedProviderPolicy;
import dev.totem.vanillatweaks.network.ObserverOwnedScreenCapability;
import dev.totem.vanillatweaks.network.ObserverOwnedScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverOwnedScreenProtocols;
import dev.totem.vanillatweaks.network.ObserverScreenCapabilities;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ObserverOwnedScreenProtocolTest {
    @Test
    void newFeatureProviderDoesNotNeedCentralFamilyRegistration() {
        assertEquals(1, ObserverOwnedScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1L << 26, ObserverOwnedScreenCapability.CAPABILITY);

        var alchemy = new ObserverOwnedScreenPayloads.ProviderIdentity("alchemy_cauldron", 1);
        var providers = new ObserverOwnedScreenPayloads.ProviderSet(
                ObserverOwnedScreenPayloads.PROTOCOL_VERSION, List.of(alchemy));
        assertEquals(java.util.Set.of(alchemy), ObserverNativeSessionManager.validateOwnedProviderSet(providers));
        assertEquals(0L, ObserverScreenCapabilities.vanillaCapability("alchemy_cauldron"));

        assertNull(ObserverNativeSessionManager.validateOwnedProviderSet(new ObserverOwnedScreenPayloads.ProviderSet(
                2, List.of(alchemy))));
        assertNull(ObserverNativeSessionManager.validateOwnedProviderSet(new ObserverOwnedScreenPayloads.ProviderSet(
                ObserverOwnedScreenPayloads.PROTOCOL_VERSION, List.of(alchemy, alchemy))));
    }

    @Test
    void owningModulesCannotClaimObserverOwnedVanillaFamilies() {
        var furnace = new ObserverOwnedScreenPayloads.ProviderIdentity(
                ObserverNativeScreenPayloads.FAMILY_FURNACE, 1);
        assertTrue(ObserverScreenCapabilities.isReservedVanillaFamily(furnace.familyId()));
        assertFalse(ObserverOwnedProviderPolicy.validIdentity(furnace));
        assertNull(ObserverNativeSessionManager.validateOwnedProviderSet(new ObserverOwnedScreenPayloads.ProviderSet(
                ObserverOwnedScreenPayloads.PROTOCOL_VERSION, List.of(furnace))));

        assertTrue(ObserverOwnedProviderPolicy.validIdentity(
                new ObserverOwnedScreenPayloads.ProviderIdentity("totem:alchemy_cauldron", 7)));
        assertFalse(ObserverOwnedProviderPolicy.validIdentity(
                new ObserverOwnedScreenPayloads.ProviderIdentity("Invalid Family", 1)));
    }

    @Test
    void genericOwnedStateIsStructurallyBoundedWhileProviderOwnsVariantCompatibility() {
        var open = new ObserverScreenSnapshot("alchemy_cauldron", "research", 7, 42,
                Component.literal("Alchemy"), List.of(), new int[0], Map.of(), new byte[0]);
        assertTrue(ObserverOwnedScreenRelayManager.validState(
                new ObserverOwnedScreenPayloads.State(true, open)));

        var differentVersion = new ObserverScreenSnapshot("alchemy_cauldron", "research", 8, 43,
                Component.literal("Alchemy"), List.of(), new int[0], Map.of(), new byte[0]);
        assertTrue(ObserverOwnedScreenRelayManager.validState(
                new ObserverOwnedScreenPayloads.State(true, differentVersion)),
                "server structural validation must not hardcode an owning module's protocol");

        var closed = ObserverOwnedScreenPayloads.closed("alchemy_cauldron", "research", 7, 44);
        assertTrue(ObserverOwnedScreenRelayManager.validState(
                new ObserverOwnedScreenPayloads.State(false, closed)));

        var dirtyClose = new ObserverScreenSnapshot("alchemy_cauldron", "research", 7, 45,
                Component.empty(), List.of(), new int[]{1}, Map.of(), new byte[0]);
        assertFalse(ObserverOwnedScreenRelayManager.validState(
                new ObserverOwnedScreenPayloads.State(false, dirtyClose)));
    }

    @Test
    void legacyFamilyProtocolTableRemainsOnlyForV4CompatibilityPaths() {
        assertEquals(4, ObserverOwnedScreenProtocols.expected("nexus"));
        assertTrue(ObserverOwnedScreenProtocols.accepts("nexus", 4));
        assertFalse(ObserverOwnedScreenProtocols.accepts("nexus", 3));
        assertFalse(ObserverOwnedScreenProtocols.accepts("alchemy_cauldron", 1));
    }
}
