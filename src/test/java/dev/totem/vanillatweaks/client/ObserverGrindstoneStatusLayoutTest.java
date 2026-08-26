package dev.totem.vanillatweaks.client;

import org.junit.jupiter.api.Test;

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverGrindstoneStatusLayoutTest {
    private static final ToIntFunction<String> MONOSPACE_WIDTH =
            value -> value.codePointCount(0, value.length()) * 6;

    @Test
    void preservesFullResultMeaningAtVanillaContainerWidth() {
        ObserverGrindstoneScreenClient.GrindstoneStatusLayout layout =
                ObserverGrindstoneScreenClient.grindstoneStatusLayout(
                        true, true, true, false, 160, MONOSPACE_WIDTH);

        assertEquals("Result ready", layout.status());
        assertTrue(layout.fits());
        assertEquals((160 - layout.textWidth()) / 2, layout.statusX());
    }

    @Test
    void keepsEveryStateMeaningVisibleAtMinimumSupportedWidth() {
        assertCompactStatus("Invalid", true, true, false, true);
        assertCompactStatus("Ready", true, true, true, false);
        assertCompactStatus("Working", true, false, false, false);
        assertCompactStatus("Insert", false, false, false, false);
    }

    @Test
    void statusRowStaysBetweenMachineSlotsAndInventorySlots() {
        assertTrue(ObserverGrindstoneScreenClient.STATUS_Y
                > ObserverGrindstoneScreenClient.MACHINE_SLOT_BORDER_BOTTOM);
        assertTrue(ObserverGrindstoneScreenClient.STATUS_Y
                + ObserverGrindstoneScreenClient.TEXT_HEIGHT
                <= ObserverGrindstoneScreenClient.INVENTORY_SLOT_BORDER_TOP);
    }

    @Test
    void unicodeSafeEllipsisNeverSplitsSupplementaryCodePoints() {
        String fitted = ObserverGrindstoneScreenClient.fitStatusText(
                "觀🔥察🔥者處理中", 30, MONOSPACE_WIDTH);

        assertTrue(fitted.endsWith("…"));
        assertTrue(fitted.contains("🔥"));
        assertFalse(Character.isHighSurrogate(fitted.charAt(fitted.length() - 2)));
        assertTrue(MONOSPACE_WIDTH.applyAsInt(fitted) <= 30);
    }

    private static void assertCompactStatus(
            String expected,
            boolean primary,
            boolean secondary,
            boolean result,
            boolean invalid
    ) {
        ObserverGrindstoneScreenClient.GrindstoneStatusLayout layout =
                ObserverGrindstoneScreenClient.grindstoneStatusLayout(
                        primary, secondary, result, invalid, 48, MONOSPACE_WIDTH);

        assertEquals(expected, layout.status());
        assertFalse(layout.status().isBlank());
        assertTrue(layout.fits());
        assertTrue(layout.statusX() + layout.textWidth() <= 48);
    }
}
