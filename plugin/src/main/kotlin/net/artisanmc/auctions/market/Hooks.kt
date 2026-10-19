package net.artisanmc.auctions.market

import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

/** A refusal from another plugin, with the message it wants shown (null: a generic one). */
class Veto(val message: Any?)

/** What a listing asks for before it exists. Another plugin may change [price], [startPrice] and [duration]. */
data class ListRequest(
    val seller: UUID,
    val sellerName: String,
    val type: net.artisanmc.auctions.api.ListingType,
    val items: List<ListingItem>,
    val currency: String,
    /** Fixed price, or the optional buy-now price of an auction. */
    val price: BigDecimal?,
    val startPrice: BigDecimal?,
    val duration: Duration,
    /** Active listings this seller may have, permissions included. */
    val limit: Int,
)

/**
 * The add-on's events, seen from the market. The Bukkit implementation fires
 * the `artisan-auctions-api` events; tests record the calls.
 */
interface Hooks {
    /** Before any money or item moves. Returns the request to use, or a veto. */
    fun preList(request: ListRequest): Pair<ListRequest?, Veto?> = request to null
    fun prePurchase(buyer: UUID, listing: Listing, price: BigDecimal): Veto? = null
    fun preBid(bidder: UUID, listing: Listing, amount: BigDecimal): Veto? = null
    fun preCancel(actor: UUID, listing: Listing, admin: Boolean): Veto? = null

    fun listed(listing: Listing) {}
    fun sold(listing: Listing, buyer: UUID, price: BigDecimal, tax: BigDecimal) {}
    fun bidPlaced(listing: Listing, bidder: UUID, amount: BigDecimal) {}
    fun outbid(listing: Listing, previous: UUID, amount: BigDecimal) {}
    fun expired(listing: Listing) {}
    fun cancelled(listing: Listing, actor: UUID) {}
    fun claimed(claim: Claim) {}

    object None : Hooks
}
