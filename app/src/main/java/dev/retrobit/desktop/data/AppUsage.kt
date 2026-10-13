package dev.retrobit.desktop.data

/**
 * How often an app was launched, so the app drawer can list the apps the user actually uses on top.
 *
 * Stored in the preferences as `pkg=count|pkg=count`: plain text, no database, small enough to be
 * written on every launch. Everything here is pure Kotlin so the ordering can be unit tested on the
 * JVM (the project's tests run without Robolectric).
 */
object AppUsage {

    /** Safety net so a device with hundreds of apps cannot grow the preference without bound. */
    const val MAX_ROWS = 240

    /** `pkg=count` pairs, separated by this character. Package names contain neither character. */
    const val SEP_ROW = '|'
    const val SEP_COUNT = '='

    /** Reads the stored value; malformed rows are skipped instead of throwing. */
    fun parse(raw: String): Map<String, Int> {
        if (raw.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, Int>()
        raw.split(SEP_ROW).forEach { row ->
            val eq = row.indexOf(SEP_COUNT)
            if (eq <= 0) return@forEach
            val pkg = row.substring(0, eq)
            val count = row.substring(eq + 1).toIntOrNull() ?: return@forEach
            if (count > 0) out[pkg] = (out[pkg] ?: 0) + count
        }
        return out
    }

    /** Serialises a map back to the stored form (dropping zero and negative counts). */
    fun encode(counts: Map<String, Int>): String =
        counts.entries
            .filter { it.value > 0 }
            .joinToString(SEP_ROW.toString()) { "${it.key}${SEP_COUNT}${it.value}" }

    /**
     * The stored value with one more launch counted for [pkg]. When the table is full the rows that
     * were used least are dropped — never the app that was just opened, and never a favourite.
     */
    fun bump(raw: String, pkg: String): String {
        if (pkg.isBlank()) return raw
        val counts = LinkedHashMap(parse(raw))
        counts[pkg] = (counts[pkg] ?: 0) + 1
        if (counts.size <= MAX_ROWS) return encode(counts)
        val ranked = counts.entries.sortedByDescending { it.value }
        val keep = LinkedHashMap<String, Int>()
        ranked.take(MAX_ROWS).forEach { keep[it.key] = it.value }
        if (!keep.containsKey(pkg)) {
            // drop the least used row that is not [pkg], then keep the app that was just opened
            ranked.lastOrNull { it.key != pkg }?.let { keep.remove(it.key) }
            keep[pkg] = counts[pkg] ?: 1
        }
        return encode(keep)
    }

    /**
     * The order the drawer shows: the most launched apps first, then everything else A-Z. Ties on the
     * launch count are broken by name, and equal names by package, so the list never shuffles.
     */
    fun <T> order(
        items: List<T>,
        counts: Map<String, Int>,
        pkg: (T) -> String,
        label: (T) -> String,
    ): List<T> {
        val byPackage = pkg
        val byLabel = label
        return items.sortedWith(
            compareByDescending<T> { counts[byPackage(it)] ?: 0 }
                .thenBy { byLabel(it).lowercase() }
                .thenBy { byPackage(it) },
        )
    }

    /** Apps the user launched at least once, most used first (used by the drawer footer). */
    fun used(counts: Map<String, Int>): Int = counts.count { it.value > 0 }
}
