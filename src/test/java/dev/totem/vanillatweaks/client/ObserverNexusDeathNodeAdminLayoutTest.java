package dev.totem.vanillatweaks.client;

import org.junit.jupiter.api.Test;

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverNexusDeathNodeAdminLayoutTest {
    private static final ToIntFunction<String> MONOSPACE_WIDTH =
            value -> value.codePointCount(0, value.length()) * 6;

    @Test
    void keepsTwelvePixelMarginsInTheNativeClientViewport() {
        ObserverNexusDeathNodeAdminScreenClient.DeathNodeAdminLayout layout =
                ObserverNexusDeathNodeAdminScreenClient.deathNodeAdminLayout(854 / 2, 480 / 2, 3, 1);

        assertEquals(12, layout.left());
        assertEquals(12, layout.top());
        assertEquals(403, layout.panelWidth());
        assertEquals(216, layout.panelHeight());
        assertEquals(12, layout.screenWidth() - layout.right());
        assertEquals(12, layout.screenHeight() - layout.bottom());
        assertTrue(layout.fits());
    }

    @Test
    void retainsTwoCompleteFixtureRowsAndEveryVerticalSemanticRegion() {
        ObserverNexusDeathNodeAdminScreenClient.DeathNodeAdminLayout layout =
                ObserverNexusDeathNodeAdminScreenClient.deathNodeAdminLayout(427, 240, 3, 1);

        assertEquals(2, layout.rowCapacity());
        assertEquals(2, layout.visibleRows());
        assertEquals(1, layout.firstRow());
        assertEquals(3, layout.firstRow() + layout.visibleRows());
        assertEquals(116, layout.lastRowBottom());
        assertEquals(124, layout.listBottom());
        assertEquals(132, layout.detailTop());
        assertEquals(192, layout.detailBottom());
        assertEquals(198, layout.confirmationY());
        assertTrue(layout.lastRowBottom()
                <= layout.listBottom() - ObserverNexusDeathNodeAdminScreenClient.LIST_PADDING);
        assertTrue(layout.detailTop() >= layout.listBottom() + ObserverNexusDeathNodeAdminScreenClient.DETAIL_GAP);
        assertTrue(layout.fits());
    }

    @Test
    void clampsScrollToTheCompleteLastPageAndUsesAdditionalHeight() {
        ObserverNexusDeathNodeAdminScreenClient.DeathNodeAdminLayout nativeLayout =
                ObserverNexusDeathNodeAdminScreenClient.deathNodeAdminLayout(427, 240, 10, 99);
        ObserverNexusDeathNodeAdminScreenClient.DeathNodeAdminLayout tallLayout =
                ObserverNexusDeathNodeAdminScreenClient.deathNodeAdminLayout(854, 480, 10, 99);

        assertEquals(8, nativeLayout.firstRow());
        assertEquals(2, nativeLayout.visibleRows());
        assertEquals(2, tallLayout.firstRow());
        assertEquals(8, tallLayout.visibleRows());
        assertTrue(nativeLayout.fits());
        assertTrue(tallLayout.fits());
    }

    @Test
    void longEnglishTraditionalChineseAndEmojiStayInsideThePixelBudget() {
        String source = "非常遙遠的終界死亡節點管理員 🧭 · minecraft:the_end_with_a_very_long_dimension_name";
        ObserverNexusDeathNodeAdminScreenClient.BoundedLabel label =
                ObserverNexusDeathNodeAdminScreenClient.boundedLabel(source, 126, MONOSPACE_WIDTH);

        assertTrue(label.text().endsWith("…"));
        assertTrue(label.text().contains("🧭"));
        assertTrue(label.fits());
        assertEquals(126, label.textWidth());
        assertFalse(hasUnpairedSurrogate(label.text()));
    }

    @Test
    void everyHeaderRowDetailAndConfirmationLabelUsesAMeasuredBoundary() {
        ObserverNexusDeathNodeAdminScreenClient.DeathNodeAdminLayout layout =
                ObserverNexusDeathNodeAdminScreenClient.deathNodeAdminLayout(427, 240, 3, 1);
        String[] semantics = {
                "死亡節點管理介面 Admin 🧭",
                "Page 9999 Total 999999 +",
                "Owner: 擁有非常長名字的管理員玩家",
                "Dimension: minecraft:overworld_with_a_long_namespace",
                "Status: awaiting_recovery_validation",
                "Time: since_the_beginning_of_this_world",
                "Administrator view 管理員檢視",
                "Ancient city 遠古城市節點",
                "Steve_with_a_long_owner_name",
                "disabled_pending_manual_review",
                "minecraft:the_nether_with_a_long_namespace",
                "-12345678, -2048, 98765432",
                "Diagnostics: stale_chunk, low_support, 跨維度診斷🧭",
                "Confirmation pending: permanently_purge_this_death_node"
        };
        int[] widths = {
                layout.titleWidth(), layout.pageWidth(), layout.contentWidth(), layout.contentWidth(),
                layout.threeColumnWidth(), layout.threeColumnWidth(), layout.thirdColumnWidth(),
                181, 89, 71, 241, 108, layout.contentWidth() - 16, layout.contentWidth()
        };

        for (int index = 0; index < semantics.length; index++) {
            ObserverNexusDeathNodeAdminScreenClient.BoundedLabel label =
                    ObserverNexusDeathNodeAdminScreenClient.boundedLabel(
                            semantics[index], widths[index], MONOSPACE_WIDTH);
            assertTrue(label.fits(), semantics[index]);
            assertFalse(hasUnpairedSurrogate(label.text()), semantics[index]);
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
