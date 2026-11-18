package net.artisanmc.auctions.stats

import net.artisanmc.auctions.api.ListingType
import net.artisanmc.auctions.market.Listing
import java.math.BigDecimal

/**
 * `auctions:market`: what the house holds now, one row per currency. No event records a present
 * state, so the editor asks for it (`StatsApi.declareQuery`).
 *
 * Row: `currency`, `sales` (active fixed-price listings), `auctions` (active auctions), `bids`
 * (auctions with a bid), `sale_value` (sum of the fixed prices), `bid_value` (sum of the best
 * bids), `money_waiting` (money in claims boxes), `items_waiting` (item claims, all currencies:
 * the same on every row).
 */
object MarketFigures {
    fun rows(active: Collection<Listing>, itemsWaiting: Int, moneyWaiting: Map<String, BigDecimal>): List<Map<String, Any>> {
        val byCurrency = active.groupBy { it.currency }
        return (byCurrency.keys + moneyWaiting.keys).distinct().sorted().map { currency ->
            val ls = byCurrency[currency].orEmpty()
            val sales = ls.filter { it.type == ListingType.SALE }
            val auctions = ls.filter { it.type == ListingType.AUCTION }
            linkedMapOf(
                "currency" to currency,
                "sales" to sales.size,
                "auctions" to auctions.size,
                "bids" to auctions.count { it.currentBid != null },
                "sale_value" to sales.sumOf { it.price ?: BigDecimal.ZERO }.toDouble(),
                "bid_value" to auctions.sumOf { it.currentBid ?: BigDecimal.ZERO }.toDouble(),
                "money_waiting" to (moneyWaiting[currency] ?: BigDecimal.ZERO).toDouble(),
                "items_waiting" to itemsWaiting,
            )
        }
    }
}
