package net.artisanmc.auctions.market

import net.artisanmc.auctions.api.ListingType
import net.artisanmc.auctions.config.AuctionsConfig
import net.artisanmc.auctions.db.AuctionStore
import net.artisanmc.auctions.db.Migrations
import org.h2.jdbcx.JdbcDataSource
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.*

class AuctionTest {
    private val ds = JdbcDataSource().apply { setURL("jdbc:h2:mem:auction_${System.nanoTime()};DB_CLOSE_DELAY=-1") }
    private val logger = Logger.getAnonymousLogger()
    private val store = AuctionStore(ds).also { Migrations(ds, logger).run() }
    private val threads = QueueThreads()
    private val wallets = FakeWallets("vault")
    private val giver = FakeGiver()
    private var config = AuctionsConfig()
    private var now = 1_000_000L
    private val events = mutableListOf<String>()
    private val hooks = object : Hooks {
        override fun outbid(listing: Listing, previous: UUID, amount: BigDecimal) { events += "outbid:${amount.stripTrailingZeros().toPlainString()}" }
        override fun won(listing: Listing, winner: UUID, price: BigDecimal) { events += "won" }
    }
    private val cache = ListingCache()
    private val market = Market("srv", store, cache, threads, wallets,
        ClaimDelivery(store, threads, wallets, giver, { hooks }, logger) { now }, { config }, { hooks }, logger) { now }

    private val seller = UUID.randomUUID()
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    private fun auction(start: String = "100", buyout: String? = null, hours: Long = 24): Listing {
        val r = ListRequest(seller, "Seller", ListingType.AUCTION, listOf(ListingItem(0, "blob", mapOf("material" to "STONE"), "k", 1)),
            "vault", buyout?.let(::BigDecimal), BigDecimal(start), Duration.ofHours(hours), 10)
        assertNull(market.check(r))
        var out: Market.Outcome? = null
        market.create(r, { fail() }) { out = it }
        threads.drain()
        return (out as Market.Listed).listing
    }

    private fun bid(who: UUID, l: Listing, amount: String): Market.Outcome {
        var out: Market.Outcome? = null
        market.bid(who, l.id, BigDecimal(amount)) { out = it }
        threads.drain()
        return out!!
    }

    private fun money(p: UUID) = wallets.of(p)

    @Test fun `a bid is held and the outbid bidder is paid back`() {
        val l = auction()
        wallets.balances[alice to "vault"] = BigDecimal(500)
        wallets.balances[bob to "vault"] = BigDecimal(500)
        assertEquals("bid_too_low", bid(alice, l, "99").key)
        assertIs<Market.BidPlaced>(bid(alice, l, "100"))
        assertEquals("400", money(alice))
        assertEquals("already_leading", bid(alice, l, "200").key)
        assertEquals("bid_too_low", bid(bob, l, "104").key, "5 % raise")
        assertIs<Market.BidPlaced>(bid(bob, l, "105"))
        assertEquals("500", money(alice), "refunded at once")
        assertEquals("395", money(bob))
        assertEquals(listOf("outbid:100"), events)
        val row = store.listing(l.id)!!
        assertEquals(bob, row.currentBidder)
        assertEquals(2, row.bidCount)
        assertEquals(listOf("held", "refunded"), store.bids(l.id).map { it.third })
    }

    @Test fun `a late bid extends the auction`() {
        val l = auction(hours = 1)
        wallets.balances[alice to "vault"] = BigDecimal(500)
        now = l.endsAt - 10_000
        val out = bid(alice, l, "100") as Market.BidPlaced
        assertTrue(out.extended)
        assertEquals(now + 60_000, cache.get(l.id)!!.endsAt)
    }

    @Test fun `the leader wins at the end and the seller is paid the net`() {
        val l = auction()
        wallets.balances[alice to "vault"] = BigDecimal(500)
        giver.online += alice
        bid(alice, l, "200")
        now = l.endsAt + 1
        market.expireDue()
        threads.drain()
        assertEquals(ListingStatus.SOLD, store.listing(l.id)!!.status)
        assertEquals("300", money(alice))
        assertEquals("190", money(seller), "5 % tax")
        assertEquals(listOf("blob"), giver.given[alice]?.toList())
        assertEquals(listOf("captured"), store.bids(l.id).map { it.third })
        assertTrue("won" in events)
        assertEquals(0, BigDecimal(200).compareTo(store.recentSales("k", "vault", 0, 10, setOf("auction")).single().second))
    }

    @Test fun `an auction without bids expires to the seller`() {
        val l = auction()
        now = l.endsAt + 1
        market.expireDue()
        threads.drain()
        assertEquals(ListingStatus.EXPIRED, store.listing(l.id)!!.status)
        assertEquals(listOf("blob"), store.unclaimed("srv", seller).map { it.itemBlob })
    }

    @Test fun `cancelling an auction refunds the leader`() {
        val l = auction()
        wallets.balances[alice to "vault"] = BigDecimal(500)
        bid(alice, l, "150")
        market.cancel(seller, l.id, admin = false) {}
        threads.drain()
        assertEquals("500", money(alice))
        config = config.copy(limits = config.limits.copy(cancelAuctionWithBids = false))
        val other = auction()
        bid(alice, other, "100")
        var out: Market.Outcome? = null
        market.cancel(seller, other.id, admin = false) { out = it }
        assertEquals("has_bids", out?.key)
    }

    @Test fun `a bid reaching the buy-now price buys`() {
        config = config.copy(auctions = config.auctions.copy(buyout = true))
        val l = auction(buyout = "300")
        wallets.balances[alice to "vault"] = BigDecimal(500)
        wallets.balances[bob to "vault"] = BigDecimal(500)
        bid(bob, l, "100")
        assertIs<Market.Bought>(bid(alice, l, "300"))
        assertEquals("200", money(alice))
        assertEquals("500", money(bob), "the leader is refunded")
        assertEquals("285", money(seller))
    }

    @Test fun `a crash after the bid charge is refunded at start`() {
        val l = auction()
        wallets.balances[alice to "vault"] = BigDecimal(500)
        market.bid(alice, l.id, BigDecimal(100)) {}
        threads.step(); threads.step(); threads.clear()
        market.start()
        threads.drain()
        assertEquals("400", money(alice))
        assertEquals(0, BigDecimal(100).compareTo(store.unclaimed("srv", alice).single().amount))
    }
}
