package dev.totem.vanillatweaks.client;

import org.junit.jupiter.api.Test;

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverLoomLayoutTest {
    private static final ToIntFunction<String> MONOSPACE_WIDTH =
            value -> value.codePointCount(0, value.length()) * 6;

    @Test
    void usesVanillaFourByFourPatternViewportWithoutEnteringInventory() {
        assertEquals(4, ObserverLoomScreenClient.PATTERN_COLUMNS);
        assertEquals(4, ObserverLoomScreenClient.PATTERN_ROWS);
        assertEquals(14, ObserverLoomScreenClient.PATTERN_CELL_SIZE);
        assertEquals(69, ObserverLoomScreenClient.PATTERN_GRID_BOTTOM);
        assertTrue(ObserverLoomScreenClient.PATTERN_GRID_BOTTOM
                < ObserverLoomScreenClient.STATUS_Y);
        assertTrue(ObserverLoomScreenClient.STATUS_Y + ObserverLoomScreenClient.TEXT_HEIGHT
                <= ObserverLoomScreenClient.INVENTORY_SLOT_BORDER_TOP);
        assertTrue(ObserverLoomScreenClient.STATUS_X + ObserverLoomScreenClient.STATUS_MAX_WIDTH
                + ObserverLoomScreenClient.STATUS_RIGHT_GUTTER
                <= ObserverLoomScreenClient.RESULT_SLOT_BORDER_LEFT);
    }

    @Test
    void keepsAllSixteenPatternsInTheScrolledVanillaViewport() {
        ObserverLoomScreenClient.LoomPatternViewport viewport =
                ObserverLoomScreenClient.loomPatternViewport(1, 20);

        assertEquals(4, viewport.first());
        assertEquals(20, viewport.lastExclusive());
        assertEquals(16, viewport.visibleCount());
    }

    @Test
    void statusPreservesSelectionTotalAndReadyStateAtNormalWidth() {
        ObserverLoomScreenClient.LoomStatusLayout status =
                ObserverLoomScreenClient.loomStatusLayout(
                        5, 20, false, true,
                        ObserverLoomScreenClient.STATUS_MAX_WIDTH, MONOSPACE_WIDTH);

        assertEquals("6/20 Ready", status.text());
        assertTrue(status.fits());
    }

    @Test
    void statusPreservesLimitMeaningAtLargestSupportedCatalogue() {
        ObserverLoomScreenClient.LoomStatusLayout status =
                ObserverLoomScreenClient.loomStatusLayout(
                        511, 512, true, false,
                        ObserverLoomScreenClient.STATUS_MAX_WIDTH, MONOSPACE_WIDTH);

        assertEquals("512/512 Limit", status.text());
        assertTrue(status.fits());
    }

    @Test
    void reasonableMinimumWidthKeepsTotalAndNoSelectionMeaning() {
        ObserverLoomScreenClient.LoomStatusLayout status =
                ObserverLoomScreenClient.loomStatusLayout(
                        -1, 512, false, false, 66, MONOSPACE_WIDTH);

        assertEquals("-/512 Pick", status.text());
        assertTrue(status.fits());
    }
}
