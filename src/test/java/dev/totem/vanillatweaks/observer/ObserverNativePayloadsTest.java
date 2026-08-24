package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverNativePayloadsTest {
    @Test
    void protocolV4UsesVersionedPacketIdentifiers() {
        assertEquals(4, ObserverNativePayloads.PROTOCOL_VERSION);
        assertNativeVersioned(ObserverNativePayloads.NativeControl.TYPE.id().getPath());
        assertNativeVersioned(ObserverNativePayloads.NativeSession.TYPE.id().getPath());
        assertNativeVersioned(ObserverNativePayloads.NativeViewState.TYPE.id().getPath());
        assertNativeVersioned(ObserverNativePayloads.NativeViewRelay.TYPE.id().getPath());
    }

    @Test
    void semanticScreenProtocolV2ExposesStableContainerCapability() {
        assertEquals(2, ObserverNativeScreenPayloads.SCREEN_PROTOCOL_VERSION);
        assertTrue(ObserverNativeScreenPayloads.ContainerState.TYPE.id().getPath().endsWith("_v2"));
        assertTrue(ObserverNativeScreenPayloads.ContainerRelay.TYPE.id().getPath().endsWith("_v2"));

        long container = ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS;
        assertEquals(container, ObserverNativeScreenPayloads.KNOWN_CAPABILITIES);
        assertEquals(container, ObserverNativeScreenPayloads.capabilityForFamily(
                ObserverNativeScreenPayloads.FAMILY_CONTAINER_SLOTS
        ));
        assertEquals(0L, ObserverNativeScreenPayloads.capabilityForFamily("unknown"));
        assertEquals(container, ObserverNativeScreenPayloads.sanitizeCapabilities(container | (1L << 40)));
        assertTrue(ObserverNativeScreenPayloads.supports(container, container));
        assertFalse(ObserverNativeScreenPayloads.supports(0L, container));
    }

    @Test
    void compatibilityPayloadSurfaceContainsNoFramebufferMessages() {
        Set<String> nestedTypes = Arrays.stream(ObserverPayloads.class.getDeclaredClasses())
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("ScreenState", "ScreenRelay", "Stop"), nestedTypes);
        assertThrows(ClassNotFoundException.class, () ->
                Class.forName("dev.totem.vanillatweaks.observer.ObserverFrameRules"));
    }

    private static void assertNativeVersioned(String path) {
        assertTrue(path.endsWith("_v4"), () -> "Native Observer v4 packet id is not versioned: " + path);
    }
}
