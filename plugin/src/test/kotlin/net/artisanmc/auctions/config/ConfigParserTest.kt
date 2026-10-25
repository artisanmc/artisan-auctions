package net.artisanmc.auctions.config

import org.yaml.snakeyaml.Yaml
import java.math.BigDecimal
import java.time.Duration
import kotlin.test.*

class ConfigParserTest {
    private fun parse(yaml: String) = ConfigParser.parse(Yaml().load<Map<String, Any?>>(yaml) ?: emptyMap<String, Any?>())

    @Test fun `the shipped file reads without errors and equals the defaults`() {
        val text = javaClass.getResource("/module/data/config.yaml")!!.readText()
        val result = parse(text)
        assertEquals(emptyList(), result.errors)
        // Regex has identity equality: compare the rest.
        assertEquals(AuctionsConfig().copy(median = AuctionsConfig.Median()), result.config.copy(median = AuctionsConfig.Median()))
        assertEquals(AuctionsConfig.Median().copy(loreIgnore = emptyList()), result.config.median.copy(loreIgnore = emptyList()))
    }

    @Test fun `an empty file is the defaults`() {
        val result = parse("")
        assertEquals(emptyList(), result.errors)
        assertTrue(result.config.sales.enabled && result.config.auctions.enabled)
    }

    @Test fun `values are read`() {
        val c = parse("""
            sales: { enabled: false, duration: 2d, min_price: 10.5 }
            auctions: { durations: [6h, 1h30m], default_duration: 6h, anti_snipe: { window: 2m } }
            limits: { sale_tax: { percent: 2.5 } }
            currencies: { allowed: [vault, tokens], default: tokens }
            median: { lore_ignore: ["^Kills"], include: [sale] }
        """.trimIndent()).also { assertEquals(emptyList(), it.errors) }.config
        assertFalse(c.sales.enabled)
        assertEquals(Duration.ofDays(2), c.sales.duration)
        assertEquals(BigDecimal("10.5"), c.sales.minPrice)
        assertEquals(listOf(Duration.ofMinutes(90), Duration.ofHours(6)), c.auctions.durations)
        assertEquals(Duration.ofMinutes(2), c.auctions.antiSnipe.window)
        assertEquals(BigDecimal("2.5"), c.limits.saleTax.percent)
        assertEquals("tokens", c.currencies.default)
        assertTrue(c.median.loreIgnore.single().containsMatchIn("Kills : 42"))
        assertEquals(setOf("sale"), c.median.include)
    }

    @Test fun `each mistake is reported`() {
        val errors = parse("""
            sales: { duration: soon, min_price: -1, enabled: maybe }
            auctions: { durations: [1h], default_duration: 24h }
            limits: { max_active_listings: 2.5 }
            currencies: { allowed: [vault], default: gems }
            median: { key: smart, lore_ignore: ["("], include: [trade] }
        """.trimIndent()).errors
        val expected = listOf("sales.duration", "sales.min_price", "sales.enabled", "auctions.default_duration",
            "limits.max_active_listings", "currencies.default", "median.key", "median.lore_ignore", "median.include")
        for (key in expected) assertTrue(errors.any { it.startsWith(key) }, "no error for $key in $errors")
    }

    @Test fun `both modes off is a read-only house, not an error`() {
        val result = parse("sales: { enabled: false }\nauctions: { enabled: false }")
        assertEquals(emptyList(), result.errors)
        assertTrue(result.config.readOnly)
    }

    @Test fun `min above max is refused`() {
        assertTrue(parse("sales: { min_price: 10, max_price: 5 }").errors.isNotEmpty())
    }
}
