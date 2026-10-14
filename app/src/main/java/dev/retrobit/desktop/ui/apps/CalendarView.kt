package dev.retrobit.desktop.ui.apps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.data.CalendarMath
import dev.retrobit.desktop.model.EntryKind
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.RetroButton
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.desktop.EntryEditor
import dev.retrobit.desktop.ui.desktop.EntryRow
import dev.retrobit.desktop.ui.theme.LocalPalette
import java.time.LocalDate
import java.time.YearMonth

/** Full calendar window: big month view, agenda, tasks and birthdays, plus an entry editor. */
@Composable
fun CalendarView(vm: LauncherViewModel) {
    val p = LocalPalette.current
    val store = vm.calendar
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    var editing by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf(0) } // 0 = month, 1 = agenda, 2 = tasks

    val marked = remember(store.entries.toList(), month) { CalendarMath.daysWithEntries(store.entries, month) }
    val dayEntries = remember(store.entries.toList(), selected) { store.on(selected) }
    val upcoming = remember(store.entries.toList(), today) { store.upcoming(today, 14) }
    val tasks = remember(store.entries.toList()) { store.openTasks() }
    val birthdays = remember(store.entries.toList()) { store.entries.filter { it.kind == EntryKind.BIRTHDAY } }

    Column(Modifier.fillMaxSize().background(p.paper)) {
        // toolbar
        Row(
            Modifier.fillMaxWidth().background(p.soft).padding(dp(8f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(6f)),
        ) {
            GlyphIcon(Glyph.CALENDAR, dp(15f))
            RetroText(CalendarMath.prettyMonth(month), size = 13f, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            RetroButton(label = "<", modifier = Modifier.width(dp(56f))) { month = month.minusMonths(1) }
            RetroButton(label = "today", modifier = Modifier.width(dp(92f)), active = YearMonth.from(today) == month) {
                month = YearMonth.from(today)
                selected = today
            }
            RetroButton(label = ">", modifier = Modifier.width(dp(56f))) { month = month.plusMonths(1) }
            RetroButton(label = "+ add", modifier = Modifier.width(dp(96f)), active = editing) { editing = !editing }
        }
        Box(Modifier.fillMaxWidth().height(dp(1.4f)).background(p.edge))

        if (editing) {
            Column(Modifier.fillMaxWidth().background(p.soft).padding(dp(10f)), verticalArrangement = Arrangement.spacedBy(dp(6f))) {
                RetroText("new entry", size = 11f, weight = FontWeight.Bold)
                EntryEditor(vm, defaultDate = selected, onClose = { editing = false })
            }
            Box(Modifier.fillMaxWidth().height(dp(1.4f)).background(p.edge))
        }

        Row(Modifier.weight(1f).fillMaxWidth()) {
            // month grid
            Column(Modifier.weight(1.4f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(dp(8f))) {
                Row(Modifier.fillMaxWidth()) {
                    CalendarMath.weekdayHeaders().forEach { label ->
                        RetroText(label, size = 10f, color = p.mid, modifier = Modifier.weight(1f), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(dp(4f)))
                CalendarMath.monthGrid(month).forEach { week ->
                    Row(Modifier.fillMaxWidth().height(dp(54f)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEach { day ->
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                if (day != null) BigDayCell(
                                    day = day,
                                    isToday = day == today,
                                    isSelected = day == selected,
                                    hasEntries = day in marked,
                                    count = store.on(day).size,
                                ) { selected = day }
                            }
                        }
                    }
                    Spacer(Modifier.height(dp(3f)))
                }
            }
            Box(Modifier.width(dp(1.4f)).fillMaxHeight().background(p.edge))
            // side panel
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(dp(8f)), verticalArrangement = Arrangement.spacedBy(dp(7f))) {
                Row(horizontalArrangement = Arrangement.spacedBy(dp(4f))) {
                    listOf("day", "agenda", "tasks").forEachIndexed { i, label ->
                        RetroButton(label = label, active = view == i, modifier = Modifier.weight(1f)) { view = i }
                    }
                }
                when (view) {
                    0 -> {
                        RetroText(CalendarMath.prettyDate(selected), size = 11f, weight = FontWeight.Bold)
                        if (dayEntries.isEmpty()) RetroText("nothing planned", size = 10.5f, color = p.mid)
                        dayEntries.forEach { entry -> EntryRow(vm, entry, compact = false) }
                        if (tasks.isNotEmpty()) {
                            Box(Modifier.fillMaxWidth().height(dp(1f)).background(p.ink.copy(alpha = 0.3f)))
                            RetroText("open tasks", size = 11f, weight = FontWeight.Bold)
                            tasks.take(8).forEach { task -> EntryRow(vm, task, compact = true) }
                        }
                    }
                    1 -> {
                        RetroText("next 14 days", size = 11f, weight = FontWeight.Bold)
                        if (upcoming.isEmpty()) RetroText("calendar is clear", size = 10.5f, color = p.mid)
                        upcoming.forEach { (day, entry) ->
                            Column {
                                RetroText(CalendarMath.prettyDate(day), size = 9.5f, color = p.mid, maxLines = 1)
                                EntryRow(vm, entry, compact = true)
                            }
                        }
                    }
                    else -> {
                        RetroText("open tasks (${tasks.size})", size = 11f, weight = FontWeight.Bold)
                        if (tasks.isEmpty()) RetroText("all done", size = 10.5f, color = p.mid)
                        tasks.forEach { task -> EntryRow(vm, task, compact = true) }
                        Box(Modifier.fillMaxWidth().height(dp(1f)).background(p.ink.copy(alpha = 0.3f)))
                        RetroText("birthdays (${birthdays.size})", size = 11f, weight = FontWeight.Bold)
                        birthdays.sortedBy { it.date.monthValue * 100 + it.date.dayOfMonth }.forEach { b ->
                            EntryRow(vm, b, compact = true)
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(dp(1.4f)).background(p.edge))
        Row(
            Modifier.fillMaxWidth().background(p.soft).padding(horizontal = dp(8f), vertical = dp(5f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(8f)),
        ) {
            RetroText("${store.entries.size} entries · ${tasks.size} open tasks · tap a day to see it", size = 10f, color = p.mid, maxLines = 1)
        }
    }
}

@Composable
private fun BigDayCell(
    day: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    hasEntries: Boolean,
    count: Int,
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
            .fillMaxSize()
            .background(bg)
            .border(dp(if (isSelected) 1.8f else 1f), p.ink.copy(alpha = if (isSelected) 1f else 0.35f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            )
            .padding(dp(4f)),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        RetroText("${day.dayOfMonth}", size = 11.5f, color = fg, weight = if (isToday) FontWeight.Bold else FontWeight.Normal)
        if (hasEntries) {
            Canvas(Modifier.fillMaxWidth().height(dp(10f))) {
                val dot = 4f
                repeat(count.coerceAtMost(5)) { i ->
                    drawRect(fg, topLeft = Offset(i * (dot + 3f), 0f), size = Size(dot, dot))
                }
            }
        }
    }
}
