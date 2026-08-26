package dev.totem.vanillatweaks.client;

import org.junit.jupiter.api.Test;

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverSmithingLabelLayoutTest {
    private static final ToIntFunction<String> VANILLA_LIKE_WIDTH = value -> {
        int width = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            width += codePoint == ' ' ? 4 : 5;
            offset += Character.charCount(codePoint);
        }
        return width;
    };
    private static final ToIntFunction<String> MONOSPACE_WIDTH =
            value -> value.codePointCount(0, value.length()) * 6;

    @Test
    void preservesFullSemanticsAtVanillaContainerWidth() {
        ObserverSmithingScreenClient.SmithingLabelLayout layout =
                ObserverSmithingScreenClient.smithingLabelLayout(
                        false, true, 70, 160, VANILLA_LIKE_WIDTH);

        assertEquals("Result ready", layout.status());
        assertEquals("T Template  B Base  A Addition", layout.legend());
        assertTrue(layout.fits());
    }

    @Test
    void preservesEachSemanticMarkerAtMinimumSupportedWidth() {
        ObserverSmithingScreenClient.SmithingLabelLayout layout =
                ObserverSmithingScreenClient.smithingLabelLayout(
                        false, true, 64, 64, MONOSPACE_WIDTH);

        assertEquals("Ready", layout.status());
        assertEquals("T  B  A", layout.legend());
        assertTrue(layout.fits());
    }

    @Test
    void neverDropsInvalidRecipeMeaningWhenTheFullPhraseDoesNotFit() {
        ObserverSmithingScreenClient.SmithingLabelLayout layout =
                ObserverSmithingScreenClient.smithingLabelLayout(
                        true, false, 64, 64, MONOSPACE_WIDTH);

        assertEquals("Invalid", layout.status());
        assertFalse(layout.status().isBlank());
        assertTrue(layout.fits());
    }

    @Test
    void resultWaitingStateRemainsVisibleInCompactLayout() {
        ObserverSmithingScreenClient.SmithingLabelLayout layout =
                ObserverSmithingScreenClient.smithingLabelLayout(
                        false, false, 64, 64, MONOSPACE_WIDTH);

        assertEquals("Waiting", layout.status());
        assertTrue(layout.fits());
    }

    @Test
    void markerRowsAndLegendStayOutsideCanonicalSlotBorders() {
        assertTrue(ObserverSmithingScreenClient.INPUT_MARKER_Y
                + ObserverSmithingScreenClient.TEXT_HEIGHT
                <= ObserverSmithingScreenClient.MACHINE_SLOT_BORDER_TOP);
        assertTrue(ObserverSmithingScreenClient.INPUT_LEGEND_Y
                >= ObserverSmithingScreenClient.MACHINE_SLOT_BORDER_BOTTOM);
        assertTrue(ObserverSmithingScreenClient.INPUT_LEGEND_Y
                + ObserverSmithingScreenClient.TEXT_HEIGHT
                <= ObserverSmithingScreenClient.INVENTORY_SLOT_BORDER_TOP);
    }

    @Test
    void eachMarkerIsCenteredOnItsCanonicalInputSlot() {
        for (int input = 0; input < ObserverSmithingScreenClient.INPUT_SLOT_CENTERS.length; input++) {
            int markerWidth = 6;
            int markerX = ObserverSmithingScreenClient.centeredInputMarkerX(input, markerWidth);
            assertEquals(ObserverSmithingScreenClient.INPUT_SLOT_CENTERS[input], markerX + markerWidth / 2);
        }
    }
}
