package net.artisanmc.auctions.median

import net.artisanmc.auctions.api.ListingType
import net.artisanmc.auctions.config.AuctionsConfig
import net.artisanmc.auctions.db.AuctionStore
import net.artisanmc.auctions.db.Migrations
import net.artisanmc.auctions.market.QueueThreads
import net.artisanmc.auctions.market.Sale
import org.h2.jdbcx.JdbcDataSource
import java.math.BigDecimal
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.*

class PriceKeysTest {
    private val m = AuctionsConfig.Median(loreIgnore = listOf(Regex("^Durability"), Regex("^Kills:")))

    @Test fun `an item plugin id wins over everything`() {
        val f = ItemFacts("DIAMOND_SWORD", pdc = mapOf("nexo:id" to "excalibur"), itemName = "Excalibur", lore = listOf("Legendary"))
        assertEquals("custom:nexo:id=excalibur", PriceKeys.of(f, m))
        assertEquals("custom:itemsadder:x:y", PriceKeys.of(ItemFacts("STONE", pluginIds = mapOf("itemsadder" to "x:y")), m))
        val declared = m.copy(customIdKeys = listOf("myserver:legendary_id"))
        assertEquals("custom:myserver:legendary_id=7", PriceKeys.of(ItemFacts("STONE", pdc = mapOf("myserver:legendary_id" to "7")), declared))
    }

    @Test fun `name and lore identify a hand-made item, ignoring per-copy lines`() {
        val a = ItemFacts("DIAMOND_SWORD", itemName = "Excalibur", lore = listOf("Fire +3", "", "Durability: 812/1561", "Kills: 42"))
        val b = a.copy(lore = listOf("Fire +3", "Durability: 10/1561"))
        val stronger = a.copy(lore = listOf("Fire +4"))
        assertEquals(PriceKeys.of(a, m), PriceKeys.of(b, m))
        assertNotEquals(PriceKeys.of(a, m), PriceKeys.of(stronger, m))
        assertTrue(PriceKeys.of(a, m).startsWith("custom:diamond_sword#name=Excalibur#lore="))
    }

    @Test fun `an anvil name without lore stays vanilla`() {
        val renamedDirt = ItemFacts("DIRT", customName = "Excalibur")
        assertEquals("vanilla:dirt", PriceKeys.of(renamedDirt, m))
        val withLore = renamedDirt.copy(lore = listOf("Legendary"))
        assertTrue(PriceKeys.of(withLore, m).startsWith("custom:dirt#name=Excalibur"))
    }

    @Test fun `enchantments split vanilla items but not custom ones`() {
        val sword = ItemFacts("DIAMOND_SWORD", enchants = mapOf("sharpness" to 5, "unbreaking" to 3))
        assertEquals("vanilla:diamond_sword#sharpness=5,unbreaking=3", PriceKeys.of(sword, m))
        val custom = sword.copy(itemName = "Excalibur")
        assertEquals(PriceKeys.of(custom, m), PriceKeys.of(custom.copy(enchants = emptyMap()), m))
        assertEquals("vanilla:potion#potion=swiftness", PriceKeys.of(ItemFacts("POTION", potion = "SWIFTNESS"), m))
    }

    @Test fun `attributes enter the key only when asked`() {
        val f = ItemFacts("STICK", itemName = "Wand", pdc = mapOf("magic:power" to "3"))
        assertFalse(PriceKeys.of(f, m).contains("#attr="))
        val values = m.copy(attributes = "values")
        assertNotEquals(PriceKeys.of(f, values), PriceKeys.of(f.copy(pdc = mapOf("magic:power" to "4")), values))
        val keys = m.copy(attributes = "keys")
        assertEquals(PriceKeys.of(f, keys), PriceKeys.of(f.copy(pdc = mapOf("magic:power" to "4")), keys))
    }

    @Test fun `vanilla mode ignores custom signals and keys fit the column`() {
        val f = ItemFacts("STONE", pdc = mapOf("nexo:id" to "x"), itemName = "N".repeat(400))
        assertEquals("vanilla:stone", PriceKeys.of(f, m.copy(key = "vanilla")))
        assertTrue(PriceKeys.of(ItemFacts("STONE", pdc = mapOf("nexo:id" to "y".repeat(400))), m).length <= 255)
    }
}

class MedianMathTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private val c = UUID.randomUUID()

    private fun sales(vararg s: Pair<UUID, Int>) = s.map { it.first to BigDecimal(it.second) }

    @Test fun `odd, even and too few`() {
        assertEquals(BigDecimal("20.00"), MedianMath.of(sales(a to 10, b to 20, c to 1000), 3, 3, 14).price)
        assertEquals(BigDecimal("15.00"), MedianMath.of(sales(a to 10, b to 20), 3, 2, 14).price)
        val few = MedianMath.of(sales(a to 10), 3, 2, 14)
        assertNull(few.price)
        assertEquals(1, few.sales)
    }

    @Test fun `one seller cannot flood the window`() {
        val flood = sales(a to 1000, a to 1000, a to 1000, a to 1000, a to 1000, b to 10, c to 12)
        val m = MedianMath.of(flood, 2, 3, 14)
        assertEquals(4, m.sales)
        assertEquals(BigDecimal("506.00"), m.price, "only the two newest of a count")
    }

    @Test fun `ratio`() {
        assertEquals(-20, MedianMath.ratio(BigDecimal(80), BigDecimal(100)))
        assertEquals(50, MedianMath.ratio(BigDecimal(150), BigDecimal(100)))
    }
}

class MedianCacheTest {
    private val ds = JdbcDataSource().apply { setURL("jdbc:h2:mem:median_${System.nanoTime()};DB_CLOSE_DELAY=-1") }
    private val store = AuctionStore(ds).also { Migrations(ds, Logger.getAnonymousLogger()).run() }
    private val threads = QueueThreads()
    private var now = 100L * MedianCache.DAY_MS
    private var loads = 0
    private val cache = MedianCache(store, threads, { AuctionsConfig() }, Logger.getAnonymousLogger(), { now }) { loads++ }

    private fun sale(key: String, unit: Int, daysAgo: Int, currency: String = "vault") = Sale(
        UUID.randomUUID(), UUID.randomUUID(), "srv", ListingType.SALE, key, currency, 1, BigDecimal(unit), BigDecimal(unit),
        UUID.randomUUID(), UUID.randomUUID(), now - daysAgo * MedianCache.DAY_MS)

    @Test fun `computed in the background, per currency, within the window`() {
        store.insertSales((1..5).map { sale("vanilla:stone", it * 10, 1) } + sale("vanilla:stone", 9999, 30) + sale("vanilla:stone", 1, 1, "tokens"))
        assertNull(cache.get("vanilla:stone", "vault"))
        threads.drain()
        assertEquals(1, loads)
        val m = cache.get("vanilla:stone", "vault")!!
        assertEquals(BigDecimal("30.00"), m.price, "the 30-day-old sale is outside the 14-day window")
        assertEquals(5, m.sales)
        cache.get("vanilla:stone", "tokens"); threads.drain()
        assertNull(cache.get("vanilla:stone", "tokens")!!.price, "one sale is not enough")
    }

    @Test fun `custom keys use their own longer window and lower threshold`() {
        store.insertSales(listOf(sale("custom:x", 100, 60), sale("custom:x", 200, 70), sale("custom:x", 300, 80)))
        cache.get("custom:x", "vault"); threads.drain()
        assertEquals(BigDecimal("200.00"), cache.get("custom:x", "vault")!!.price)
    }

    @Test fun `a sale invalidates the entry`() {
        store.insertSales((1..5).map { sale("vanilla:dirt", 10, 1) })
        cache.get("vanilla:dirt", "vault"); threads.drain()
        store.insertSales((1..6).map { sale("vanilla:dirt", 50, 1) })
        assertEquals(BigDecimal("10.00"), cache.get("vanilla:dirt", "vault")!!.price)
        cache.invalidate("vanilla:dirt", "vault")
        cache.get("vanilla:dirt", "vault"); threads.drain()
        assertEquals(BigDecimal("50.00"), cache.get("vanilla:dirt", "vault")!!.price)
    }
}
