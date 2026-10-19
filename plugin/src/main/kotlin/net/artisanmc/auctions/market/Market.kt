package net.artisanmc.auctions.market

import net.artisanmc.auctions.api.ListingType
import net.artisanmc.auctions.config.AuctionsConfig
import net.artisanmc.auctions.db.AuctionStore
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The rules of the house: listing, buying, cancelling, expiring. Called on the
 * main thread; database work goes through [threads] and every result comes
 * back on the main thread. Money and items always move in an order where the
 * only possible failure ends in the claims box, never in a duplicate.
 */
class Market(
    private val server: String,
    private val store: AuctionStore,
    private val cache: ListingCache,
    private val threads: Threads,
    private val wallets: Wallets,
    private val delivery: ClaimDelivery,
    private val config: () -> AuctionsConfig,
    private val hooks: () -> Hooks,
    private val logger: Logger,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Listings with an operation in flight: a double click never charges twice. */
    private val busy = HashSet<UUID>()

    // ── Results ──────────────────────────────────────────────────────────────

    sealed interface Outcome {
        /** Message key under `auctions.messages`, with its placeholders. */
        val key: String
        val args: Map<String, Any> get() = emptyMap()
    }

    sealed interface Refusal : Outcome
    data class Problem(override val key: String, override val args: Map<String, Any> = emptyMap()) : Refusal
    data class Vetoed(val veto: Veto) : Refusal { override val key = "vetoed" }

    data class Listed(val listing: Listing, val fee: BigDecimal) : Outcome { override val key = "listed" }
    data class Bought(val listing: Listing, val price: BigDecimal) : Outcome { override val key = "bought" }
    data class Cancelled(val listing: Listing) : Outcome { override val key = "cancelled" }

    // ── Start-up ─────────────────────────────────────────────────────────────

    /** Loads the active listings and refunds payments a crash left pending. */
    fun start(done: () -> Unit = {}) {
        threads.async {
            val active = store.activeListings(server)
            val refunds = store.pendingPayments(server).mapNotNull { p ->
                val claim = Claim.money(server, p.player, p.currency, p.amount, ClaimReason.REFUND, p.listingId, clock())
                if (store.refundPayment(p.id, claim)) claim else null
            }
            if (refunds.isNotEmpty()) logger.warning("[Auctions] ${refunds.size} interrupted payment(s) refunded to the claims box")
            threads.main {
                cache.replaceAll(active)
                done()
            }
        }
    }

    // ── Listing ──────────────────────────────────────────────────────────────

    /** Checks that need no item and no money. Null = acceptable. */
    fun check(r: ListRequest): Problem? {
        val c = config()
        if (r.items.isEmpty()) return Problem("no_item")
        if (r.type == ListingType.SALE && !c.sales.enabled) return Problem("sales_disabled")
        if (r.type == ListingType.AUCTION && !c.auctions.enabled) return Problem("auctions_disabled")
        if (r.currency !in c.currencies.allowed || !wallets.has(r.currency)) return Problem("unknown_currency", mapOf("currency" to r.currency))
        if (cache.countBySeller(r.seller) >= r.limit) return Problem("limit_reached", mapOf("limit" to r.limit))
        val bounds = mapOf("min" to c.sales.minPrice, "max" to c.sales.maxPrice)
        when (r.type) {
            ListingType.SALE -> {
                val p = r.price ?: return Problem("price_required")
                if (p < c.sales.minPrice || p > c.sales.maxPrice) return Problem("price_out_of_bounds", bounds)
            }
            ListingType.AUCTION -> {
                val start = r.startPrice ?: return Problem("price_required")
                if (start < c.sales.minPrice || start > c.sales.maxPrice) return Problem("price_out_of_bounds", bounds)
                if (r.duration !in c.auctions.durations) return Problem("bad_duration")
                r.price?.let { buyout ->
                    if (!c.auctions.buyout) return Problem("buyout_disabled")
                    if (buyout <= start || buyout > c.sales.maxPrice) return Problem("buyout_too_low")
                }
            }
        }
        return null
    }

    /** The listing fee the seller would pay for [r]. */
    fun fee(r: ListRequest): BigDecimal =
        Fees.of(r.price ?: r.startPrice ?: BigDecimal.ZERO, config().limits.listingFee, capped = false)

    /**
     * Creates the listing. The caller has already run [check] and the pre-list
     * hook, and taken the items from the seller. [itemsBack] is called (main
     * thread) when the listing could not be written, to hand the items back.
     */
    fun create(r: ListRequest, itemsBack: () -> Unit, done: (Outcome) -> Unit) {
        val fee = fee(r)
        if (fee.signum() > 0 && !wallets.withdraw(r.seller, r.currency, fee)) {
            itemsBack()
            done(Problem("cannot_pay_fee", mapOf("fee" to fee)))
            return
        }
        val now = clock()
        val listing = Listing(
            id = UUID.randomUUID(), server = server, type = r.type, status = ListingStatus.ACTIVE,
            sellerUuid = r.seller, sellerName = r.sellerName, currency = r.currency,
            price = r.price?.let(Fees::money), startPrice = r.startPrice?.let(Fees::money),
            currentBid = null, currentBidder = null, bidCount = 0,
            createdAt = now, endsAt = now + r.duration.toMillis(), rowVersion = 0, items = r.items,
        )
        threads.async {
            val ok = runCatching { store.insertListing(listing) }
                .onFailure { logger.log(Level.SEVERE, "[Auctions] listing could not be saved, items returned", it) }
                .isSuccess
            threads.main {
                if (ok) {
                    cache.put(listing)
                    hooks().listed(listing)
                    done(Listed(listing, fee))
                } else {
                    itemsBack()
                    if (fee.signum() > 0 && !wallets.deposit(r.seller, r.currency, fee)) {
                        logger.severe("[Auctions] listing fee $fee ${r.currency} of ${r.seller} could not be refunded")
                    }
                    done(Problem("error"))
                }
            }
        }
    }

    // ── Buying ───────────────────────────────────────────────────────────────

    /** Price a buyer pays now: the sale price, or an auction's buy-now price. */
    fun buyPrice(l: Listing): BigDecimal? = when (l.type) {
        ListingType.SALE -> l.price
        ListingType.AUCTION -> l.price?.takeIf { config().auctions.buyout }
    }

    fun buy(buyer: UUID, id: UUID, done: (Outcome) -> Unit) {
        val l = cache.get(id) ?: return done(Problem("gone"))
        val price = buyPrice(l) ?: return done(Problem("not_for_sale"))
        if (l.sellerUuid == buyer) return done(Problem("own_listing"))
        if (l.endsAt <= clock()) return done(Problem("gone"))
        if (id in busy) return done(Problem("busy"))
        hooks().prePurchase(buyer, l, price)?.let { return done(Vetoed(it)) }
        if (wallets.balance(buyer, l.currency) < price) return done(Problem("insufficient_funds", mapOf("price" to price)))

        busy += id
        val payment = Payment(UUID.randomUUID(), server, buyer, id, l.currency, price, "buy", clock())
        threads.async {
            val journaled = runCatching { store.insertPayment(payment) }
                .onFailure { logger.log(Level.WARNING, "[Auctions] purchase journal failed", it) }.isSuccess
            threads.main {
                if (!journaled) { busy -= id; return@main done(Problem("error")) }
                if (!wallets.withdraw(buyer, l.currency, price)) {
                    busy -= id
                    threads.async { runCatching { store.deletePayment(payment.id) } }
                    return@main done(Problem("insufficient_funds", mapOf("price" to price)))
                }
                threads.async { settlePurchase(l, buyer, price, payment, done) }
            }
        }
    }

    private fun settlePurchase(l: Listing, buyer: UUID, price: BigDecimal, payment: Payment, done: (Outcome) -> Unit) {
        val now = clock()
        val tax = Fees.of(price, config().limits.saleTax)
        val net = Fees.money(price - tax)
        val toBuyer = l.items.map { Claim.item(server, buyer, it.blob, ClaimReason.BOUGHT, l.id, now) }
        val toSeller = listOfNotNull(
            net.takeIf { it.signum() > 0 }?.let { Claim.money(server, l.sellerUuid, l.currency, it, ClaimReason.SOLD, l.id, now) },
        )
        // An auction that had bids owes them back when someone buys it outright.
        val toBidder = listOfNotNull(l.currentBidder?.let { b ->
            l.currentBid?.let { Claim.money(server, b, l.currency, it, ClaimReason.OUTBID, l.id, now) }
        })
        val sold = try {
            store.close(l.id, l.rowVersion, ListingStatus.SOLD, now, toBuyer + toSeller + toBidder,
                saleRows(l, buyer, price, now), payment.id, winner = buyer, bids = "refunded")
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "[Auctions] purchase of ${l.id} failed, payment left to reconcile", e)
            threads.main { busy -= l.id; done(Problem("error")) }
            return
        }
        if (sold) {
            threads.main {
                busy -= l.id
                cache.remove(l.id)
                hooks().sold(l, buyer, price, tax)
                done(Bought(l, price))
                delivery.deliver(toBuyer, items = config().claims.deliverDirectly)
                delivery.deliver(toSeller + toBidder)
            }
        } else {
            // Somebody else won the race: the money goes back through the box.
            val refund = Claim.money(server, buyer, l.currency, price, ClaimReason.REFUND, l.id, now)
            val refunded = runCatching { store.refundPayment(payment.id, refund) }.getOrDefault(false)
            val fresh = runCatching { store.listing(l.id) }.getOrNull()
            threads.main {
                busy -= l.id
                if (fresh == null || fresh.status != ListingStatus.ACTIVE) cache.remove(l.id) else cache.put(fresh)
                done(Problem("gone"))
                if (refunded) delivery.deliver(listOf(refund))
            }
        }
    }

    /** One `auc_sales` row, unless the lot mixes items (no price per item). */
    internal fun saleRows(l: Listing, buyer: UUID?, total: BigDecimal, now: Long): List<Sale> {
        val key = l.sharedPriceKey ?: return emptyList()
        val qty = l.quantity.coerceAtLeast(1)
        return listOf(Sale(UUID.randomUUID(), l.id, server, l.type, key, l.currency, qty, total,
            total.divide(BigDecimal(qty), 4, RoundingMode.HALF_UP), l.sellerUuid, buyer, now))
    }

    // ── Cancelling and expiring ──────────────────────────────────────────────

    fun cancel(actor: UUID, id: UUID, admin: Boolean, done: (Outcome) -> Unit) {
        val l = cache.get(id) ?: return done(Problem("gone"))
        if (!admin && l.sellerUuid != actor) return done(Problem("not_yours"))
        if (!admin && l.bidCount > 0 && !config().limits.cancelAuctionWithBids) return done(Problem("has_bids"))
        if (id in busy) return done(Problem("busy"))
        hooks().preCancel(actor, l, admin)?.let { return done(Vetoed(it)) }
        busy += id
        threads.async {
            val now = clock()
            val claims = returnClaims(l, ClaimReason.CANCELLED, now)
            val ok = runCatching { store.close(l.id, l.rowVersion, ListingStatus.CANCELLED, now, claims, bids = "refunded") }
                .onFailure { logger.log(Level.WARNING, "[Auctions] cancel of ${l.id} failed", it) }.getOrDefault(false)
            threads.main {
                busy -= id
                if (!ok) return@main done(Problem("gone"))
                cache.remove(id)
                hooks().cancelled(l, actor)
                done(Cancelled(l))
                delivery.deliver(claims)
            }
        }
    }

    /** Items back to the seller, the leading bid back to its bidder. */
    private fun returnClaims(l: Listing, reason: ClaimReason, now: Long): List<Claim> =
        l.items.map { Claim.item(server, l.sellerUuid, it.blob, reason, l.id, now) } +
            listOfNotNull(l.currentBidder?.let { b ->
                l.currentBid?.let { Claim.money(server, b, l.currency, it, ClaimReason.REFUND, l.id, now) }
            })

    /** Ends what is due (main thread, every few seconds). Auctions with bids are left to [auctionEnd]. */
    fun expireDue(auctionEnd: (Listing) -> Unit = {}) {
        val now = clock()
        for (l in cache.due(now)) {
            if (l.id in busy) continue
            if (l.type == ListingType.AUCTION && l.currentBidder != null) { auctionEnd(l); continue }
            busy += l.id
            threads.async {
                val claims = returnClaims(l, ClaimReason.EXPIRED, now)
                val ok = runCatching { store.close(l.id, l.rowVersion, ListingStatus.EXPIRED, now, claims, bids = "refunded") }
                    .onFailure { logger.log(Level.WARNING, "[Auctions] expiry of ${l.id} failed", it) }.getOrDefault(false)
                val fresh = if (ok) null else runCatching { store.listing(l.id) }.getOrNull()
                threads.main {
                    busy -= l.id
                    if (ok) {
                        cache.remove(l.id)
                        hooks().expired(l)
                        delivery.deliver(claims, items = false)
                    } else if (fresh == null || fresh.status != ListingStatus.ACTIVE) cache.remove(l.id)
                    else cache.put(fresh)
                }
            }
        }
    }

    // ── Shared with the auction flow ─────────────────────────────────────────

    internal fun lock(id: UUID): Boolean = busy.add(id)
    internal fun unlock(id: UUID) { busy -= id }
}
