package dev.totem.vanillatweaks.observer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObserverFrameRulesTest {
    @Test
    void frameBytesAreSplitBelowServerboundPayloadLimit() {
        assertEquals(1, ObserverFrameRules.chunkCount(1));
        assertEquals(1, ObserverFrameRules.chunkCount(ObserverFrameRules.CHUNK_BYTES));
        assertEquals(2, ObserverFrameRules.chunkCount(ObserverFrameRules.CHUNK_BYTES + 1));
        assertEquals(0, ObserverFrameRules.chunkCount(0));
        assertEquals(0, ObserverFrameRules.chunkCount(ObserverFrameRules.MAX_FRAME_BYTES + 1));
    }

    @Test
    void relayRejectsOversizedOrMalformedChunks() {
        assertTrue(ObserverFrameRules.validChunk(
                0, 1, 640, 360, 1920, 1080, ObserverFrameRules.CHUNK_BYTES));
        assertFalse(ObserverFrameRules.validChunk(
                0, 1, 641, 360, 1920, 1080, 100));
        assertFalse(ObserverFrameRules.validChunk(
                1, 1, 640, 360, 1920, 1080, 100));
        assertFalse(ObserverFrameRules.validChunk(
                0, 1, 640, 360, 1920, 1080, ObserverFrameRules.CHUNK_BYTES + 1));
    }
}
