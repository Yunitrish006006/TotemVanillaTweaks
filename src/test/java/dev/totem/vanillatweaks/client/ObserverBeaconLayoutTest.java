package dev.totem.vanillatweaks.client;

import org.junit.jupiter.api.Test;

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverBeaconLayoutTest {
    private static final ToIntFunction<String> MONOSPACE_WIDTH =
            value -> value.codePointCount(0, value.length()) * 6;

    @Test
    void restoresVanillaPanelGeometryInsideTheNativeClientViewport() {
        assertEquals(230, ObserverBeaconScreenClient.PANEL_WIDTH);
        assertEquals(219, ObserverBeaconScreenClient.PANEL_HEIGHT);

        int logicalViewportWidth = 854 / 2;
        int logicalViewportHeight = 480 / 2;
        int horizontalMargin = (logicalViewportWidth - ObserverBeaconScreenClient.PANEL_WIDTH) / 2;
        int verticalMargin = (logicalViewportHeight - ObserverBeaconScreenClient.PANEL_HEIGHT) / 2;

        assertTrue(horizontalMargin >= ObserverBeaconScreenClient.SAFE_SCREEN_MARGIN);
        assertTrue(verticalMargin >= ObserverBeaconScreenClient.SAFE_SCREEN_MARGIN);
        assertTrue(ObserverBeaconScreenClient.HOTBAR_SLOT_BORDER_BOTTOM
                < ObserverBeaconScreenClient.PANEL_HEIGHT);
    }

    @Test
    void semanticRowsDoNotSharePixelsOrEnterTheInventory() {
        int[] rows = {
                ObserverBeaconScreenClient.TITLE_Y,
                ObserverBeaconScreenClient.TIER_Y,
                ObserverBeaconScreenClient.PRIMARY_Y,
                ObserverBeaconScreenClient.SECONDARY_Y,
                ObserverBeaconScreenClient.PAYMENT_Y,
                ObserverBeaconScreenClient.CONFIRM_Y,
                ObserverBeaconScreenClient.EFFECT_1_Y,
                ObserverBeaconScreenClient.EFFECT_2_Y,
                ObserverBeaconScreenClient.EFFECT_3_Y,
                ObserverBeaconScreenClient.EFFECT_4_Y
        };
        for (int row = 0; row < rows.length - 1; row++) {
            assertTrue(rows[row] + ObserverBeaconScreenClient.TEXT_HEIGHT <= rows[row + 1]);
        }
        assertTrue(ObserverBeaconScreenClient.EFFECT_4_Y + ObserverBeaconScreenClient.TEXT_HEIGHT
                <= ObserverBeaconScreenClient.INVENTORY_SLOT_BORDER_TOP);
    }

    @Test
    void effectLegendKeepsAVisibleGutterBeforeThePaymentSlot() {
        assertTrue(ObserverBeaconScreenClient.CONTENT_X
                        + ObserverBeaconScreenClient.EFFECT_TEXT_MAX_WIDTH
                        + ObserverBeaconScreenClient.EFFECT_SLOT_GUTTER
                <= ObserverBeaconScreenClient.PAYMENT_SLOT_BORDER_LEFT);
        assertTrue(ObserverBeaconScreenClient.EFFECT_2_Y + ObserverBeaconScreenClient.TEXT_HEIGHT
                > ObserverBeaconScreenClient.PAYMENT_SLOT_BORDER_TOP);
        assertTrue(ObserverBeaconScreenClient.EFFECT_4_Y
                < ObserverBeaconScreenClient.PAYMENT_SLOT_BORDER_BOTTOM);
    }

    @Test
    void normalStatePreservesEverySemanticValueOnIndependentRows() {
        ObserverBeaconScreenClient.BeaconTextLayout layout =
                ObserverBeaconScreenClient.beaconTextLayout(
                        "Beacon", 4, "minecraft:speed", "minecraft:regeneration",
                        true, true, MONOSPACE_WIDTH);

        assertEquals("Beacon", layout.title().text());
        assertEquals("Tier: 4/4", layout.tier().text());
        assertEquals("Primary: speed", layout.primary().text());
        assertEquals("Secondary: regeneration", layout.secondary().text());
        assertEquals("Payment ready", layout.payment().text());
        assertEquals("Confirm available", layout.confirm().text());
        assertEquals("L4 Regen / Upgrade", layout.effect4().text());
        assertTrue(layout.fits());
    }

    @Test
    void longEnglishAndUnicodeLabelsAreEllipsizedWithinTheirOwnBudget() {
        ObserverBeaconScreenClient.BeaconTextLayout layout =
                ObserverBeaconScreenClient.beaconTextLayout(
                        "Beacon status for the very distant mountain outpost 測試🧭",
                        4,
                        "example:extraordinarily_long_primary_effect_name_測試🧭",
                        "example:extraordinarily_long_secondary_effect_name_測試🧭",
                        false, false, MONOSPACE_WIDTH);

        assertTrue(layout.title().text().endsWith("…"));
        assertTrue(layout.primary().text().endsWith("…"));
        assertTrue(layout.secondary().text().endsWith("…"));
        assertEquals("Payment required", layout.payment().text());
        assertEquals("Confirm unavailable", layout.confirm().text());
        assertTrue(layout.fits());
    }
}
