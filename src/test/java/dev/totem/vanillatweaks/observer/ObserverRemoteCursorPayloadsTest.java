package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverRemoteCursorPayloads;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ObserverRemoteCursorPayloadsTest {
    @Test void ownsNextCapabilityBitAndBoundsGeometry() {
        assertEquals(1L << 24, ObserverRemoteCursorPayloads.CAPABILITY);
        assertTrue(ObserverRemoteCursorPayloads.valid(1, 175, 165, 176, 166));
        assertFalse(ObserverRemoteCursorPayloads.valid(-1, 0, 0, 176, 166));
        assertFalse(ObserverRemoteCursorPayloads.valid(1, 176, 0, 176, 166));
        assertFalse(ObserverRemoteCursorPayloads.valid(1, Float.NaN, 0, 176, 166));
        assertFalse(ObserverRemoteCursorPayloads.valid(1, 0, 0, 5000, 166));
    }

    @Test void relayRateBoundaryMatchesAdvertisedTwentyHertz() {
        assertEquals(20, ObserverRemoteCursorPayloads.MAX_UPDATES_PER_SECOND);
        assertEquals(50_000_000L, ObserverRemoteCursorRelayManager.MIN_INTERVAL_NANOS);
        assertEquals(1_000_000_000L,
                ObserverRemoteCursorRelayManager.MIN_INTERVAL_NANOS
                        * ObserverRemoteCursorPayloads.MAX_UPDATES_PER_SECOND);
    }

    @Test void cursorSequencesAreIndependentPerTargetAndFamily() {
        UUID target = UUID.randomUUID();
        ObserverRemoteCursorRelayManager.clearTarget(target);
        assertTrue(ObserverRemoteCursorRelayManager.acceptSequence(target, "family_a", 10_000));
        assertFalse(ObserverRemoteCursorRelayManager.acceptSequence(target, "family_a", 9_999));
        assertTrue(ObserverRemoteCursorRelayManager.acceptSequence(target, "family_b", 1));
        ObserverRemoteCursorRelayManager.clearTarget(target);
    }
}
