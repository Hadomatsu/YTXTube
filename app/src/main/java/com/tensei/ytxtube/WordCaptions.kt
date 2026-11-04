package com.tensei.ytxtube

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Word-by-word captions, laid out like YouTube's own subtitles.
 *
 * The captions are cut into display lines once, up front, and a line never changes after that:
 * words are only ever added to the end of the line that is being spoken, left to right. The
 * first line after a pause fills the top slot, the next one the bottom slot. When the bottom line
 * is full, the top line vanishes, the bottom line moves up to the top, and the new words come in
 * on the bottom, and so on. Nothing already on screen moves sideways, and nothing from the future
 * is ever shown.
 *
 * Word timing comes from two places:
 *  - YouTube's auto-generated (ASR) captions carry a timestamp in front of every word inside the
 *    WebVTT text (`word<00:00:01.760><c> next</c>`), so those are exact, and their own line
 *    breaks are used as the line breaks.
 *  - Human-written captions only have a start and end per cue, so the words are spread over the
 *    cue at roughly speaking pace and wrapped to the line width.
 *
 * This file is plain Kotlin on purpose (no Android types) so it can be unit tested on a laptop.
 */
data class CaptionWord(
    val startMs: Long,
    /** When this word stops being the newest thing on screen (end of its cue / line). */
    val endMs: Long,
    val text: String,
    /** First word of a source caption line; always starts a new display line. */
    val lineStart: Boolean,
)

/** Tuning knobs for what the caption plate shows. */
object WordCaptionStyle {
    /** How long the last word lingers after its line ends. */
    const val HOLD_MS = 400L

    /**
     * Most characters on one display line. The plate is drawn [LINE_WIDTH_EM] wide so a line this
     * long always fits, whatever the letters are.
     */
    const val MAX_LINE_CHARS = 32
    const val LINE_WIDTH_EM = 21f

    /** A pause longer than this ends the line, and the line before it is not kept on screen. */
    const val MAX_GAP_MS = 1_500L
}

/**
 * Splits the text of a caption into the two fixed slots of the plate. [text] holds the top line,
 * a line break and the bottom line; the top line may be empty. Anything else (a cue that came
 * from the player itself) is reduced to its last two lines.
 */
fun captionSlots(text: String): Pair<String, String> {
    val lines = text.split('\n')
    val bottom = lines.last()
    val top = if (lines.size >= 2) lines[lines.size - 2] else ""
    return top to bottom
}

class WordTimeline(words: List<CaptionWord>) {
    private val words: List<CaptionWord> = words.sortedBy { it.startMs }

    /** Index of the first word of every display line. */
    private val lineStarts: IntArray

    /** Display line of every word. */
    private val lineOf: IntArray

    /** Lines that start after a pause (or the very first line): nothing is kept above them. */
    private val fresh: BooleanArray

    init {
        val starts = ArrayList<Int>()
        val pausedBefore = ArrayList<Boolean>()
        val owner = IntArray(this.words.size)
        var chars = 0
        for (i in this.words.indices) {
            val w = this.words[i]
            val gap = i > 0 && w.startMs - this.words[i - 1].endMs > WordCaptionStyle.MAX_GAP_MS
            // YouTube's own line breaks in automatic captions fall anywhere ("touching" / "that."), so
            // lines are packed to the width instead; a source break only counts after a sentence end.
            val sentenceBreak = i > 0 && w.lineStart &&
                (this.words[i - 1].text.trimEnd('"', '\'', ')', ']').lastOrNull() ?: ' ') in SENTENCE_ENDS
            val newLine = i == 0 || gap || sentenceBreak ||
                chars + 1 + w.text.length > WordCaptionStyle.MAX_LINE_CHARS
            if (newLine) {
                starts += i
                pausedBefore += (i == 0 || gap)
                chars = w.text.length
            } else {
                chars += 1 + w.text.length
            }
            owner[i] = starts.size - 1
        }
        lineStarts = starts.toIntArray()
        lineOf = owner
        fresh = pausedBefore.toBooleanArray()
    }

    val isEmpty: Boolean get() = words.isEmpty()
    val size: Int get() = words.size

    /**
     * What should be on screen at [posMs]: the previous line, a line break and the line being
     * spoken (the previous line is empty when there was a pause before this one). Null when
     * nothing has been said yet or the speaker has gone quiet.
     */
    fun textAt(posMs: Long): String? {
        if (words.isEmpty() || posMs < words[0].startMs) return null

        // Newest word that has started.
        var lo = 0
        var hi = words.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (words[mid].startMs <= posMs) lo = mid else hi = mid - 1
        }

        // Newest word that is still "alive". Usually that is the one we just found; it only
        // differs when cues overlap or a short cue sits inside a longer one.
        var anchor = -1
        var i = lo
        val floor = max(0, lo - ANCHOR_LOOKBACK)
        while (i >= floor) {
            if (words[i].endMs + WordCaptionStyle.HOLD_MS >= posMs) { anchor = i; break }
            i--
        }
        if (anchor < 0) return null

        val line = lineOf[anchor]
        val first = lineStarts[line]
        return if (fresh[line]) {
            // First line after a pause, still being spoken: it fills the top slot.
            join(first, anchor) + "\n"
        } else {
            // Any later line fills the bottom slot, with the line before it whole above it.
            join(lineStarts[line - 1], first - 1) + "\n" + join(first, anchor)
        }
    }

    private fun join(from: Int, to: Int): String {
        val sb = StringBuilder()
        for (k in from..to) {
            if (k > from) sb.append(' ')
            sb.append(words[k].text)
        }
        return sb.toString()
    }

    private companion object {
        const val ANCHOR_LOOKBACK = 32
        val SENTENCE_ENDS = setOf('.', '?', '!', '♪')
    }
}

object WordCaptionParser {
    private val TIME_LINE =
        Regex("""((?:\d{1,2}:)?\d{2}:\d{2}[.,]\d{3})\s*-->\s*((?:\d{1,2}:)?\d{2}:\d{2}[.,]\d{3})""")
    private val INLINE_TS = Regex("""<((?:\d{1,2}:)?\d{2}:\d{2}[.,]\d{3})>""")
    private val ANY_TAG = Regex("<[^>]*>")
    private val NUMERIC_ENTITY = Regex("&#(\\d+);")
    private val SPACES = Regex("[\\s\\u00A0]+")
    private val BLOCK_SPLIT = Regex("\r?\n\r?\n+")

    /** Rough speaking pace used to spread a cue's words when there is no per-word timing. */
    private const val MS_PER_CHAR = 70L
    private const val MIN_SPREAD_MS = 600L
    private const val MS_PER_WORD_FALLBACK = 400L

    /** Two identical plain lines this close together are YouTube's rolling-caption repeats. */
    private const val REPEAT_SLACK_MS = 200L

    fun parseVtt(raw: String): WordTimeline {
        val words = ArrayList<CaptionWord>()
        // Lines already emitted: (normalised text, time it stops being on screen).
        val recent = ArrayList<Pair<String, Long>>()
        val seenTimed = HashSet<String>()

        for (block in blocksOf(raw)) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val timeIndex = lines.indexOfFirst { TIME_LINE.containsMatchIn(it) }
            if (timeIndex < 0) continue
            val match = TIME_LINE.find(lines[timeIndex]) ?: continue
            val cueStart = parseTimestampMs(match.groupValues[1]) ?: continue
            val cueEnd = parseTimestampMs(match.groupValues[2]) ?: continue
            val text = lines.drop(timeIndex + 1)
            if (text.isEmpty()) continue

            val timed = text.filter { INLINE_TS.containsMatchIn(it) }
            if (timed.isNotEmpty()) {
                // Auto-generated captions. The untagged lines in the same block are just the
                // previous line being repeated, so only the tagged line carries new words.
                for (line in timed) {
                    val parsed = timedLineWords(line, cueStart, cueEnd)
                    if (parsed.isEmpty()) continue
                    val key = parsed.first().startMs.toString() + "|" + joined(parsed)
                    if (!seenTimed.add(key)) continue
                    words += parsed
                    recent += normalise(joined(parsed)) to parsed.last().endMs
                }
                continue
            }

            // Plain cue. YouTube also repeats finished auto-caption lines as plain text, so
            // drop lines we already emitted that are still on screen when this block starts.
            val plainLines = text.map { clean(it) }.filter { it.isNotEmpty() }
            if (plainLines.isEmpty()) continue
            val fresh = plainLines.filter { line ->
                val n = normalise(line)
                recent.none { (seen, until) -> seen == n && until >= cueStart - REPEAT_SLACK_MS }
            }
            if (fresh.isEmpty()) continue
            val emitted = if (fresh.size == plainLines.size) {
                // A normal (human) cue, possibly two lines: spread it as one piece of speech.
                listOf(plainLines.joinToString(" "))
            } else {
                fresh
            }
            for (line in emitted) {
                words += plainWords(line, cueStart, cueEnd)
                recent += normalise(line) to cueEnd
            }
        }
        return WordTimeline(words)
    }

    /**
     * Split a VTT file into cue blocks. YouTube puts a lone space on the line after the timing
     * line; if anything strips that space the line becomes empty and the timing line ends up in
     * a block of its own, so a timing-only block is glued back onto the text block after it.
     */
    private fun blocksOf(raw: String): List<String> {
        val blocks = raw.trim().split(BLOCK_SPLIT)
        val out = ArrayList<String>(blocks.size)
        var i = 0
        while (i < blocks.size) {
            val block = blocks[i]
            val next = blocks.getOrNull(i + 1)
            if (next != null && endsWithTiming(block) && !TIME_LINE.containsMatchIn(next) && !isMetadata(next)) {
                out += block + "\n" + next
                i += 2
            } else {
                out += block
                i++
            }
        }
        return out
    }

    private fun endsWithTiming(block: String): Boolean {
        val last = block.lines().lastOrNull { it.isNotBlank() } ?: return false
        return TIME_LINE.containsMatchIn(last)
    }

    private fun isMetadata(block: String): Boolean {
        val head = block.trimStart().substringBefore(' ').substringBefore('\n')
        return head == "NOTE" || head == "STYLE" || head == "REGION"
    }

    /** Spread a cue's words over it at speaking pace. Used when no per-word timing exists. */
    fun plainWords(text: String, cueStart: Long, cueEnd: Long): List<CaptionWord> {
        val tokens = text.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val duration = max(1L, cueEnd - cueStart)
        val totalWeight = tokens.sumOf { it.length + 1 }.toLong()
        val span = min(duration, max(MIN_SPREAD_MS, totalWeight * MS_PER_CHAR))
        val end = max(cueEnd, cueStart + 1)
        var weight = 0L
        return tokens.mapIndexed { index, token ->
            val start = cueStart + span * weight / totalWeight
            weight += token.length + 1
            CaptionWord(start, max(end, start + 1), token, lineStart = index == 0)
        }
    }

    private data class Segment(val start: Long, val text: String)

    private fun timedLineWords(line: String, cueStart: Long, cueEnd: Long): List<CaptionWord> {
        val segments = ArrayList<Segment>()
        var cursor = 0
        var segmentStart = cueStart
        for (m in INLINE_TS.findAll(line)) {
            val chunk = clean(line.substring(cursor, m.range.first))
            if (chunk.isNotEmpty()) segments += Segment(segmentStart, chunk)
            segmentStart = parseTimestampMs(m.groupValues[1]) ?: segmentStart
            cursor = m.range.last + 1
        }
        val tail = clean(line.substring(cursor))
        if (tail.isNotEmpty()) segments += Segment(segmentStart, tail)
        if (segments.isEmpty()) return emptyList()

        val lastSegment = segments.last()
        val lastTokens = lastSegment.text.split(' ').count { it.isNotEmpty() }
        val lineEnd = max(cueEnd, lastSegment.start + lastTokens * MS_PER_WORD_FALLBACK)

        val out = ArrayList<CaptionWord>()
        segments.forEachIndexed { index, segment ->
            val tokens = segment.text.split(' ').filter { it.isNotEmpty() }
            val isLast = index == segments.lastIndex
            val segmentEnd = if (isLast) lineEnd else max(segment.start, segments[index + 1].start)
            // A segment can hold several words; share its time between them.
            val spreadEnd = if (isLast) min(segmentEnd, segment.start + tokens.size * MS_PER_WORD_FALLBACK) else segmentEnd
            val total = tokens.sumOf { it.length + 1 }.toLong()
            var weight = 0L
            tokens.forEachIndexed { t, token ->
                val start = segment.start + (spreadEnd - segment.start) * weight / total
                weight += token.length + 1
                val end = if (t == tokens.lastIndex) segmentEnd else segment.start + (spreadEnd - segment.start) * weight / total
                out += CaptionWord(start, max(end, start + 1), token, lineStart = out.isEmpty())
            }
        }
        return out
    }

    private fun joined(words: List<CaptionWord>) = words.joinToString(" ") { it.text }

    private fun normalise(s: String) = s.lowercase().replace(SPACES, " ").trim()

    private fun clean(s: String): String = decode(s.replace(ANY_TAG, "")).replace(SPACES, " ").trim()

    private fun decode(s: String): String {
        if (!s.contains('&')) return s
        return s
            .replace(NUMERIC_ENTITY) { m ->
                m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
            }
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    internal fun parseTimestampMs(raw: String): Long? {
        val parts = raw.trim().replace(',', '.').split(':')
        return try {
            when (parts.size) {
                2 -> parts[0].toLong() * 60_000L + (parts[1].toDouble() * 1000).roundToLong()
                3 -> parts[0].toLong() * 3_600_000L + parts[1].toLong() * 60_000L + (parts[2].toDouble() * 1000).roundToLong()
                else -> null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
}
