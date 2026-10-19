package net.artisanmc.auctions.market

import net.artisanmc.auctions.api.ListingType
import java.math.BigDecimal
import java.util.UUID

enum class ListingStatus { ACTIVE, SOLD, EXPIRED, CANCELLED;
    val id: String get() = name.lowercase()
    companion object { fun of(id: String) = valueOf(id.uppercase()) }
}

/**
 * One stack of a listing. [blob] is the exact item (see ItemCodec), [display]
 * what menus show without decoding it, [priceKey] its identity for the median.
 */
data class ListingItem(
    val slot: Int,
    val blob: String,
    val display: Map<String, Any?>,
    val priceKey: String,
    val quantity: Int,
)

data class Listing(
    val id: UUID,
    val server: String,
    val type: ListingType,
    val status: ListingStatus,
    val sellerUuid: UUID,
    val sellerName: String,
    val currency: String,
    /** Sale price, or the buy-now price of an auction (null: none). */
    val price: BigDecimal?,
    val startPrice: BigDecimal?,
    val currentBid: BigDecimal?,
    val currentBidder: UUID?,
    val bidCount: Int,
    val createdAt: Long,
    val endsAt: Long,
    val rowVersion: Int,
    val items: List<ListingItem>,
) {
    val isLot: Boolean get() = items.size > 1
    val quantity: Int get() = items.sumOf { it.quantity }
    /** The key every stack shares, or null for a mixed lot (kept out of the median). */
    val sharedPriceKey: String? get() = items.map { it.priceKey }.distinct().singleOrNull()
}

enum class ClaimKind { ITEM, MONEY;
    val id: String get() = name.lowercase()
    companion object { fun of(id: String) = valueOf(id.uppercase()) }
}

/** Why something waits in a player's claims box. */
enum class ClaimReason { BOUGHT, SOLD, EXPIRED, CANCELLED, WON, REFUND, OUTBID, IMPORTED;
    val id: String get() = name.lowercase()
    companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: REFUND }
}

data class Claim(
    val id: UUID,
    val server: String,
    val owner: UUID,
    val kind: ClaimKind,
    val itemBlob: String?,
    val currency: String?,
    val amount: BigDecimal?,
    val reason: ClaimReason,
    val listingId: UUID?,
    val createdAt: Long,
) {
    companion object {
        fun item(server: String, owner: UUID, blob: String, reason: ClaimReason, listing: UUID?, now: Long) =
            Claim(UUID.randomUUID(), server, owner, ClaimKind.ITEM, blob, null, null, reason, listing, now)

        fun money(server: String, owner: UUID, currency: String, amount: BigDecimal, reason: ClaimReason, listing: UUID?, now: Long) =
            Claim(UUID.randomUUID(), server, owner, ClaimKind.MONEY, null, currency, amount, reason, listing, now)
    }
}

/** A concluded sale, the median's raw material. */
data class Sale(
    val id: UUID,
    val listingId: UUID,
    val server: String,
    val type: ListingType,
    val priceKey: String,
    val currency: String,
    val quantity: Int,
    val total: BigDecimal,
    val unitPrice: BigDecimal,
    val seller: UUID,
    val buyer: UUID?,
    val soldAt: Long,
)

enum class PaymentState { PENDING, SETTLED, REFUNDED;
    val id: String get() = name.lowercase()
}

/** A buyer's money taken before the sale is written. */
data class Payment(
    val id: UUID,
    val server: String,
    val player: UUID,
    val listingId: UUID,
    val currency: String,
    val amount: BigDecimal,
    val purpose: String,
    val createdAt: Long,
)
