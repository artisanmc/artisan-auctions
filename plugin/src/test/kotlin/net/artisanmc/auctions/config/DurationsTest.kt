package net.artisanmc.auctions.config

import java.time.Duration
import kotlin.test.*

class DurationsTest {
    @Test fun `units and sums`() {
        assertEquals(Duration.ofSeconds(90), parseDuration("90s"))
        assertEquals(Duration.ofMinutes(15), parseDuration("15m"))
        assertEquals(Duration.ofHours(48), parseDuration("48h"))
        assertEquals(Duration.ofDays(30), parseDuration("30d"))
        assertEquals(Duration.ofMinutes(90), parseDuration("1h 30m"))
        assertEquals(Duration.ofMinutes(90), parseDuration("1H30M"))
    }

    @Test fun `unreadable or zero is null`() {
        listOf("", "soon", "10", "5x", "-1h", "0s", "1h-").forEach { assertNull(parseDuration(it), it) }
    }

    @Test fun `format is the inverse`() {
        listOf("1d1h", "1m30s", "2d", "45m").forEach {
            assertEquals(it, formatDuration(parseDuration(it)!!))
        }
    }
}
