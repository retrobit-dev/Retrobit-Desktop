package dev.retrobit.desktop.model

import java.time.LocalDate
import java.time.LocalTime

enum class EntryKind(val label: String) {
    EVENT("event"),
    TASK("task"),
    AGENDA("agenda"),
    BIRTHDAY("birthday"),
}

/**
 * One calendar entry. Birthdays repeat every year ([yearly] is forced on for them), tasks carry a
 * done flag, everything else is a plain dated entry with an optional time.
 */
data class CalendarEntry(
    val id: String,
    val title: String,
    val kind: EntryKind = EntryKind.EVENT,
    val date: LocalDate,
    val time: LocalTime? = null,
    val done: Boolean = false,
    val notes: String = "",
) {
    val repeatsYearly: Boolean get() = kind == EntryKind.BIRTHDAY

    /** True when this entry happens on [day] (year is ignored for birthdays). */
    fun occursOn(day: LocalDate): Boolean =
        if (repeatsYearly) date.monthValue == day.monthValue && date.dayOfMonth == day.dayOfMonth
        else date == day
}
