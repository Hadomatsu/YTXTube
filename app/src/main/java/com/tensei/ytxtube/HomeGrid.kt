package com.tensei.ytxtube

/**
 * How many videos fit side by side on the home feed.
 *
 * The count comes from the width the list actually gets (not from "phone vs tablet"), so a
 * foldable lands on the right layout on both screens: 2 columns on a cover screen of about
 * 350-410 dp, 4 columns on an unfolded inner screen of about 720-830 dp, and fewer again when a
 * navigation rail or side panel takes some of that width.
 *
 * Plain Kotlin on purpose (no Compose types) so it can be unit tested.
 */
object HomeGrid {
    /** Narrowest a card is allowed to get, in dp. */
    const val MIN_CARD_DP = 165f

    /** Space between cards and at the screen edges, in dp. Must match the list padding. */
    const val GAP_DP = 10f
    const val EDGE_DP = 12f

    const val MIN_COLUMNS = 2
    const val MAX_COLUMNS = 6

    fun columns(widthDp: Float): Int {
        val usable = widthDp - 2 * EDGE_DP + GAP_DP
        return (usable / (MIN_CARD_DP + GAP_DP)).toInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS)
    }
}
