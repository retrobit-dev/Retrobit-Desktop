package dev.retrobit.desktop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The drawer order: most used first, then A-Z — and never a lost or corrupt count. */
class AppUsageTest {

    private data class App(val pkg: String, val label: String)

    private fun order(apps: List<App>, counts: Map<String, Int>): List<String> =
        AppUsage.order(apps, counts, { it.pkg }, { it.label }).map { it.pkg }

    private val apps = listOf(
        App("com.zeta", "Zeta"),
        App("com.alpha", "Alpha"),
        App("com.bravo", "bravo"),
        App("com.charlie", "Charlie"),
    )

    @Test
    fun `an empty preference parses to no counts`() {
        assertEquals(emptyMap<String, Int>(), AppUsage.parse(""))
    }

    @Test
    fun `counts survive a round trip`() {
        val raw = AppUsage.encode(mapOf("com.alpha" to 3, "com.bravo" to 1))
        assertEquals(mapOf("com.alpha" to 3, "com.bravo" to 1), AppUsage.parse(raw))
    }

    @Test
    fun `broken rows are skipped instead of throwing`() {
        val counts = AppUsage.parse("com.alpha=2|=5|com.bravo=x|com.charlie=|nonsense|com.zeta=0")
        assertEquals(mapOf("com.alpha" to 2), counts)
    }

    @Test
    fun `bump counts one more launch`() {
        val once = AppUsage.bump("", "com.alpha")
        assertEquals(mapOf("com.alpha" to 1), AppUsage.parse(once))
        val twice = AppUsage.bump(once, "com.alpha")
        assertEquals(mapOf("com.alpha" to 2), AppUsage.parse(twice))
    }

    @Test
    fun `bump ignores a blank package`() {
        assertEquals("com.alpha=1", AppUsage.bump("com.alpha=1", ""))
    }

    @Test
    fun `the most used apps come first, the rest alphabetically`() {
        val counts = mapOf("com.zeta" to 9, "com.bravo" to 4)
        // Zeta (9) then bravo (4), then Alpha and Charlie (0) A-Z: bravo was already used
        assertEquals(listOf("com.zeta", "com.bravo", "com.alpha", "com.charlie"), order(apps, counts))
    }

    @Test
    fun `alphabetical order ignores letter case`() {
        assertEquals(listOf("com.alpha", "com.bravo", "com.charlie", "com.zeta"), order(apps, emptyMap()))
    }

    @Test
    fun `equal counts are ordered by name and then by package`() {
        val twins = listOf(App("b.pkg", "Same"), App("a.pkg", "Same"), App("c.pkg", "another"))
        val counts = mapOf("b.pkg" to 2, "a.pkg" to 2, "c.pkg" to 2)
        assertEquals(listOf("c.pkg", "a.pkg", "b.pkg"), order(twins, counts))
    }

    @Test
    fun `a full table keeps the most used rows`() {
        val raw = (1..AppUsage.MAX_ROWS).joinToString("|") { "com.app$it=$it" }
        val bumped = AppUsage.bump(raw, "com.fresh")
        val counts = AppUsage.parse(bumped)
        assertEquals(AppUsage.MAX_ROWS, counts.size)
        // the app that was just opened is kept, the least used single-use row is dropped instead
        assertEquals(1, counts["com.fresh"] ?: -1)
        assertTrue(counts.containsKey("com.app${AppUsage.MAX_ROWS}"))
        assertTrue(counts.containsKey("com.app2"))
        assertTrue(!counts.containsKey("com.app1"))
    }

    @Test
    fun `used counts how many apps were opened at least once`() {
        assertEquals(2, AppUsage.used(mapOf("a" to 1, "b" to 7, "c" to 0)))
    }
}
