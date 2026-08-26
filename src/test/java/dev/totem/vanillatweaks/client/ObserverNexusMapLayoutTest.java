package dev.totem.vanillatweaks.client;

import org.junit.jupiter.api.Test;

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverNexusMapLayoutTest {
    private static final ToIntFunction<String> MONOSPACE_WIDTH =
            value -> value.codePointCount(0, value.length()) * 6;

    @Test
    void keepsTwelvePixelMarginsInTheNativeClientViewport() {
        ObserverNexusScreenClient.MapLayout layout =
                ObserverNexusScreenClient.mapLayout(854 / 2, 480 / 2, 10, 0);

        assertEquals(12, layout.left());
        assertEquals(12, layout.top());
        assertEquals(403, layout.panelWidth());
        assertEquals(216, layout.panelHeight());
        assertEquals(12, layout.screenWidth() - layout.right());
        assertEquals(12, layout.screenHeight() - layout.bottom());
        assertTrue(layout.fits());
    }

    @Test
    void derivesVisibleRowsFromTheAvailableHeight() {
        ObserverNexusScreenClient.MapLayout nativeLayout =
                ObserverNexusScreenClient.mapLayout(427, 240, 20, 0);
        ObserverNexusScreenClient.MapLayout tallLayout =
                ObserverNexusScreenClient.mapLayout(854, 480, 20, 0);

        assertEquals(4, nativeLayout.rowCapacity());
        assertEquals(4, nativeLayout.visibleRows());
        assertEquals(8, tallLayout.rowCapacity());
        assertEquals(8, tallLayout.visibleRows());
        assertTrue(nativeLayout.lastRowBottom() <= nativeLayout.listBottom());
        assertTrue(tallLayout.lastRowBottom() <= tallLayout.listBottom());
    }

    @Test
    void clampsScrollToACompleteLastPageInsteadOfCroppingRows() {
        ObserverNexusScreenClient.MapLayout layout =
                ObserverNexusScreenClient.mapLayout(427, 240, 10, 99);

        assertEquals(6, layout.firstRow());
        assertEquals(4, layout.visibleRows());
        assertEquals(10, layout.firstRow() + layout.visibleRows());
        assertTrue(layout.fits());
    }

    @Test
    void longEnglishTraditionalChineseAndEmojiStayInsideThePixelBudget() {
        String source = "非常遙遠的山脈中繼站 🧭 · minecraft:the_end_with_a_very_long_dimension_name";
        ObserverNexusScreenClient.MapLabel label =
                ObserverNexusScreenClient.mapLabel(source, 126, MONOSPACE_WIDTH);

        assertTrue(label.text().endsWith("…"));
        assertTrue(label.text().contains("🧭"));
        assertTrue(label.fits());
        assertEquals(126, label.textWidth());
        assertFalse(hasUnpairedSurrogate(label.text()));
    }

    @Test
    void everyMapTextRegionUsesTheSameMeasuredContentBoundary() {
        ObserverNexusScreenClient.MapLayout layout =
                ObserverNexusScreenClient.mapLayout(427, 240, 2, 0);
        String[] semantics = {
                "Nexus Map 測試🧭",
                "Home Nexus · minecraft:overworld",
                "Search: mountain Type: all Friend: friends Sort: distance",
                "Zoom 1.25 scroll 0 [materials]",
                "Mountain Relay · remote · minecraft:overworld · ready",
                "Tier 2 resonance 73% distance 1340 food 5 amethyst 2"
        };

        for (String semantic : semantics) {
            assertTrue(ObserverNexusScreenClient.mapLabel(
                    semantic, layout.contentWidth(), MONOSPACE_WIDTH).fits());
        }
    }

    private static boolean hasUnpairedSurrogate(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) return true;
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
        }
        return false;
    }
}
