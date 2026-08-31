package dev.totem.vanillatweaks.observer;

import dev.totem.core.api.v1.client.observer.ObserverScreenSnapshot;
import dev.totem.vanillatweaks.network.ObserverOwnedScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverOwnedScreenProtocols;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ObserverOwnedScreenProtocolTest {
    @Test void screenProtocolNegotiatesIndependentlyFromTransportProtocol() {
        assertEquals(1, ObserverOwnedScreenPayloads.PROTOCOL_VERSION);
        assertEquals(3, ObserverOwnedScreenProtocols.expected("nexus"));
        var providers = new ObserverOwnedScreenPayloads.ProviderSet(
                ObserverOwnedScreenPayloads.PROTOCOL_VERSION,
                List.of(new ObserverOwnedScreenPayloads.ProviderIdentity("nexus", 3)));
        assertNotNull(ObserverNativeSessionManager.validateOwnedProviderSet(providers));
        assertTrue(ObserverOwnedScreenProtocols.accepts("nexus", 3));
        assertFalse(ObserverOwnedScreenProtocols.accepts("nexus", 2));
        assertFalse(ObserverOwnedScreenProtocols.accepts("nexus", 1));
        assertFalse(ObserverOwnedScreenProtocols.accepts("unknown", 1));
        assertNull(ObserverNativeSessionManager.validateOwnedProviderSet(new ObserverOwnedScreenPayloads.ProviderSet(
                2, List.of(new ObserverOwnedScreenPayloads.ProviderIdentity("nexus", 3)))));
    }

    @Test void closeRetainsCurrentScreenProtocolAndUnknownVersionsAreRejected() {
        ObserverScreenSnapshot closed = ObserverOwnedScreenPayloads.closed("nexus", "map", 3, 42);
        assertEquals(3, closed.protocolVersion());
        assertTrue(ObserverOwnedScreenRelayManager.validState(new ObserverOwnedScreenPayloads.State(false, closed)));

        var unknown = new ObserverScreenSnapshot("nexus", "map", 4, 43, Component.empty(),
                List.of(), new int[0], Map.of(), new byte[0]);
        assertFalse(ObserverOwnedScreenRelayManager.validState(new ObserverOwnedScreenPayloads.State(true, unknown)));
        assertNull(ObserverNativeSessionManager.validateOwnedProviderSet(new ObserverOwnedScreenPayloads.ProviderSet(
                ObserverOwnedScreenPayloads.PROTOCOL_VERSION,
                List.of(new ObserverOwnedScreenPayloads.ProviderIdentity("nexus", 4)))));
    }
}
