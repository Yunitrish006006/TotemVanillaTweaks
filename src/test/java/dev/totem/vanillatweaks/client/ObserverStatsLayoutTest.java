package dev.totem.vanillatweaks.client;

import org.junit.jupiter.api.Test;

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverStatsLayoutTest {
    private static final ToIntFunction<String> MONOSPACE_WIDTH =
            value -> value.codePointCount(0, value.length()) * 6;

    @Test
    void keepsTwelvePixelMarginsInTheNativeClientViewport() {
        ObserverStatsScreenClient.StatsLayout layout =
                ObserverStatsScreenClient.statsLayout(854 / 2, 480 / 2);

        assertEquals(12, layout.left());
        assertEquals(12, layout.top());
        assertEquals(403, layout.panelWidth());
        assertEquals(216, layout.panelHeight());
        assertEquals(12, layout.screenWidth() - layout.right());
        assertEquals(12, layout.screenHeight() - layout.bottom());
        assertEquals(158, layout.bodyHeight());
        assertTrue(layout.fits());
    }

    @Test
    void titleTabsAndMeasuredSortStatusOwnDisjointRegions() {
        ObserverStatsScreenClient.StatsLayout layout = ObserverStatsScreenClient.statsLayout(427, 240);

        assertTrue(layout.contentLeft() + layout.titleWidth()
                + ObserverStatsScreenClient.TITLE_SORT_GAP <= layout.sortX());
        assertTrue(layout.sortX() + layout.sortWidth() <= layout.contentRight());
        assertTrue(ObserverStatsScreenClient.TITLE_Y + ObserverStatsScreenClient.TEXT_HEIGHT
                < ObserverStatsScreenClient.TAB_Y);
        for (int tab = 0; tab < 2; tab++) {
            assertTrue(layout.tabRight(tab) + ObserverStatsScreenClient.TAB_GAP <= layout.tabX(tab + 1));
        }
        assertTrue(layout.tabRight(2) <= layout.contentRight());
        assertTrue(ObserverStatsScreenClient.TAB_Y + ObserverStatsScreenClient.TAB_HEIGHT
                <= layout.bodyY());

        ObserverStatsScreenClient.BoundedLabel sort = ObserverStatsScreenClient.boundedLabel(
                "Sort: extraordinarily_long_used_statistic_統計🧭 ↓", layout.sortWidth(), MONOSPACE_WIDTH);
        assertTrue(sort.text().endsWith("…"));
        assertTrue(sort.fits());
    }

    @Test
    void itemNameAndAllSixStatColumnsStayInsideTheBody() {
        ObserverStatsScreenClient.StatsLayout layout = ObserverStatsScreenClient.statsLayout(427, 240);

        assertEquals(41, layout.itemStatWidth());
        assertTrue(layout.itemLabelX() + layout.itemLabelWidth()
                + ObserverStatsScreenClient.ITEM_ICON_TEXT_GAP <= layout.itemFirstStatX());
        for (int column = 0; column < 6; column++) {
            assertTrue(layout.itemStatX(column) >= layout.bodyInnerLeft());
            assertTrue(layout.itemStatRight(column) <= layout.bodyInnerRight());
            if (column > 0) assertEquals(layout.itemStatRight(column - 1), layout.itemStatX(column));
            ObserverStatsScreenClient.BoundedLabel header = ObserverStatsScreenClient.boundedLabel(
                    new String[]{"Mine", "Break", "Craft", "Use", "Pick", "Drop"}[column],
                    layout.itemStatWidth() - 4, MONOSPACE_WIDTH);
            ObserverStatsScreenClient.BoundedLabel value = ObserverStatsScreenClient.boundedLabel(
                    "2147483647", layout.itemStatWidth() - 4, MONOSPACE_WIDTH);
            assertTrue(header.fits());
            assertTrue(value.fits());
        }
        assertEquals(layout.bodyInnerRight(), layout.itemStatRight(5));

        ObserverStatsScreenClient.BoundedLabel itemName = ObserverStatsScreenClient.boundedLabel(
                "example:exceptionally_long_item_identifier_測試🧭", layout.itemLabelWidth(), MONOSPACE_WIDTH);
        assertTrue(itemName.text().endsWith("…"));
        assertTrue(itemName.fits());
    }

    @Test
    void generalAndMobColumnsRemainBoundedAndAligned() {
        ObserverStatsScreenClient.StatsLayout layout = ObserverStatsScreenClient.statsLayout(427, 240);

        assertTrue(layout.generalLabelRight() + ObserverStatsScreenClient.COLUMN_GAP
                <= layout.generalValueX());
        assertEquals(layout.bodyInnerRight(), layout.generalValueRight());
        assertTrue(layout.mobNameRight() + ObserverStatsScreenClient.COLUMN_GAP <= layout.mobKilledX());
        assertTrue(layout.mobKilledRight() + ObserverStatsScreenClient.COLUMN_GAP <= layout.mobKilledByX());
        assertEquals(layout.bodyInnerRight(), layout.mobKilledByRight());

        String[] text = {
                "Distance flown through a very long custom dimension 統計🧭",
                "999 days, 23 hours, 59 minutes, 59 seconds",
                "Extremely Long Translated Mob Name 傳說生物🧭",
                "2147483647",
                "2147483647"
        };
        int[] budgets = {
                layout.generalLabelWidth(), layout.generalValueWidth(), layout.mobNameWidth(),
                layout.mobStatWidth() - 4, layout.mobStatWidth() - 4
        };
        for (int index = 0; index < text.length; index++) {
            ObserverStatsScreenClient.BoundedLabel label =
                    ObserverStatsScreenClient.boundedLabel(text[index], budgets[index], MONOSPACE_WIDTH);
            assertTrue(label.fits(), text[index]);
            assertFalse(hasUnpairedSurrogate(label.text()), text[index]);
        }
    }

    @Test
    void rowCapacityAndScrollKeepOnlyCompleteRowsAndClampTheLastPage() {
        ObserverStatsScreenClient.StatsLayout layout = ObserverStatsScreenClient.statsLayout(427, 240);

        assertEquals(9, layout.generalRowCapacity());
        assertEquals(6, layout.tableRowCapacity());
        ObserverStatsScreenClient.RowWindow fixture = ObserverStatsScreenClient.rowWindow(
                3, 20.0D, ObserverStatsScreenClient.TABLE_ROW_HEIGHT, layout.tableRowCapacity());
        assertEquals(0, fixture.firstRow());
        assertEquals(3, fixture.visibleRows());
        ObserverStatsScreenClient.RowWindow lastPage = ObserverStatsScreenClient.rowWindow(
                100, 99999.0D, ObserverStatsScreenClient.TABLE_ROW_HEIGHT, layout.tableRowCapacity());
        assertEquals(94, lastPage.firstRow());
        assertEquals(6, lastPage.visibleRows());
        assertEquals(100, lastPage.lastExclusive());

        int lastTableRowBottom = layout.bodyY() + ObserverStatsScreenClient.TABLE_HEADER_HEIGHT
                + layout.tableRowCapacity() * ObserverStatsScreenClient.TABLE_ROW_HEIGHT;
        int lastGeneralRowBottom = layout.bodyY() + 4
                + layout.generalRowCapacity() * ObserverStatsScreenClient.GENERAL_ROW_HEIGHT;
        assertTrue(lastTableRowBottom <= layout.bodyBottom() - ObserverStatsScreenClient.TABLE_BOTTOM_PADDING);
        assertTrue(lastGeneralRowBottom <= layout.bodyBottom());
    }

    @Test
    void unicodeEllipsisNeverSplitsEmojiOrTraditionalChinese() {
        String source = "統計🧭資料非常長 Statistics observer semantic family";
        ObserverStatsScreenClient.BoundedLabel label =
                ObserverStatsScreenClient.boundedLabel(source, 72, MONOSPACE_WIDTH);

        assertTrue(label.text().contains("🧭"));
        assertTrue(label.text().endsWith("…"));
        assertEquals(72, label.textWidth());
        assertTrue(label.fits());
        assertFalse(hasUnpairedSurrogate(label.text()));
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
