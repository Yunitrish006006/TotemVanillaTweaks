package dev.totem.vanillatweaks.client;

import org.junit.jupiter.api.Test;

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverCraftingHeaderLayoutTest {
    private static final ToIntFunction<String> MONOSPACE_WIDTH = value -> value.codePointCount(0, value.length()) * 6;

    @Test
    void preservesBothLabelsWhenTheyFit() {
        ObserverNativeCraftingScreenClient.CraftingHeaderLayout layout =
                ObserverNativeCraftingScreenClient.craftingHeaderLayout(
                        "Crafting", "Crafting 3x3", 176, MONOSPACE_WIDTH);

        assertEquals("Crafting", layout.title());
        assertEquals("Crafting 3x3", layout.mode());
        assertTrue(layout.fits());
        assertTrue(layout.modeX() - layout.titleWidth() >= 8);
    }

    @Test
    void ellipsizesWithoutOverlapAtVanillaContainerWidth() {
        ObserverNativeCraftingScreenClient.CraftingHeaderLayout layout =
                ObserverNativeCraftingScreenClient.craftingHeaderLayout(
                        "Observer Crafting Test", "Crafting 3x3 / table_3x3", 176, MONOSPACE_WIDTH);

        assertTrue(layout.title().endsWith("…"));
        assertTrue(layout.mode().endsWith("…"));
        assertTrue(layout.fits());
        assertTrue(layout.modeX() - layout.titleWidth() >= 8);
    }

    @Test
    void remainsDisjointAtMinimumSupportedContentWidth() {
        ObserverNativeCraftingScreenClient.CraftingHeaderLayout layout =
                ObserverNativeCraftingScreenClient.craftingHeaderLayout(
                        "A very long crafting title", "Crafting 3x3 / table_3x3", 64, MONOSPACE_WIDTH);

        assertFalse(layout.title().isEmpty());
        assertFalse(layout.mode().isEmpty());
        assertTrue(layout.fits());
        assertTrue(layout.modeX() - layout.titleWidth() >= 8);
        assertTrue(layout.modeX() + layout.modeWidth() <= 64);
    }

    @Test
    void truncationKeepsTraditionalChineseAndSupplementaryCodePointsWellFormed() {
        ObserverNativeCraftingScreenClient.CraftingHeaderLayout layout =
                ObserverNativeCraftingScreenClient.craftingHeaderLayout(
                        "觀🔥察🔥者合成測試", "合成 3x3 / 工作台格", 64, MONOSPACE_WIDTH);

        assertTrue(layout.title().endsWith("…"));
        assertTrue(layout.title().contains("🔥"));
        assertFalse(Character.isHighSurrogate(layout.title().charAt(layout.title().length() - 2)));
        assertTrue(layout.fits());
    }
}
