package com.tensei.ytxtube

/**
 * How many videos sit side by side on the home feed and in the "More videos" list of the watch
 * page.
 *
 * The count comes from the width the list actually gets (not from "phone vs tablet"), so a
 * foldable lands on the right layout on both screens: one big card per row on the cover screen
 * (about 350-410 dp wide) and two per row on the unfolded inner screen (about 720-830 dp), even
 * when a navigation rail takes some of that width.
 *
 * Plain Kotlin on purpose (no Compose types) so it can be unit tested.
 */
object HomeGrid {
    /** From this width on the list switches to two columns, in dp (the usual tablet breakpoint). */
    const val TWO_COLUMNS_FROM_DP = 600f

    /** Space between cards and at the screen edges, in dp. Must match the list padding. */
    const val GAP_DP = 10f
    const val EDGE_DP = 12f

    const val MIN_COLUMNS = 1
    const val MAX_COLUMNS = 2

    fun columns(widthDp: Float): Int = if (widthDp >= TWO_COLUMNS_FROM_DP) MAX_COLUMNS else MIN_COLUMNS
}
