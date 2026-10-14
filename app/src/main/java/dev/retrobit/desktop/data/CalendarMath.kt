package dev.retrobit.desktop.data

import dev.retrobit.desktop.model.CalendarEntry
import dev.retrobit.desktop.model.EntryKind
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure date maths behind the calendar widget and the calendar window.
 * No Android imports, so it runs in plain JVM unit tests (see CalendarMathTest.kt).
 */
object CalendarMath {

    /** Number of cells in the month view (6 weeks × 7 days). */
    const val CELLS = 42

    /** 6×7 grid for [month]; null cells are the padding days before/after the month. */
    fun monthGrid(month: YearMonth): List<List<LocalDate?>> {
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - 1 // Monday = 0
        val days = month.lengthOfMonth()
        val cells = ArrayList<LocalDate?>(CELLS)
        repeat(lead) { cells += null }
        for (d in 1..days) cells += month.atDay(d)
        while (cells.size < CELLS) cells += null
        return cells.chunked(7)
    }

    /** Weekday headers, Monday first, in the given locale (short: "Mon"). */
    fun weekdayHeaders(locale: Locale = Locale.getDefault()): List<String> =
        (1..7).map { java.time.DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale) }

    /** Does [entry] fall on [day] (birthdays match by month/day)? */
    fun occursOn(entry: CalendarEntry, day: LocalDate): Boolean = entry.occursOn(day)

    /** Entries of [day], tasks first, then by time, then alphabetically. */
    fun entriesOn(entries: List<CalendarEntry>, day: LocalDate): List<CalendarEntry> =
        entries.filter { it.occursOn(day) }.sortedWith(entryOrder)

    /** Days inside [month] that have at least one entry. */
    fun daysWithEntries(entries: List<CalendarEntry>, month: YearMonth): Set<LocalDate> {
        val out = LinkedHashSet<LocalDate>()
        val days = month.lengthOfMonth()
        for (d in 1..days) {
            val day = month.atDay(d)
            if (entries.any { it.occursOn(day) }) out += day
        }
        // birthdays that only exist in a past/future year still show on the correct day
        entries.filter { it.repeatsYearly }.forEach { entry ->
            if (entry.date.monthValue == month.monthValue) {
                out += month.atDay(entry.date.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
            }
        }
        return out
    }

    /** The next [days] days (starting at [from]) that have entries, oldest first. */
    fun upcoming(entries: List<CalendarEntry>, from: LocalDate, days: Int = 7): List<Pair<LocalDate, CalendarEntry>> {
        val out = ArrayList<Pair<LocalDate, CalendarEntry>>()
        for (i in 0 until days) {
            val day = from.plusDays(i.toLong())
            entriesOn(entries, day).forEach { out += day to it }
        }
        return out
    }

    /** Open (unfinished) tasks, oldest first, optionally limited to [until]. */
    fun openTasks(entries: List<CalendarEntry>, until: LocalDate? = null): List<CalendarEntry> =
        entries.filter { it.kind == EntryKind.TASK && !it.done && (until == null || !it.date.isAfter(until)) }
            .sortedBy { it.date }

    fun prettyDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} ${date.dayOfMonth} " +
            "${date.month.getDisplayName(TextStyle.SHORT, locale)} ${date.year}"

    fun prettyMonth(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        "${month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() }} ${month.year}"

    fun prettyTime(time: LocalTime?): String = time?.let { String.format(Locale.US, "%02d:%02d", it.hour, it.minute) } ?: "--:--"

    private val entryOrder = compareBy<CalendarEntry>({ it.kind.ordinal }, { it.time ?: LocalTime.MAX }, { it.title.lowercase() })

    /** Parses "2026-10-20" or "20/10/2026", returns null when unusable. */
    fun parseDate(raw: String): LocalDate? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val patterns = listOf(
            Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})"),
            Regex("(\\d{1,2})/(\\d{1,2})/(\\d{4})"),
            Regex("(\\d{1,2})-(\\d{1,2})-(\\d{4})"),
        )
        for ((i, rx) in patterns.withIndex()) {
            val m = rx.matchEntire(text) ?: continue
            val (y, mo, d) = if (i == 0) {
                Triple(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            } else {
                Triple(m.groupValues[3], m.groupValues[2], m.groupValues[1])
            }
            runCatching { return LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }
        }
        return runCatching { LocalDate.parse(text) }.getOrNull()
    }

    /** Parses "14:30" / "9.30" / "9", returns null when unusable. */
    fun parseTime(raw: String?): LocalTime? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val m = Regex("(\\d{1,2})[:.](\\d{2})").matchEntire(text)
        if (m != null) {
            runCatching { return LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        }
        val h = text.toIntOrNull()
        if (h != null) runCatching { return LocalTime.of(h, 0) }
        return null
    }
}
