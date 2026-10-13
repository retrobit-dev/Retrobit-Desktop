package dev.retrobit.desktop.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.data.CalendarMath
import dev.retrobit.desktop.model.CalendarEntry
import dev.retrobit.desktop.model.EntryKind
import dev.retrobit.desktop.model.WinKind
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.RetroIconButton
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.theme.LocalPalette
import java.time.LocalDate
import java.time.YearMonth

/**
 * Calendar widget for the desktop: month grid, day selection, agenda for the selected day and an
 * inline editor so entries (event / task / agenda / birthday) can be added without leaving the
 * home screen. Everything is stored by [dev.retrobit.desktop.data.CalendarStore].
 */
@Composable
fun CalendarWidget(vm: LauncherViewModel, modifier: Modifier = Modifier, width: Dp = dp(250f)) {
    val p = LocalPalette.current
    val store = vm.calendar
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    var editing by remember { mutableStateOf(false) }

    val marked = remember(store.entries.toList(), month) { CalendarMath.daysWithEntries(store.entries, month) }
    val dayEntries = remember(store.entries.toList(), selected) { store.on(selected) }

    Column(
        modifier
            .width(width)
            .background(p.paper)
            .border(dp(1.6f), p.ink),
    ) {
        // ── title bar ─────────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().background(p.soft).padding(horizontal = dp(6f), vertical = dp(4f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(5f)),
        ) {
            GlyphIcon(Glyph.CALENDAR, dp(13f))
            RetroText(CalendarMath.prettyMonth(month), size = 11f, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            RetroIconButton(label = "<", boxSize = dp(19f)) { month = month.minusMonths(1) }
            RetroIconButton(label = ">", boxSize = dp(19f)) { month = month.plusMonths(1) }
        }
        Box(Modifier.fillMaxWidth().height(dp(1.2f)).background(p.ink))

        // ── month grid ────────────────────────────────────────────────────────────
        Column(Modifier.padding(horizontal = dp(6f), vertical = dp(5f)), verticalArrangement = Arrangement.spacedBy(dp(2f))) {
            Row(Modifier.fillMaxWidth()) {
                CalendarMath.weekdayHeaders().forEach { label ->
                    RetroText(
                        label.take(2),
                        size = 10f,
                        color = p.mid,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                }
            }
            CalendarMath.monthGrid(month).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { day ->
                        Box(Modifier.weight(1f)) {
                            if (day == null) {
                                Spacer(Modifier.height(dp(22f)))
                            } else {
                                DayCell(
                                    day = day,
                                    isToday = day == today,
                                    isSelected = day == selected,
                                    hasEntries = day in marked,
                                    onSelect = {
                                        selected = day
                                        editing = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(dp(1.2f)).background(p.ink))

        // ── agenda for the selected day ───────────────────────────────────────────
        Column(
            Modifier.fillMaxWidth().height(dp(132f)).verticalScroll(rememberScrollState()).padding(dp(7f)),
            verticalArrangement = Arrangement.spacedBy(dp(5f)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
                RetroText(CalendarMath.prettyDate(selected), size = 11f, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                RetroIconButton(label = "+", boxSize = dp(20f), active = editing) { editing = !editing }
            }
            if (editing) {
                EntryEditor(vm, defaultDate = selected, onClose = { editing = false })
            }
            if (dayEntries.isEmpty() && !editing) {
                RetroText("nothing planned — tap + to add", size = 10f, color = p.mid, maxLines = 2)
            }
            dayEntries.forEach { entry ->
                EntryRow(vm, entry, compact = true) { vm.openWindow(WinKind.CALENDAR) }
            }
            val tasks = vm.calendar.openTasks(selected)
            if (tasks.isNotEmpty()) {
                RetroText("open tasks (${tasks.size})", size = 10f, color = p.mid)
                tasks.take(4).forEach { task ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(5f))) {
                        RetroIconButton(label = if (task.done) "x" else "-", boxSize = dp(17f)) { vm.calendar.toggleDone(task.id) }
                        RetroText(task.title, size = 10f, color = p.mid, maxLines = 1, modifier = Modifier.weight(1f))
                        RetroText(CalendarMath.prettyTime(task.time), size = 9f, color = p.mid)
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(dp(1.2f)).background(p.ink))
        Row(
            Modifier.fillMaxWidth().background(p.soft).padding(horizontal = dp(7f), vertical = dp(4f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(6f)),
        ) {
            RetroText(
                "${vm.calendar.entries.size} entries · ${vm.calendar.openTasks().size} open tasks",
                size = 9.5f, color = p.mid, maxLines = 1, modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        vm.openWindow(WinKind.CALENDAR)
                    }
                    .padding(horizontal = dp(3f)),
            ) {
                RetroText("open", size = 9.5f, weight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    hasEntries: Boolean,
    onSelect: () -> Unit,
) {
    val p = LocalPalette.current
    val bg = when {
        isSelected -> p.ink
        isToday -> p.soft
        else -> p.paper
    }
    val fg = if (isSelected) p.paper else p.ink
    Column(
        Modifier
            .fillMaxWidth()
            .height(dp(22f))
            .background(bg)
            .border(dp(if (isToday && !isSelected) 1.2f else 0.8f), if (isSelected) p.ink else p.ink.copy(alpha = 0.25f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSelect),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RetroText("${day.dayOfMonth}", size = 10f, color = fg, maxLines = 1)
        if (hasEntries) {
            Box(Modifier.size(dp(3f)).background(fg))
        }
    }
}

/** One agenda line: type tag, title, time, done toggle and a delete button. */
@Composable
fun EntryRow(
    vm: LauncherViewModel,
    entry: CalendarEntry,
    compact: Boolean,
    onOpen: (() -> Unit)? = null,
) {
    val p = LocalPalette.current
    val tag = when (entry.kind) {
        EntryKind.EVENT -> "EVT"
        EntryKind.TASK -> "TSK"
        EntryKind.AGENDA -> "AGD"
        EntryKind.BIRTHDAY -> "BIR"
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dp(5f)),
    ) {
        Box(
            Modifier
                .background(if (entry.kind == EntryKind.BIRTHDAY) p.paper else p.ink)
                .border(dp(1f), p.ink)
                .padding(horizontal = dp(3f)),
        ) {
            RetroText(tag, size = 8.5f, color = if (entry.kind == EntryKind.BIRTHDAY) p.ink else p.paper, maxLines = 1)
        }
        RetroText(
            entry.title,
            size = if (compact) 10.5f else 12f,
            color = if (entry.done) p.mid else p.ink,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (!compact) RetroText(CalendarMath.prettyDate(entry.date), size = 10f, color = p.mid, maxLines = 1)
        RetroText(CalendarMath.prettyTime(entry.time), size = 10f, color = p.mid, maxLines = 1)
        if (entry.kind == EntryKind.TASK) {
            RetroIconButton(label = if (entry.done) "x" else "-", boxSize = dp(18f)) { vm.calendar.toggleDone(entry.id) }
        }
        if (onOpen != null) {
            RetroIconButton(label = ">", boxSize = dp(18f), onClick = onOpen)
        }
        RetroIconButton(label = "del", boxSize = dp(26f)) { vm.calendar.remove(entry.id) }
    }
}

/** Compact inline editor used by the widget (the calendar window has a roomier one). */
@Composable
fun EntryEditor(
    vm: LauncherViewModel,
    defaultDate: LocalDate,
    onClose: () -> Unit,
    dateEditable: Boolean = true,
) {
    var title by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(defaultDate.toString()) }
    var timeText by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(EntryKind.EVENT) }

    Column(verticalArrangement = Arrangement.spacedBy(dp(4f))) {
        RetroTextFieldCompact(value = title, onValueChange = { title = it }, placeholder = "what is it?")
        if (dateEditable) {
            Row(horizontalArrangement = Arrangement.spacedBy(dp(4f))) {
                Box(Modifier.weight(1f)) {
                    RetroTextFieldCompact(value = dateText, onValueChange = { dateText = it }, placeholder = "yyyy-mm-dd")
                }
                Box(Modifier.width(dp(58f))) {
                    RetroTextFieldCompact(value = timeText, onValueChange = { timeText = it }, placeholder = "hh:mm")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dp(4f))) {
            EntryKind.values().forEach { k ->
                val label = when (k) {
                    EntryKind.EVENT -> "event"
                    EntryKind.TASK -> "task"
                    EntryKind.AGENDA -> "agenda"
                    EntryKind.BIRTHDAY -> "birthday"
                }
                RetroIconButton(
                    label = label.take(4),
                    boxSize = dp(32f),
                    active = kind == k,
                    onClick = { kind = k },
                )
            }
            Spacer(Modifier.weight(1f))
            RetroIconButton(label = "add", boxSize = dp(40f), onClick = {
                val date = if (dateEditable) CalendarMath.parseDate(dateText) ?: defaultDate else defaultDate
                val time = CalendarMath.parseTime(timeText)
                if (vm.calendar.add(title, date, kind, time) != null) {
                    title = ""
                    timeText = ""
                    onClose()
                }
            })
        }
    }
}

/** Small single line text field tuned for widget density. */
@Composable
fun RetroTextFieldCompact(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    dev.retrobit.desktop.ui.components.RetroTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        textSize = 10.5f,
        modifier = Modifier.fillMaxWidth(),
    )
}
