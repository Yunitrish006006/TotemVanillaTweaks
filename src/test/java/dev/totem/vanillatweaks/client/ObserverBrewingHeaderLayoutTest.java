package dev.totem.vanillatweaks.client;

import org.junit.jupiter.api.Test;

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverBrewingHeaderLayoutTest {
    private static final ToIntFunction<String> MONOSPACE_WIDTH =
            value -> value.codePointCount(0, value.length()) * 6;

    @Test
    void preservesTitleAndFullFuelStateAtVanillaContainerWidth() {
        ObserverBrewingScreenClient.BrewingHeaderLayout layout =
                ObserverBrewingScreenClient.brewingHeaderLayout("Brewing Stand", 12, 160, MONOSPACE_WIDTH);

        assertEquals("Brewing Stand", layout.title());
        assertEquals("Fuel 12/20", layout.fuel());
        assertTrue(layout.fits());
        assertTrue(layout.fuelX() - layout.titleWidth() >= 8);
    }

    @Test
    void ellipsizesLongTitleWithoutSacrificingFuelValue() {
        ObserverBrewingScreenClient.BrewingHeaderLayout layout =
                ObserverBrewingScreenClient.brewingHeaderLayout(
                        "Observer Brewing Stand Test", 12, 160, MONOSPACE_WIDTH);

        assertTrue(layout.title().endsWith("…"));
        assertEquals("Fuel 12/20", layout.fuel());
        assertTrue(layout.fits());
        assertTrue(layout.fuelX() - layout.titleWidth() >= 8);
    }

    @Test
    void keepsNumericFuelStateAtMinimumSupportedContentWidth() {
        ObserverBrewingScreenClient.BrewingHeaderLayout layout =
                ObserverBrewingScreenClient.brewingHeaderLayout(
                        "A very long brewing title", 12, 64, MONOSPACE_WIDTH);

        assertFalse(layout.title().isEmpty());
        assertEquals("12/20", layout.fuel());
        assertTrue(layout.fits());
        assertTrue(layout.fuelX() - layout.titleWidth() >= 8);
        assertTrue(layout.fuelX() + layout.fuelWidth() <= 64);
    }

    @Test
    void truncationKeepsTraditionalChineseAndSupplementaryCodePointsWellFormed() {
        ObserverBrewingScreenClient.BrewingHeaderLayout layout =
                ObserverBrewingScreenClient.brewingHeaderLayout(
                        "觀🔥察🔥者釀造台測試", 20, 64, MONOSPACE_WIDTH);

        assertTrue(layout.title().endsWith("…"));
        assertTrue(layout.title().contains("🔥"));
        assertFalse(Character.isHighSurrogate(layout.title().charAt(layout.title().length() - 2)));
        assertTrue(layout.fits());
    }

    @Test
    void headerTextEndsBeforeCanonicalBrewingSlotsBegin() {
        assertTrue(ObserverBrewingScreenClient.HEADER_TEXT_Y
                + ObserverBrewingScreenClient.HEADER_TEXT_HEIGHT
                <= ObserverBrewingScreenClient.BREWING_SLOT_BORDER_TOP);
    }
}
