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
        assertEquals("hello", t.textAt(300))
        assertEquals("hello everyone", t.textAt(700))
        // "welcome" has not been said yet at 1.0 s, so it must not be shown.
        assertEquals("hello everyone", t.textAt(1000))
        assertEquals("hello everyone welcome back", t.textAt(1500))
    }

    @Test
    fun autoCaptions_keepsPreviousLineForLookingBack() {
        val t = WordCaptionParser.parseVtt(autoVtt)
        assertEquals("hello everyone welcome back to the", t.textAt(2500))
        assertEquals("hello everyone welcome back to the channel", t.textAt(3000))
    }

    @Test
    fun autoCaptions_dropsTheLineBeforeThePreviousOne() {
        val t = WordCaptionParser.parseVtt(autoVtt)
        // "going" only starts at 5.04 s, so at 4.9 s the newest word is still "we're".
        assertEquals("to the channel today we're", t.textAt(4900))
        assertEquals("to the channel today we're going", t.textAt(5100))
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
        assertEquals("to the channel today we're going to talk", t.textAt(6000))
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
        assertEquals("So", t.textAt(1000))
        assertEquals("So what do we do", t.textAt(2000))
    }

    @Test
    fun manualCaptions_fullCueIsShownByItsEnd() {
        val t = WordCaptionParser.parseVtt(manualVtt)
        assertEquals("So what do we do now? Let's find out.", t.textAt(3900))
    }

    @Test
    fun manualCaptions_nextCueKeepsTheLastOneForContext() {
        val t = WordCaptionParser.parseVtt(manualVtt)
        assertEquals("So what do we do now? Let's find out. Okay.", t.textAt(4600))
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
        assertEquals("Yes.", t.textAt(7100))
    }

    @Test
    fun entitiesAndStyleTagsAreCleaned() {
        val vtt = """
WEBVTT

00:00:01.000 --> 00:00:03.000
<c.colorE5E5E5>don&#39;t</c> stop &amp; go
""".trimIndent()
        val t = WordCaptionParser.parseVtt(vtt)
        assertEquals("don't stop & go", t.textAt(2999))
    }

    @Test
    fun emptyOrGarbageInputGivesAnEmptyTimeline() {
        assertEquals(true, WordCaptionParser.parseVtt("").isEmpty)
        assertEquals(true, WordCaptionParser.parseVtt("WEBVTT\n\nnot a cue").isEmpty)
        assertNull(WordCaptionParser.parseVtt("").textAt(1000))
    }

    @Test
    fun timestampParsing() {
        assertEquals(3_723_450L, WordCaptionParser.parseTimestampMs("01:02:03.450"))
        assertEquals(62_500L, WordCaptionParser.parseTimestampMs("01:02,500"))
        assertNull(WordCaptionParser.parseTimestampMs("nope"))
    }
}
