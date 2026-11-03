package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordCaptionsTest {

    /** Shaped like a real YouTube auto-caption VTT: rolling two-line cues with inline word times. */
    // Built line by line so the lone-space lines YouTube emits survive editors that trim them.
    private val autoLines = listOf(
        "WEBVTT",
        "Kind: captions",
        "Language: en",
        "",
        "00:00:00.160 --> 00:00:02.230 align:start position:0%",
        " ",
        "hello<00:00:00.560><c> everyone</c><00:00:01.120><c> welcome</c><00:00:01.440><c> back</c>",
        "",
        "00:00:02.230 --> 00:00:02.240 align:start position:0%",
        "hello everyone welcome back",
        " ",
        "",
        "00:00:02.240 --> 00:00:04.710 align:start position:0%",
        "hello everyone welcome back",
        "to<00:00:02.480><c> the</c><00:00:02.720><c> channel</c><00:00:03.360><c> today</c>",
        "",
        "00:00:04.710 --> 00:00:04.720 align:start position:0%",
        "to the channel today",
        " ",
        "",
        "00:00:04.720 --> 00:00:06.549 align:start position:0%",
        "to the channel today",
        "we're<00:00:05.040><c> going</c><00:00:05.280><c> to</c><00:00:05.520><c> talk</c>",
    )
    private val autoVtt = autoLines.joinToString("\n")

    @Test
    fun autoCaptions_nothingBeforeTheFirstWord() {
        assertNull(WordCaptionParser.parseVtt(autoVtt).textAt(100))
    }

    @Test
    fun autoCaptions_wordsAppearOneByOne() {
        val t = WordCaptionParser.parseVtt(autoVtt)
        assertEquals("\nhello", t.textAt(300))
        assertEquals("\nhello everyone", t.textAt(700))
        // "welcome" has not been said yet at 1.0 s, so it must not be shown.
        assertEquals("\nhello everyone", t.textAt(1000))
        assertEquals("\nhello everyone welcome back", t.textAt(1500))
    }

    @Test
    fun autoCaptions_keepsPreviousLineForLookingBack() {
        val t = WordCaptionParser.parseVtt(autoVtt)
        // The finished line moves up, the new line grows underneath it.
        assertEquals("hello everyone welcome back\nto the", t.textAt(2500))
        assertEquals("hello everyone welcome back\nto the channel", t.textAt(3000))
    }

    @Test
    fun autoCaptions_dropsTheLineBeforeThePreviousOne() {
        val t = WordCaptionParser.parseVtt(autoVtt)
        // "going" only starts at 5.04 s, so at 4.9 s the newest word is still "we're".
        assertEquals("to the channel today\nwe're", t.textAt(4900))
        assertEquals("to the channel today\nwe're going", t.textAt(5100))
    }

    @Test
    fun autoCaptions_sameResultWhenTheLoneSpaceLinesAreStripped() {
        // Same file, but the " " lines came through empty, which splits each cue in two.
        val stripped = autoLines.map { if (it == " ") "" else it }.joinToString("\n")
        val a = WordCaptionParser.parseVtt(autoVtt)
        val b = WordCaptionParser.parseVtt(stripped)
        assertEquals(a.size, b.size)
        for (ms in listOf(300L, 700L, 1500L, 2500L, 3000L, 4900L, 5100L, 6000L, 8000L)) {
            assertEquals(a.textAt(ms), b.textAt(ms))
        }
    }

    @Test
    fun autoCaptions_rollingRepeatsAreNotDuplicated() {
        // 4 + 4 + 4 words; the plain repeat lines must not add anything.
        assertEquals(12, WordCaptionParser.parseVtt(autoVtt).size)
    }

    @Test
    fun autoCaptions_hidesAfterALongSilence() {
        val t = WordCaptionParser.parseVtt(autoVtt)
        assertEquals("to the channel today\nwe're going to talk", t.textAt(6000))
        assertNull(t.textAt(8000))
    }

    private val manualVtt = """
WEBVTT

1
00:00:01.000 --> 00:00:04.000
So what do we do now?
Let's find out.

2
00:00:04.500 --> 00:00:06.000
Okay.
""".trimIndent()

    @Test
    fun manualCaptions_wordsAreSpreadAcrossTheCue() {
        val t = WordCaptionParser.parseVtt(manualVtt)
        assertNull(t.textAt(900))
        assertEquals("\nSo", t.textAt(1000))
        assertEquals("\nSo what do we do", t.textAt(2000))
    }

    @Test
    fun manualCaptions_fullCueIsShownByItsEnd() {
        val t = WordCaptionParser.parseVtt(manualVtt)
        // 37 characters do not fit one line, so the last word wraps onto a line of its own.
        assertEquals("So what do we do now? Let's find\nout.", t.textAt(3900))
    }

    @Test
    fun manualCaptions_nextCueKeepsTheLastOneForContext() {
        val t = WordCaptionParser.parseVtt(manualVtt)
        assertEquals("out.\nOkay.", t.textAt(4600))
    }

    @Test
    fun manualCaptions_sameLineSpokenTwiceIsNotTreatedAsARepeat() {
        val vtt = """
WEBVTT

00:00:01.000 --> 00:00:02.000
Yes.

00:00:07.000 --> 00:00:08.000
Yes.
""".trimIndent()
        val t = WordCaptionParser.parseVtt(vtt)
        assertEquals(2, t.size)
        // A long pause before it: nothing from the first "Yes." is kept above it.
        assertEquals("\nYes.", t.textAt(7100))
    }

    @Test
    fun entitiesAndStyleTagsAreCleaned() {
        val vtt = """
WEBVTT

00:00:01.000 --> 00:00:03.000
<c.colorE5E5E5>don&#39;t</c> stop &amp; go
""".trimIndent()
        val t = WordCaptionParser.parseVtt(vtt)
        assertEquals("\ndon't stop & go", t.textAt(2999))
    }

    @Test
    fun emptyOrGarbageInputGivesAnEmptyTimeline() {
        assertEquals(true, WordCaptionParser.parseVtt("").isEmpty)
        assertEquals(true, WordCaptionParser.parseVtt("WEBVTT\n\nnot a cue").isEmpty)
        assertNull(WordCaptionParser.parseVtt("").textAt(1000))
    }

    @Test
    fun layout_wordsOnlyEverAppendToTheBottomLine() {
        // Walk through the auto-caption sample in 10 ms steps. Between two neighbouring frames
        // either the bottom line grew by appending words (top line unchanged), or a new line
        // started and the old bottom line moved up as it was. Nothing is ever edited in the middle.
        val t = WordCaptionParser.parseVtt(autoVtt)
        var previous: Pair<String, String>? = null
        var ms = 0L
        while (ms <= 7_000L) {
            val now = t.textAt(ms)?.let { captionSlots(it) }
            val before = previous
            if (before != null && now != null) {
                val grew = now.second.startsWith(before.second) && now.first == before.first
                val rolled = now.first == before.second
                assertEquals("at $ms ms: $before -> $now", true, grew || rolled)
            }
            previous = now
            ms += 10L
        }
    }

    @Test
    fun layout_aLongCueIsWrappedToTheLineWidth() {
        val vtt = """
WEBVTT

00:00:01.000 --> 00:00:09.000
This is a rather long sentence that somebody wrote on one single caption line for no reason.
""".trimIndent()
        val t = WordCaptionParser.parseVtt(vtt)
        var ms = 900L
        while (ms <= 9_500L) {
            val (top, bottom) = captionSlots(t.textAt(ms) ?: "\n")
            assertEquals("top at $ms: '$top'", true, top.length <= WordCaptionStyle.MAX_LINE_CHARS)
            assertEquals("bottom at $ms: '$bottom'", true, bottom.length <= WordCaptionStyle.MAX_LINE_CHARS)
            ms += 50L
        }
        // The very end shows the last two lines, the first one has scrolled away.
        val (top, bottom) = captionSlots(t.textAt(8_900L)!!)
        assertEquals("single caption line for no", top)
        assertEquals("reason.", bottom)
    }

    @Test
    fun layout_aPauseStartsAFreshPlate() {
        val vtt = """
WEBVTT

00:00:01.000 --> 00:00:02.000
First thing.

00:00:09.000 --> 00:00:10.000
Much later.
""".trimIndent()
        val t = WordCaptionParser.parseVtt(vtt)
        assertEquals("\nFirst thing.", t.textAt(1_900))
        assertEquals("\nMuch", t.textAt(9_000))
        assertEquals(Pair("", "Much later."), captionSlots(t.textAt(9_900)!!))
    }

    @Test
    fun slots_splitIntoTopAndBottom() {
        assertEquals(Pair("a", "b"), captionSlots("a\nb"))
        assertEquals(Pair("", "b"), captionSlots("\nb"))
        assertEquals(Pair("", "b"), captionSlots("b"))
        assertEquals(Pair("y", "z"), captionSlots("x\ny\nz"))
    }

    @Test
    fun timestampParsing() {
        assertEquals(3_723_450L, WordCaptionParser.parseTimestampMs("01:02:03.450"))
        assertEquals(62_500L, WordCaptionParser.parseTimestampMs("01:02,500"))
        assertNull(WordCaptionParser.parseTimestampMs("nope"))
    }
}
