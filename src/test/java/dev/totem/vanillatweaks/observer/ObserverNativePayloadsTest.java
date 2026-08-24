package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverNativePayloadsTest {
    @Test
    void protocolV2UsesVersionedPacketIdentifiers() {
        assertEquals(2, ObserverNativePayloads.PROTOCOL_VERSION);
        assertVersioned(ObserverNativePayloads.NativeControl.TYPE.id().getPath());
        assertVersioned(ObserverNativePayloads.NativeSession.TYPE.id().getPath());
        assertVersioned(ObserverNativePayloads.NativeViewState.TYPE.id().getPath());
        assertVersioned(ObserverNativePayloads.NativeViewRelay.TYPE.id().getPath());
    }

    private static void assertVersioned(String path) {
        assertTrue(path.endsWith("_v2"), () -> "Native Observer v2 packet id is not versioned: " + path);
    }
}
