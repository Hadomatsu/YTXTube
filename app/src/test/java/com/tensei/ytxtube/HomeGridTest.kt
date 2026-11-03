package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGridTest {

    // Honor Magic V5: cover 1060x2376 px, inner 2172x2352 px, both about 403 ppi. The width in dp
    // depends on the display-size setting (density 2.6 to 3.0), so check the whole range.

    @Test
    fun coverScreen_getsTwoColumns() {
        for (dp in listOf(353f, 380f, 404f)) assertEquals("width $dp", 2, HomeGrid.columns(dp))
    }

    @Test
    fun innerScreen_getsFourColumns() {
        for (dp in listOf(724f, 790f, 827f)) assertEquals("width $dp", 4, HomeGrid.columns(dp))
    }

    @Test
    fun innerScreen_withASideRail_getsThree() {
        assertEquals(3, HomeGrid.columns(724f - 80f))
        assertEquals(4, HomeGrid.columns(827f - 80f))
    }

    @Test
    fun neverBelowTwoOrAboveSix() {
        assertEquals(2, HomeGrid.columns(200f))
        assertEquals(2, HomeGrid.columns(0f))
        assertEquals(6, HomeGrid.columns(2000f))
    }

    @Test
    fun cardsStayWideEnough() {
        for (dp in 300..1400 step 5) {
            val n = HomeGrid.columns(dp.toFloat())
            val card = (dp - 2 * HomeGrid.EDGE_DP - (n - 1) * HomeGrid.GAP_DP) / n
            if (n > HomeGrid.MIN_COLUMNS) assert(card >= HomeGrid.MIN_CARD_DP - 0.01f) { "width $dp -> $n columns, card $card dp" }
        }
    }
}
