package net.artisanmc.auctions.market

import net.artisanmc.auctions.config.AuctionsConfig
import java.math.BigDecimal
import kotlin.test.*

class SellRulesTest {
    @Test fun `blacklist by material and by lore`() {
        val items = AuctionsConfig.Items(blacklist = listOf("BEDROCK"), blacklistLoreContains = listOf("Soulbound"))
        assertTrue(SellRules.forbidden("bedrock", emptyList(), items))
        assertTrue(SellRules.forbidden("DIAMOND", listOf("A SOULBOUND blade"), items))
        assertFalse(SellRules.forbidden("DIAMOND", listOf("Sharp"), items))
    }

    @Test fun `the highest limit permission wins`() {
        assertEquals(10, SellRules.limit(listOf("auctions.use"), 10))
        assertEquals(25, SellRules.limit(listOf("auctions.limit.5", "auctions.limit.25"), 10))
        assertEquals(3, SellRules.limit(listOf("auctions.limit.3"), 10), "a lower grant still applies")
        assertEquals(Int.MAX_VALUE, SellRules.limit(listOf("auctions.limit.unlimited"), 10))
    }

    @Test fun `amounts are written per currency`() {
        val c = AuctionsConfig.Currencies(listOf("vault", "tokens"), "vault", mapOf("vault" to "\$" + "{amount}"))
        assertEquals("$1,000", c.format("vault", BigDecimal("1000")))
        assertEquals("0.50 tokens", c.format("tokens", BigDecimal("0.5")))
    }
}
