package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverNativePayloadsTest {
    @Test
    void protocolV3UsesVersionedPacketIdentifiers() {
        assertEquals(3, ObserverNativePayloads.PROTOCOL_VERSION);
        assertVersioned(ObserverNativePayloads.NativeControl.TYPE.id().getPath());
        assertVersioned(ObserverNativePayloads.NativeSession.TYPE.id().getPath());
        assertVersioned(ObserverNativePayloads.NativeViewState.TYPE.id().getPath());
        assertVersioned(ObserverNativePayloads.NativeViewRelay.TYPE.id().getPath());
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

    private static void assertVersioned(String path) {
        assertTrue(path.endsWith("_v3"), () -> "Native Observer v3 packet id is not versioned: " + path);
    }
}
