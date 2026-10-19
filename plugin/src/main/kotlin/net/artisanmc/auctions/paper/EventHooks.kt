package net.artisanmc.auctions.paper

import net.artisanmc.auctions.api.AuctionListing
import net.artisanmc.auctions.api.event.*
import net.artisanmc.auctions.market.Claim
import net.artisanmc.auctions.market.ClaimKind
import net.artisanmc.auctions.market.Hooks
import net.artisanmc.auctions.market.ListRequest
import net.artisanmc.auctions.market.Listing
import net.artisanmc.auctions.market.Veto
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.inventory.ItemStack
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

/** Immutable view of a listing for other plugins. Items are decoded on first use. */
class ListingSnapshot(private val l: Listing) : AuctionListing {
    private val stacks by lazy { l.items.mapNotNull { PaperItems.decodeOrNull(it.blob) } }
    override fun id(): UUID = l.id
    override fun type() = l.type
    override fun seller(): UUID = l.sellerUuid
    override fun sellerName(): String = l.sellerName
    override fun currency(): String = l.currency
    override fun price(): Optional<BigDecimal> = Optional.ofNullable(l.price)
    override fun startPrice(): Optional<BigDecimal> = Optional.ofNullable(l.startPrice)
    override fun currentBid(): Optional<BigDecimal> = Optional.ofNullable(l.currentBid)
    override fun currentBidder(): Optional<UUID> = Optional.ofNullable(l.currentBidder)
    override fun bidCount(): Int = l.bidCount
    override fun items(): List<ItemStack> = stacks.map(ItemStack::clone)
    override fun createdAt(): Instant = Instant.ofEpochMilli(l.createdAt)
    override fun endsAt(): Instant = Instant.ofEpochMilli(l.endsAt)
}

/**
 * Fires the `artisan-auctions-api` events and tells players what happened.
 * Every call runs on the main thread, as Bukkit events must.
 */
class EventHooks(
    private val notifier: ModuleNotifier,
    private val median: (ListRequest) -> BigDecimal? = { null },
    private val onSold: (Listing) -> Unit = {},
    private val listingsChanged: (UUID) -> Unit,
    private val claimsChanged: (Set<UUID>) -> Unit,
) : Hooks {
    private fun call(e: Event) = Bukkit.getPluginManager().callEvent(e)

    private fun veto(e: CancellableAuctionEvent): Veto? = if (e.isCancelled) Veto(e.denyMessage()) else null

    override fun preList(request: ListRequest): Pair<ListRequest?, Veto?> {
        val seller = Bukkit.getPlayer(request.seller) ?: return request to null
        val stacks = request.items.mapNotNull { PaperItems.decodeOrNull(it.blob) }
        val e = AuctionPreListEvent(seller, stacks, request.type, request.currency,
            request.price, request.startPrice, request.duration, median(request))
        call(e)
        veto(e)?.let { return null to it }
        return request.copy(price = e.price().orElse(null), startPrice = e.startPrice().orElse(null), duration = e.duration()) to null
    }

    override fun prePurchase(buyer: UUID, listing: Listing, price: BigDecimal): Veto? {
        val p = Bukkit.getPlayer(buyer) ?: return null
        return veto(AuctionPrePurchaseEvent(p, ListingSnapshot(listing), price).also(::call))
    }

    override fun preBid(bidder: UUID, listing: Listing, amount: BigDecimal): Veto? {
        val p = Bukkit.getPlayer(bidder) ?: return null
        return veto(AuctionPreBidEvent(p, ListingSnapshot(listing), amount).also(::call))
    }

    override fun preCancel(actor: UUID, listing: Listing, admin: Boolean): Veto? =
        veto(AuctionPreCancelEvent(Bukkit.getOfflinePlayer(actor), ListingSnapshot(listing), admin).also(::call))

    override fun listed(listing: Listing) {
        call(AuctionListedEvent(ListingSnapshot(listing)))
        listingsChanged(listing.id)
    }

    override fun sold(listing: Listing, buyer: UUID, price: BigDecimal, tax: BigDecimal) {
        onSold(listing)
        call(AuctionSoldEvent(ListingSnapshot(listing), buyer, price, tax))
        notifier.tell(listing.sellerUuid, "sold", notifier.listingArgs(listing) +
            mapOf("buyer" to (Bukkit.getOfflinePlayer(buyer).name ?: "?"), "net" to notifier.money(listing.currency, price - tax)))
        listingsChanged(listing.id)
    }

    override fun bidPlaced(listing: Listing, bidder: UUID, amount: BigDecimal) {
        call(AuctionBidPlacedEvent(ListingSnapshot(listing), bidder, amount))
        notifier.tell(listing.sellerUuid, "bid_received", notifier.listingArgs(listing) +
            mapOf("bidder" to (Bukkit.getOfflinePlayer(bidder).name ?: "?"), "amount" to notifier.money(listing.currency, amount)))
        listingsChanged(listing.id)
    }

    override fun outbid(listing: Listing, previous: UUID, amount: BigDecimal) {
        call(AuctionOutbidEvent(ListingSnapshot(listing), previous, amount))
        notifier.tell(previous, "outbid", notifier.listingArgs(listing) + mapOf("amount" to notifier.money(listing.currency, amount)))
    }

    override fun won(listing: Listing, winner: UUID, price: BigDecimal) {
        notifier.tell(winner, "won", notifier.listingArgs(listing) + mapOf("amount" to notifier.money(listing.currency, price)))
    }

    override fun expired(listing: Listing) {
        call(AuctionExpiredEvent(ListingSnapshot(listing)))
        notifier.tell(listing.sellerUuid, "expired", notifier.listingArgs(listing))
        listingsChanged(listing.id)
    }

    override fun cancelled(listing: Listing, actor: UUID) {
        call(AuctionCancelledEvent(ListingSnapshot(listing), actor))
        if (actor != listing.sellerUuid) notifier.tell(listing.sellerUuid, "cancelled_by_admin", notifier.listingArgs(listing))
        listingsChanged(listing.id)
    }

    override fun claimed(claim: Claim) {
        call(AuctionClaimedEvent(claim.owner, claim.kind == ClaimKind.MONEY, claim.currency, claim.amount))
    }

    override fun claimsChanged(owners: Set<UUID>) = claimsChanged.invoke(owners)
}
