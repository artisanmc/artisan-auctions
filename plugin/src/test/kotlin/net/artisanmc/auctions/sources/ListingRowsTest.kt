package net.artisanmc.auctions.sources

import net.artisanmc.auctions.api.ListingType
import net.artisanmc.auctions.config.AuctionsConfig
import net.artisanmc.auctions.market.Listing
import net.artisanmc.auctions.market.ListingItem
import net.artisanmc.auctions.market.ListingStatus
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.*

class ListingRowsTest {
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    private fun listing(
        price: String?, type: ListingType = ListingType.SALE, seller: UUID = alice, created: Long = 0,
        title: String? = null, material: String = "DIAMOND_SWORD", start: String? = null, bid: String? = null,
    ) = Listing(UUID.randomUUID(), "srv", type, ListingStatus.ACTIVE, seller, if (seller == alice) "Alice" else "Bob",
        "vault", price?.let(::BigDecimal), start?.let(::BigDecimal), bid?.let(::BigDecimal), if (bid != null) bob else null,
        if (bid != null) 1 else 0, created, 10_000, 0,
        listOf(ListingItem(0, "b", buildMap { put("material", material); title?.let { put("title", it) } }, "k", 1)))

    @Test fun `tabs, search and sorts`() {
        val cheap = listing("10", created = 1)
        val dear = listing("500", created = 2, title = "<gold>Excalibur")
        val auction = listing(null, ListingType.AUCTION, bob, created = 3, material = "DIRT", start = "50", bid = "60")
        val all = listOf(cheap, dear, auction)
        assertEquals(listOf(auction, dear, cheap), ListingRows.select(all, ListingRows.Query(), 0))
        assertEquals(listOf(cheap, dear), ListingRows.select(all, ListingRows.Query(tab = "sale", sort = "oldest"), 0))
        assertEquals(listOf(cheap, auction, dear), ListingRows.select(all, ListingRows.Query(sort = "price_asc"), 0))
        assertEquals(listOf(dear), ListingRows.select(all, ListingRows.Query(search = "excal"), 0))
        assertEquals(listOf(auction), ListingRows.select(all, ListingRows.Query(search = "bob"), 0))
        assertEquals(listOf(cheap, dear), ListingRows.select(all, ListingRows.Query(sort = "oldest", seller = alice), 0))
        assertEquals(emptyList(), ListingRows.select(all, ListingRows.Query(), 10_000), "ended listings are hidden")
    }

    @Test fun `a row carries formatted amounts and the viewer's flags`() {
        val c = AuctionsConfig()
        val row = ListingRows.row(listing("1234.5"), alice, c, 0)
        assertEquals("1,234.50 $", row["price_display"])
        assertEquals(true, row["own"])
        assertEquals("Diamond Sword", row["name"])
        assertEquals(mapOf("material" to "DIAMOND_SWORD", "count" to 1), row["icon"])
        val auction = ListingRows.row(listing(null, ListingType.AUCTION, start = "100", bid = "100"), bob, c, 0)
        assertEquals("105 $", auction["min_bid_display"], "5 % raise")
        assertEquals(true, auction["leading"])
        assertEquals("", auction["buy_now_display"])
    }

    @Test fun `params with blanks fall back to defaults`() {
        assertEquals(ListingRows.Query(), ListingRows.Query.of(mapOf("tab" to "", "sort" to null, "search" to "  ")))
    }
}
