package dev.retrobit.desktop.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import dev.retrobit.desktop.model.CalendarEntry
import dev.retrobit.desktop.model.EntryKind
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Agenda / tasks / events / birthdays, stored on the device as JSON inside SharedPreferences.
 * The list itself is a Compose snapshot list so the widget and the calendar window update live.
 */
class CalendarStore(context: Context) {

    private val sp = context.getSharedPreferences("retrobit_calendar", Context.MODE_PRIVATE)

    val entries: SnapshotStateList<CalendarEntry> = mutableStateListOf()

    init {
        entries.addAll(decode(sp.getString(KEY, null)))
    }

    // ── queries ─────────────────────────────────────────────────────────────────
    fun on(day: LocalDate): List<CalendarEntry> = CalendarMath.entriesOn(entries, day)

    fun upcoming(from: LocalDate = LocalDate.now(), days: Int = 7) = CalendarMath.upcoming(entries, from, days)

    fun openTasks(until: LocalDate? = null) = CalendarMath.openTasks(entries, until)

    // ── mutations ───────────────────────────────────────────────────────────────
    fun add(
        title: String,
        date: LocalDate,
        kind: EntryKind = EntryKind.EVENT,
        time: LocalTime? = null,
        notes: String = "",
    ): CalendarEntry? {
        val clean = title.trim()
        if (clean.isEmpty()) return null
        val entry = CalendarEntry(
            id = UUID.randomUUID().toString(),
            title = clean,
            kind = kind,
            date = date,
            time = time,
            notes = notes,
        )
        entries += entry
        sort()
        persist()
        return entry
    }

    fun update(entry: CalendarEntry) {
        val i = entries.indexOfFirst { it.id == entry.id }
        if (i < 0) return
        entries[i] = entry
        sort()
        persist()
    }

    fun toggleDone(id: String) {
        val i = entries.indexOfFirst { it.id == id }
        if (i < 0) return
        val e = entries[i]
        entries[i] = e.copy(done = !e.done)
        persist()
    }

    fun remove(id: String) {
        entries.removeAll { it.id == id }
        persist()
    }

    fun clearAll() {
        entries.clear()
        persist()
    }

    private fun sort() {
        val sorted = entries.sortedWith(compareBy({ it.date }, { it.time }, { it.title.lowercase() }))
        entries.clear()
        entries.addAll(sorted)
    }

    // ── persistence ─────────────────────────────────────────────────────────────
    private fun persist() {
        sp.edit().putString(KEY, encode()).apply()
    }

    fun encode(): String {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("title", e.title)
                    put("kind", e.kind.name)
                    put("date", e.date.toString())
                    if (e.time != null) put("time", String.format("%02d:%02d", e.time.hour, e.time.minute))
                    put("done", e.done)
                    if (e.notes.isNotEmpty()) put("notes", e.notes)
                }
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<CalendarEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull()
                    ?: return@mapNotNull null
                val time = parseTime(o.optString("time"))
                CalendarEntry(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    title = o.optString("title").ifBlank { "untitled" },
                    kind = runCatching { EntryKind.valueOf(o.optString("kind")) }.getOrDefault(EntryKind.EVENT),
                    date = date,
                    time = time,
                    done = o.optBoolean("done", false),
                    notes = o.optString("notes"),
                )
            }.sortedWith(compareBy({ it.date }, { it.time }, { it.title.lowercase() }))
        }.getOrDefault(emptyList())
    }

    private fun parseTime(raw: String): LocalTime? {
        if (raw.isBlank() || raw == "null") return null
        return CalendarMath.parseTime(raw)
    }

    companion object {
        private const val KEY = "entries"
    }
}
