package net.artisanmc.auctions.market

import net.artisanmc.auctions.P
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

/** Runs queued work only when told, so tests choose the interleaving. */
class QueueThreads : Threads {
    private val queue = ArrayDeque<() -> Unit>()
    override fun async(task: () -> Unit) { queue.addLast(task) }
    override fun main(task: () -> Unit) { queue.addLast(task) }
    fun drain() { while (queue.isNotEmpty()) queue.removeFirst()() }
    fun step() { queue.removeFirstOrNull()?.invoke() }
    fun clear() { queue.clear() }
    val pending get() = queue.size
}

class FakeWallets(vararg currencies: String) : Wallets {
    private val known = currencies.toSet()
    val balances = HashMap<Pair<UUID, String>, BigDecimal>()
    var refuseDeposits = false
    override fun has(currency: String) = currency in known
    override fun balance(player: UUID, currency: String) = balances[player to currency] ?: BigDecimal.ZERO
    override fun withdraw(player: UUID, currency: String, amount: BigDecimal): Boolean {
        val b = balance(player, currency)
        if (b < amount) return false
        balances[player to currency] = b - amount
        return true
    }
    override fun deposit(player: UUID, currency: String, amount: BigDecimal): Boolean {
        if (refuseDeposits) return false
        balances[player to currency] = balance(player, currency) + amount
        return true
    }
    fun of(p: UUID, c: String = "vault") = balance(p, c).stripTrailingZeros().toPlainString()
}

class FakeGiver : ItemGiver {
    val online = HashSet<UUID>()
    val given = HashMap<UUID, MutableList<String>>()
    var full = false
    override fun isOnline(player: UUID) = player in online
    override fun giveAll(player: UUID, blobs: List<String>): Boolean {
        if (player !in online || full) return false
        given.getOrPut(player) { mutableListOf() } += blobs
        return true
    }
}

class MarketTest {
    private val ds = JdbcDataSource().apply { setURL("jdbc:h2:mem:market_${System.nanoTime()};DB_CLOSE_DELAY=-1") }
    private val logger = Logger.getAnonymousLogger()
    private val store = AuctionStore(ds, P).also { Migrations(ds, logger, P).run() }
    private val threads = QueueThreads()
    private val wallets = FakeWallets("vault", "tokens")
    private val giver = FakeGiver()
    private var config = AuctionsConfig()
    private var now = 1_000_000L
    private var hooks: Hooks = Hooks.None

    private fun market(cache: ListingCache = ListingCache()): Market {
        val delivery = ClaimDelivery(store, threads, wallets, giver, { hooks }, logger) { now }
        return Market("srv", store, cache, threads, wallets, delivery, { config }, { hooks }, logger) { now }
    }

    private val seller = UUID.randomUUID()
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    private fun item(key: String = "STONE", qty: Int = 1) = ListingItem(0, "blob-$key", mapOf("material" to key), key, qty)

    private fun request(price: String = "100", items: List<ListingItem> = listOf(item())) =
        ListRequest(seller, "Seller", ListingType.SALE, items, "vault", BigDecimal(price), null, Duration.ofHours(48), 10)

    private fun list(m: Market, r: ListRequest = request()): Listing {
        assertNull(m.check(r))
        var out: Market.Outcome? = null
        m.create(r, itemsBack = { fail("items returned") }) { out = it }
        threads.drain()
        return (out as Market.Listed).listing
    }

    private fun money(p: UUID, c: String = "vault") = wallets.of(p, c)

    @Test fun `a sale pays the seller minus tax and gives the buyer the item`() {
        val m = market()
        val l = list(m)
        wallets.balances[alice to "vault"] = BigDecimal(150)
        giver.online += alice
        var out: Market.Outcome? = null
        m.buy(alice, l.id) { out = it }
        threads.drain()
        assertIs<Market.Bought>(out)
        assertEquals("50", money(alice))
        assertEquals("95", money(seller), "5 % sale tax")
        assertEquals(listOf("blob-STONE"), giver.given[alice]?.toList())
        assertTrue(store.unclaimed("srv", alice).isEmpty())
        assertEquals(ListingStatus.SOLD, store.listing(l.id)!!.status)
        assertTrue(store.pendingPayments("srv").isEmpty())
    }

    @Test fun `an offline buyer finds the item in the claims box`() {
        val m = market()
        val l = list(m)
        wallets.balances[alice to "vault"] = BigDecimal(100)
        m.buy(alice, l.id) {}
        threads.drain()
        assertEquals(listOf("blob-STONE"), store.unclaimed("srv", alice).map { it.itemBlob })
    }

    @Test fun `two servers on one database sell a listing once`() {
        val first = market()
        val l = list(first)
        val second = market(ListingCache().apply { put(l) })
        wallets.balances[alice to "vault"] = BigDecimal(100)
        wallets.balances[bob to "vault"] = BigDecimal(100)
        val outcomes = mutableListOf<Market.Outcome>()
        first.buy(alice, l.id) { outcomes += it }
        second.buy(bob, l.id) { outcomes += it }
        threads.drain()
        assertEquals(1, outcomes.count { it is Market.Bought })
        assertEquals(1, outcomes.count { it is Market.Problem && it.key == "gone" })
        // Whoever lost got the money back, through the box then delivered.
        val loser = if (outcomes.first() is Market.Bought) bob else alice
        assertEquals("100", money(loser))
        assertEquals("95", money(seller))
        assertEquals(1, store.unclaimed("srv", alice).size + store.unclaimed("srv", bob).size, "the winner's item")
    }

    @Test fun `a double click charges once`() {
        val m = market()
        val l = list(m)
        wallets.balances[alice to "vault"] = BigDecimal(1000)
        val outcomes = mutableListOf<Market.Outcome>()
        m.buy(alice, l.id) { outcomes += it }
        m.buy(alice, l.id) { outcomes += it }
        threads.drain()
        assertEquals(listOf("busy", "bought"), outcomes.map { it.key })
        assertEquals("900", money(alice))
    }

    @Test fun `the seller cannot buy their own listing and a poor buyer is refused before anything moves`() {
        val m = market()
        val l = list(m)
        var out: Market.Outcome? = null
        m.buy(seller, l.id) { out = it }
        assertEquals("own_listing", out?.key)
        wallets.balances[alice to "vault"] = BigDecimal(99)
        m.buy(alice, l.id) { out = it }
        assertEquals("insufficient_funds", out?.key)
        assertEquals(0, threads.pending)
    }

    @Test fun `a vetoed purchase charges nothing`() {
        val m = market()
        val l = list(m)
        hooks = object : Hooks { override fun prePurchase(buyer: UUID, listing: Listing, price: BigDecimal) = Veto("limit") }
        wallets.balances[alice to "vault"] = BigDecimal(100)
        var out: Market.Outcome? = null
        m.buy(alice, l.id) { out = it }
        threads.drain()
        assertIs<Market.Vetoed>(out)
        assertEquals("100", money(alice))
    }

    @Test fun `a crash after the charge is refunded at the next start`() {
        val m = market()
        val l = list(m)
        wallets.balances[alice to "vault"] = BigDecimal(100)
        m.buy(alice, l.id) {}
        threads.step() // journal written
        threads.step() // money taken
        threads.clear() // crash: the settlement never runs
        market().start()
        threads.drain()
        assertEquals("0", money(alice))
        val refund = store.unclaimed("srv", alice).single()
        assertEquals(ClaimKind.MONEY, refund.kind)
        assertEquals(0, BigDecimal(100).compareTo(refund.amount))
        assertEquals(ListingStatus.ACTIVE, store.listing(l.id)!!.status)
    }

    @Test fun `listing fee is taken and refused when unpaid`() {
        config = config.copy(limits = config.limits.copy(listingFee = AuctionsConfig.Fee(BigDecimal(10), BigDecimal(1))))
        val m = market()
        var returned = false
        var out: Market.Outcome? = null
        m.create(request(), itemsBack = { returned = true }) { out = it }
        assertEquals("cannot_pay_fee", out?.key)
        assertTrue(returned)
        wallets.balances[seller to "vault"] = BigDecimal(20)
        val l = list(m)
        assertEquals("9", money(seller), "10 % of 100 + 1")
        assertEquals(l.id, store.activeListings("srv").single().id)
    }

    @Test fun `checks refuse what the config forbids`() {
        val m = market()
        assertEquals("price_out_of_bounds", m.check(request(price = "0.5"))?.key)
        assertEquals("unknown_currency", m.check(request().copy(currency = "gems"))?.key)
        config = config.copy(sales = config.sales.copy(enabled = false))
        assertEquals("sales_disabled", m.check(request())?.key)
        config = AuctionsConfig()
        repeat(2) { list(m) }
        assertEquals("limit_reached", m.check(request().copy(limit = 2))?.key)
    }

    @Test fun `expired and cancelled listings go back to the seller`() {
        val m = market()
        val a = list(m)
        val b = list(m)
        var out: Market.Outcome? = null
        m.cancel(alice, b.id, admin = false) { out = it }
        assertEquals("not_yours", out?.key)
        giver.online += seller
        m.cancel(seller, b.id, admin = false) { out = it }
        threads.drain()
        assertIs<Market.Cancelled>(out)
        assertEquals(listOf("blob-STONE"), giver.given[seller]?.toList())

        now += Duration.ofHours(49).toMillis()
        m.expireDue()
        threads.drain()
        assertEquals(ListingStatus.EXPIRED, store.listing(a.id)!!.status)
        assertEquals(listOf("blob-STONE"), store.unclaimed("srv", seller).map { it.itemBlob }, "expired items wait in the box")
    }

    @Test fun `a claim is delivered once and stays when the inventory is full`() {
        val m = market()
        val l = list(m)
        wallets.balances[alice to "vault"] = BigDecimal(100)
        m.buy(alice, l.id) {}
        threads.drain()
        val delivery = ClaimDelivery(store, threads, wallets, giver, { hooks }, logger) { now }
        giver.online += alice
        giver.full = true
        var report: ClaimDelivery.Report? = null
        delivery.deliverAll("srv", alice) { report = it }
        threads.drain()
        assertEquals(1, report!!.left.size)
        assertEquals(1, store.unclaimed("srv", alice).size, "reopened")
        giver.full = false
        delivery.deliverAll("srv", alice) { report = it }
        delivery.deliverAll("srv", alice) {}
        threads.drain()
        assertEquals(listOf("blob-STONE"), giver.given[alice]?.toList())
    }

    @Test fun `a sale row keeps the unit price, a mixed lot has none`() {
        val m = market()
        val stack = list(m, request(price = "64", items = listOf(item(qty = 64))))
        val rows = m.saleRows(stack, alice, BigDecimal("64.00"), now)
        assertEquals(0, BigDecimal.ONE.compareTo(rows.single().unitPrice))
        val lot = stack.copy(items = listOf(item("STONE"), item("DIRT").copy(slot = 1)))
        assertTrue(m.saleRows(lot, alice, BigDecimal.TEN, now).isEmpty())
    }
}
