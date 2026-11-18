package net.artisanmc.auctions.stats

import net.artisanmc.auctions.api.ListingType
import net.artisanmc.auctions.market.Hooks
import net.artisanmc.auctions.market.Listing
import java.math.BigDecimal
import java.util.UUID

/** Where [MarketStats] writes: Artisan's `StatsApi`, a list in tests. */
fun interface StatSink {
    fun record(event: String, player: UUID?, dimensions: Map<String, String>, value: Double, details: Map<String, String>)
}

/**
 * The market's Stat events (Artisan ADR `stats-foundation`), recorded around the hooks it wraps.
 * The event names, their dimensions and `auctions:market` are public: Artisan stores them and
 * the editor's Auctions tab reads them. Never rename one.
 *
 * | Event | Player | Dimensions | Value |
 * |---|---|---|---|
 * | `auctions:listed` | seller | type, currency, item | price, or opening bid |
 * | `auctions:sold` | buyer | type, currency, item | price paid (raw rows too: seller, seller_name, buyer_name, quantity, listing) |
 * | `auctions:bid` | bidder | currency, item | amount |
 * | `auctions:expired` | seller | type, currency, item | 1 |
 * | `auctions:cancelled` | seller | type, currency, item, by (`seller`, `admin`) | 1 |
 * | `auctions:revenue` | payer | currency, kind (`fee`, `tax`) | amount the house kept |
 *
 * `item` is the material in lower case (`diamond_sword`), `lot` for a lot of several kinds.
 */
class MarketStats(
    private val inner: Hooks,
    /** A player's name for the raw rows of a sale, null when unknown. */
    private val names: (UUID) -> String? = { null },
    private val sink: StatSink,
) : Hooks by inner {

    override fun listed(listing: Listing) {
        inner.listed(listing)
        rec(LISTED, listing.sellerUuid, dims(listing), (if (listing.type == ListingType.SALE) listing.price else listing.startPrice).d())
    }

    override fun feePaid(listing: Listing, fee: BigDecimal) {
        inner.feePaid(listing, fee)
        if (fee.signum() > 0) rec(REVENUE, listing.sellerUuid, mapOf("currency" to listing.currency, "kind" to "fee"), fee.d())
    }

    override fun sold(listing: Listing, buyer: UUID, price: BigDecimal, tax: BigDecimal) {
        inner.sold(listing, buyer, price, tax)
        rec(SOLD, buyer, dims(listing), price.d(), mapOf(
            "seller" to listing.sellerUuid.toString(), "seller_name" to listing.sellerName,
            "buyer_name" to (runCatching { names(buyer) }.getOrNull() ?: ""),
            "quantity" to listing.quantity.toString(), "listing" to listing.id.toString(),
        ))
        if (tax.signum() > 0) rec(REVENUE, listing.sellerUuid, mapOf("currency" to listing.currency, "kind" to "tax"), tax.d())
    }

    override fun bidPlaced(listing: Listing, bidder: UUID, amount: BigDecimal) {
        inner.bidPlaced(listing, bidder, amount)
        rec(BID, bidder, mapOf("currency" to listing.currency, "item" to item(listing)), amount.d())
    }

    override fun expired(listing: Listing) {
        inner.expired(listing)
        rec(EXPIRED, listing.sellerUuid, dims(listing), 1.0)
    }

    override fun cancelled(listing: Listing, actor: UUID) {
        inner.cancelled(listing, actor)
        rec(CANCELLED, listing.sellerUuid, dims(listing) + ("by" to if (actor == listing.sellerUuid) "seller" else "admin"), 1.0)
    }

    /** Statistics never stand in the way of a sale. */
    private fun rec(event: String, player: UUID?, dimensions: Map<String, String>, value: Double, details: Map<String, String> = emptyMap()) {
        runCatching { sink.record(event, player, dimensions, value, details) }
    }

    private fun BigDecimal?.d(): Double = this?.toDouble() ?: 0.0

    companion object {
        const val LISTED = "auctions:listed"
        const val SOLD = "auctions:sold"
        const val BID = "auctions:bid"
        const val EXPIRED = "auctions:expired"
        const val CANCELLED = "auctions:cancelled"
        const val REVENUE = "auctions:revenue"
        /** The figure computed on demand: what the house holds now, per currency. */
        const val MARKET = "auctions:market"

        fun dims(l: Listing): Map<String, String> =
            mapOf("type" to l.type.name.lowercase(), "currency" to l.currency, "item" to item(l))

        fun item(l: Listing): String =
            l.items.map { (it.display["material"] as? String)?.lowercase() ?: "unknown" }.distinct().singleOrNull() ?: "lot"
    }
}
