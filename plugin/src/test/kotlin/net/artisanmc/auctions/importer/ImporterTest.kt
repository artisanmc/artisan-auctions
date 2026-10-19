package net.artisanmc.auctions.importer

import net.artisanmc.auctions.P
import net.artisanmc.auctions.db.AuctionStore
import net.artisanmc.auctions.db.Migrations
import net.artisanmc.auctions.market.ClaimKind
import net.artisanmc.auctions.market.ClaimReason
import org.h2.jdbcx.JdbcDataSource
import java.math.BigDecimal
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.*

class ImporterTest {
    private val ds = JdbcDataSource().apply { setURL("jdbc:h2:mem:import_${System.nanoTime()};DB_CLOSE_DELAY=-1") }
    private val store = AuctionStore(ds, P).also { Migrations(ds, Logger.getAnonymousLogger(), P).run() }
    /** "STONE*3" reads as 3 stone; "BROKEN" cannot be read. */
    private val transcoder = ItemTranscoder { item ->
        if (item.raw == "BROKEN") null
        else item.raw.split('*').let { (mat, qty) -> ConvertedItem("blob-$mat", mapOf("material" to mat), "vanilla:${mat.lowercase()}", qty.toInt()) }
    }
    private val currencies = mapOf("VAULT" to "vault")
    private val importer = Importer("srv", store, transcoder, { currencies[it] }) { 5_000 }

    private val seller = UUID.randomUUID()
    private val buyer = UUID.randomUUID()

    private val data = SourceData(listOf(
        SourceListing("listing:1", seller, "Seller", "VAULT", BigDecimal("12.5"), 1_000, 9_000_000, listOf(SourceItem("STONE*3"))),
        SourceListing("listing:2", seller, "Seller", "VAULT", BigDecimal(1), 1_000, 9_000_000, listOf(SourceItem("BROKEN"))),
        SourceClaim("claim:3", buyer, ClaimReason.IMPORTED, 2_000, items = listOf(SourceItem("DIRT*1"), SourceItem("DIRT*64"))),
        SourceClaim("money:4", seller, ClaimReason.SOLD, 2_000, currency = "VAULT", amount = BigDecimal(40)),
        SourceSale("sale:5", seller, buyer, "VAULT", BigDecimal(30), 3_000, listOf(SourceItem("STONE*10"))),
        SourceSale("sale:6", seller, buyer, "VAULT", BigDecimal(30), 3_000, listOf(SourceItem("STONE*1"), SourceItem("DIRT*1"))),
    ))

    @Test fun `a dry run counts and writes nothing`() {
        val r = importer.run("zah", data, dryRun = true)
        assertEquals(1, r.listings)
        assertEquals(2, r.itemClaims)
        assertEquals(1, r.moneyClaims)
        assertEquals(1, r.sales, "the mixed lot has no unit price")
        assertEquals(listOf("listing:2"), r.unreadable)
        assertTrue(store.activeListings("srv").isEmpty())
        assertTrue(store.importedIds("zah").isEmpty())
    }

    @Test fun `an import writes once and replays as a no-op`() {
        val r = importer.run("zah", data, dryRun = false)
        assertFalse(r.dryRun)
        val l = store.activeListings("srv").single()
        assertEquals(0, BigDecimal("12.50").compareTo(l.price))
        assertEquals("vault", l.currency)
        assertEquals(3, l.quantity)
        assertEquals(listOf("blob-DIRT", "blob-DIRT"), store.unclaimed("srv", buyer).map { it.itemBlob })
        val money = store.unclaimed("srv", seller).single()
        assertEquals(ClaimKind.MONEY, money.kind)
        val sale = store.recentSales("vanilla:stone", "vault", 0, 10, setOf("sale")).single()
        assertEquals(0, BigDecimal(3).compareTo(sale.second), "30 for 10 stone")

        val again = importer.run("zah", data, dryRun = false)
        assertEquals(4, again.alreadyImported)
        assertEquals(1, store.activeListings("srv").size)
        assertEquals(2, store.unclaimed("srv", buyer).size)
    }

    @Test fun `an unknown currency blocks the real import`() {
        val odd = SourceData(listOf(SourceListing("listing:9", seller, "S", "GEMS", BigDecimal.ONE, 0, 9_000_000, listOf(SourceItem("STONE*1")))))
        val r = importer.run("zah", odd, dryRun = false)
        assertTrue(r.blocked)
        assertTrue(r.dryRun)
        assertEquals(setOf("GEMS"), r.unknownCurrencies)
        assertTrue(store.activeListings("srv").isEmpty())
    }
}
