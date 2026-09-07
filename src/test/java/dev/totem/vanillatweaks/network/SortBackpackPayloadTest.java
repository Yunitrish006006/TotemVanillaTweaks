package dev.totem.vanillatweaks.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SortBackpackPayloadTest {
    @Test
    void canonicalPayloadUsesTotemIdentifier() {
        assertEquals("totem:sort_backpack", SortBackpackPayload.TYPE.id().toString());
    }
}
