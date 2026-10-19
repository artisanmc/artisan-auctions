package net.artisanmc.auctions.sources

import net.artisanmc.auctions.api.ListingType
import net.artisanmc.auctions.config.AuctionsConfig
import net.artisanmc.auctions.market.Fees
import net.artisanmc.auctions.market.Listing
import java.math.BigDecimal
import java.util.UUID

/**
 * Menu rows of the listings: everything a menu shows is precomputed here,
 * because templates cannot format numbers. Keys are documented in
 * [net.artisanmc.auctions.paper.ModuleBindings.LISTING_FIELDS].
 */
object ListingRows {
    /** What `auctions:listings` accepts as params. */
    data class Query(
        val tab: String = "all",
        val sort: String = "newest",
        val search: String = "",
        /** Only the listings of this player (`auctions:my_listings`). */
        val seller: UUID? = null,
    ) {
        companion object {
            fun of(params: Map<String, Any?>, seller: UUID? = null) = Query(
                tab = params["tab"]?.toString()?.lowercase()?.ifBlank { null } ?: "all",
                sort = params["sort"]?.toString()?.lowercase()?.ifBlank { null } ?: "newest",
                search = params["search"]?.toString()?.trim().orEmpty(),
                seller = seller,
            )
        }
    }

    val SORTS = listOf("newest", "oldest", "price_asc", "price_desc", "ending")

    fun select(all: Collection<Listing>, q: Query, now: Long): List<Listing> {
        val needle = q.search.lowercase()
        return all.asSequence()
            .filter { it.endsAt > now }
            .filter { q.seller == null || it.sellerUuid == q.seller }
            .filter {
                when (q.tab) {
                    "sale", "sales" -> it.type == ListingType.SALE
                    "auction", "auctions" -> it.type == ListingType.AUCTION
                    else -> true
                }
            }
            .filter { needle.isEmpty() || searchText(it).contains(needle) }
            .sortedWith(comparator(q.sort))
            .toList()
    }

    private fun comparator(sort: String): Comparator<Listing> = when (sort) {
        "oldest" -> compareBy { it.createdAt }
        "price_asc" -> compareBy<Listing> { shownPrice(it) }.thenByDescending { it.createdAt }
        "price_desc" -> compareByDescending<Listing> { shownPrice(it) }.thenByDescending { it.createdAt }
        "ending" -> compareBy { it.endsAt }
        else -> compareByDescending { it.createdAt }
    }

    /** The price a player compares: sale price, else the bid to beat. */
    fun shownPrice(l: Listing): BigDecimal = when (l.type) {
        ListingType.SALE -> l.price ?: BigDecimal.ZERO
        ListingType.AUCTION -> l.currentBid ?: l.startPrice ?: BigDecimal.ZERO
    }

    private fun searchText(l: Listing): String = buildString {
        append(l.sellerName).append(' ')
        for (item in l.items) {
            append(plain(item.display["title"]?.toString().orEmpty())).append(' ')
            append(item.display["material"]?.toString().orEmpty().replace('_', ' ')).append(' ')
        }
    }.lowercase()

    private val TAG = Regex("<[^>]*>")

    /** MiniMessage without its tags. */
    fun plain(mini: String): String = TAG.replace(mini, "").trim()

    /** `DIAMOND_SWORD` → `Diamond Sword`. */
    fun prettyMaterial(material: String): String =
        material.lowercase().split('_').filter { it.isNotEmpty() }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    fun name(l: Listing): String {
        val first = l.items.firstOrNull() ?: return "?"
        val title = first.display["title"]?.toString()?.takeIf { it.isNotBlank() }
        return title ?: prettyMaterial(first.display["material"]?.toString() ?: "STONE")
    }

    fun row(l: Listing, viewer: UUID?, c: AuctionsConfig, now: Long): Map<String, Any?> {
        val fmt = { v: BigDecimal? -> v?.let { c.currencies.format(l.currency, it) } ?: "" }
        val buyNow = when (l.type) {
            ListingType.SALE -> l.price
            ListingType.AUCTION -> l.price?.takeIf { c.auctions.buyout }
        }
        val minBid = if (l.type == ListingType.AUCTION && l.startPrice != null)
            Fees.minBid(l.startPrice, l.currentBid, c.auctions.minIncrement) else null
        val first = l.items.firstOrNull()
        val icon = LinkedHashMap<String, Any?>(first?.display ?: mapOf("material" to "STONE"))
        icon["count"] = (first?.quantity ?: 1).coerceIn(1, 64)
        return mapOf(
            "id" to l.id.toString(),
            "type" to l.type.id(),
            "is_sale" to (l.type == ListingType.SALE),
            "is_auction" to (l.type == ListingType.AUCTION),
            "icon" to icon,
            "name" to name(l),
            "seller" to l.sellerName,
            "seller_uuid" to l.sellerUuid.toString(),
            "own" to (viewer != null && viewer == l.sellerUuid),
            "currency" to l.currency,
            "price" to shownPrice(l).toDouble(),
            "price_display" to fmt(shownPrice(l)),
            "buy_now" to (buyNow != null),
            "buy_now_display" to fmt(buyNow),
            "start_price_display" to fmt(l.startPrice),
            "current_bid_display" to fmt(l.currentBid),
            "min_bid" to (minBid?.toPlainString() ?: ""),
            "min_bid_display" to fmt(minBid),
            "has_bids" to (l.bidCount > 0),
            "bid_count" to l.bidCount,
            "leading" to (viewer != null && viewer == l.currentBidder),
            "is_lot" to l.isLot,
            "lot_size" to l.items.size,
            "quantity" to l.quantity,
            "created_at" to l.createdAt,
            "ends_at" to l.endsAt,
            "ended" to (l.endsAt <= now),
        )
    }
}
