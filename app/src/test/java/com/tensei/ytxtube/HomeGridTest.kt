package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGridTest {

    // Honor Magic V5: cover 1060x2376 px, inner 2172x2352 px, both about 403 ppi. The width in dp
    // depends on the display-size setting (density 2.6 to 3.0), so check the whole range.

    @Test
    fun coverScreen_getsOneColumn() {
        for (dp in listOf(353f, 380f, 404f)) assertEquals("width $dp", 1, HomeGrid.columns(dp))
    }

    @Test
    fun innerScreen_getsTwoColumns() {
        for (dp in listOf(724f, 790f, 827f)) assertEquals("width $dp", 2, HomeGrid.columns(dp))
    }

    @Test
    fun innerScreen_withASideRail_stillGetsTwo() {
        assertEquals(2, HomeGrid.columns(724f - 80f))
        assertEquals(2, HomeGrid.columns(827f - 80f))
    }

    @Test
    fun neverBelowOneOrAboveTwo() {
        assertEquals(1, HomeGrid.columns(0f))
        assertEquals(1, HomeGrid.columns(200f))
        assertEquals(2, HomeGrid.columns(2000f))
    }

    @Test
    fun switchesAtTheTabletBreakpoint() {
        assertEquals(1, HomeGrid.columns(599f))
        assertEquals(2, HomeGrid.columns(600f))
    }

    @Test
    fun twoCardsStayWideEnough() {
        for (dp in 600..1400 step 5) {
            val card = (dp - 2 * HomeGrid.EDGE_DP - HomeGrid.GAP_DP) / 2
            assert(card >= 250f) { "width $dp -> card $card dp" }
        }
    }
}
