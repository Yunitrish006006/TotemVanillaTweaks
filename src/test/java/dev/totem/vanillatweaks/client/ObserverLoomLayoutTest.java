package dev.totem.vanillatweaks.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverLoomLayoutTest {
    @Test
    void usesVanillaFourByFourPatternViewportWithoutEnteringInventory() {
        assertEquals(4, ObserverLoomScreenClient.PATTERN_COLUMNS);
        assertEquals(4, ObserverLoomScreenClient.PATTERN_ROWS);
        assertEquals(14, ObserverLoomScreenClient.PATTERN_CELL_SIZE);
        assertEquals(69, ObserverLoomScreenClient.PATTERN_GRID_BOTTOM);
        assertTrue(ObserverLoomScreenClient.PATTERN_GRID_BOTTOM
                < ObserverLoomScreenClient.INVENTORY_SLOT_BORDER_TOP);
    }

    @Test
    void keepsAllSixteenPatternsInTheScrolledVanillaViewport() {
        ObserverLoomScreenClient.LoomPatternViewport viewport =
                ObserverLoomScreenClient.loomPatternViewport(1, 20);

        assertEquals(4, viewport.first());
        assertEquals(20, viewport.lastExclusive());
        assertEquals(16, viewport.visibleCount());
    }

}
