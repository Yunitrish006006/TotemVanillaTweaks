package dev.totem.vanillatweaks.client;

import org.junit.jupiter.api.Test;

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverFurnaceHeaderLayoutTest {
    private static final ToIntFunction<String> MONOSPACE_WIDTH = value -> value.codePointCount(0, value.length()) * 6;

    @Test
    void preservesBothLabelsWhenTheyFit() {
        ObserverNativeScreenClient.FurnaceHeaderLayout layout = ObserverNativeScreenClient.furnaceHeaderLayout(
                "Furnace", 240, MONOSPACE_WIDTH);

        assertEquals("Furnace", layout.title());
        assertEquals("Protocol-native furnace", layout.mode());
        assertTrue(layout.fits());
        assertTrue(layout.modeX() - layout.titleWidth() >= 8);
    }

    @Test
    void ellipsizesWithoutOverlapAtVanillaContainerWidth() {
        ObserverNativeScreenClient.FurnaceHeaderLayout layout = ObserverNativeScreenClient.furnaceHeaderLayout(
                "Observer Furnace Test", 176, MONOSPACE_WIDTH);

        assertTrue(layout.title().endsWith("…"));
        assertTrue(layout.mode().endsWith("…"));
        assertTrue(layout.fits());
        assertTrue(layout.modeX() - layout.titleWidth() >= 8);
    }

    @Test
    void remainsDisjointAtMinimumSupportedContentWidth() {
        ObserverNativeScreenClient.FurnaceHeaderLayout layout = ObserverNativeScreenClient.furnaceHeaderLayout(
                "A very long remote furnace title", 64, MONOSPACE_WIDTH);

        assertFalse(layout.title().isEmpty());
        assertFalse(layout.mode().isEmpty());
        assertTrue(layout.fits());
        assertTrue(layout.modeX() - layout.titleWidth() >= 8);
        assertTrue(layout.modeX() + layout.modeWidth() <= 64);
    }

    @Test
    void truncationKeepsTraditionalChineseAndSupplementaryCodePointsWellFormed() {
        ObserverNativeScreenClient.FurnaceHeaderLayout layout = ObserverNativeScreenClient.furnaceHeaderLayout(
                "觀🔥察🔥者熔爐測試", 64, MONOSPACE_WIDTH);

        assertTrue(layout.title().endsWith("…"));
        assertTrue(layout.title().contains("🔥"));
        assertFalse(Character.isHighSurrogate(layout.title().charAt(layout.title().length() - 2)));
        assertTrue(layout.fits());
    }
}
