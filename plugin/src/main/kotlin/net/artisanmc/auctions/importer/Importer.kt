package net.artisanmc.auctions.importer

import net.artisanmc.auctions.api.ListingType
import net.artisanmc.auctions.db.AuctionStore
import net.artisanmc.auctions.market.Claim
import net.artisanmc.auctions.market.ClaimReason
import net.artisanmc.auctions.market.Fees
import net.artisanmc.auctions.market.Listing
import net.artisanmc.auctions.market.ListingItem
import net.artisanmc.auctions.market.ListingStatus
import net.artisanmc.auctions.market.Sale
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/** An item as the source stored it, with an id for the report. */
data class SourceItem(val raw: String)

/** What the source holds, in this add-on's terms but with the source's item encoding and currency names. */
sealed interface SourceRecord {
    /** Stable id in the source, unique per record kind (prefix it with the kind). */
    val id: String
}

data class SourceListing(
    override val id: String,
    val seller: UUID,
    val sellerName: String,
    val currency: String,
    val price: BigDecimal,
    val createdAt: Long,
    val endsAt: Long,
    val items: List<SourceItem>,
) : SourceRecord

data class SourceClaim(
    override val id: String,
    val owner: UUID,
    val reason: ClaimReason,
    val createdAt: Long,
    /** An item claim has items (one claim each); a money claim has [currency] and [amount]. */
    val items: List<SourceItem> = emptyList(),
    val currency: String? = null,
    val amount: BigDecimal? = null,
) : SourceRecord

data class SourceSale(
    override val id: String,
    val seller: UUID,
    val buyer: UUID?,
    val currency: String,
    val total: BigDecimal,
    val soldAt: Long,
    val items: List<SourceItem>,
) : SourceRecord

/** Everything read from a source, plus what could not even be read. */
data class SourceData(val records: List<SourceRecord>, val problems: List<String> = emptyList())

/** One plugin we import from. [read] is blocking and never writes to the source. */
interface AuctionSource {
    val id: String
    fun read(): SourceData
}

/** An item converted to this add-on's form. */
data class ConvertedItem(val blob: String, val display: Map<String, Any?>, val priceKey: String, val quantity: Int)

fun interface ItemTranscoder {
    /** Null when the item cannot be read here (unknown class, missing item plugin). */
    fun convert(item: SourceItem): ConvertedItem?
}

/**
 * Turns source records into listings, claims and sales, once. Every record is
 * written with its `{prefix}import_map` row in one transaction, so a replay skips
 * what is done and a crash mid-import loses nothing. A record whose items
 * cannot all be read stays in the source and is reported, never half-imported.
 */
class Importer(
    private val server: String,
    private val store: AuctionStore,
    private val items: ItemTranscoder,
    /** Source currency name → registry id, or null when unmapped. */
    private val currency: (String) -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Report(
        val source: String,
        val dryRun: Boolean,
        val listings: Int = 0,
        val itemClaims: Int = 0,
        val moneyClaims: Int = 0,
        val sales: Int = 0,
        val alreadyImported: Int = 0,
        val unreadable: List<String> = emptyList(),
        val unknownCurrencies: Set<String> = emptySet(),
        val problems: List<String> = emptyList(),
    ) {
        /** A real import is refused while a currency has no provider. */
        val blocked: Boolean get() = unknownCurrencies.isNotEmpty()

        fun lines(): List<String> = buildList {
            add("${if (dryRun) "Dry run" else "Import"} from $source:")
            add("  active listings: $listings, items to claim: $itemClaims, amounts to claim: $moneyClaims, sales history: $sales")
            if (alreadyImported > 0) add("  already imported, skipped: $alreadyImported")
            if (unknownCurrencies.isNotEmpty()) add("  unknown currencies (map them in import.currency_map): ${unknownCurrencies.sorted().joinToString()}")
            if (unreadable.isNotEmpty()) add("  left in the source, unreadable items: ${unreadable.size} (${unreadable.take(10).joinToString()}${if (unreadable.size > 10) "…" else ""})")
            problems.take(10).forEach { add("  $it") }
        }
    }

    private class Counter { var listings = 0; var itemClaims = 0; var moneyClaims = 0; var sales = 0; var done = 0 }

    /** Blocking. With [dryRun] nothing is written. */
    fun run(source: String, data: SourceData, dryRun: Boolean): Report {
        val unknown = data.records.flatMap(::currenciesOf).filter { currency(it) == null }.toSet()
        val skip = store.importedIds(source)
        val unreadable = mutableListOf<String>()
        val problems = data.problems.toMutableList()
        val n = Counter()
        val write = !dryRun && unknown.isEmpty()
        val now = clock()

        for (r in data.records) {
            if (r.id in skip) { n.done++; continue }
            val converted = itemsOf(r).map { items.convert(it) }
            if (converted.any { it == null }) { unreadable += r.id; continue }
            val stacks = converted.filterNotNull()
            val target = UUID.randomUUID()
            when (r) {
                is SourceListing -> {
                    n.listings++
                    if (write) writeOrCount(source, r.id, target, now, n, listing = listing(target, r, stacks))
                }
                is SourceClaim -> {
                    val claims = claims(target, r, stacks)
                    if (claims.isEmpty()) { problems += "${r.id}: empty claim, skipped"; continue }
                    if (r.amount != null) n.moneyClaims++ else n.itemClaims += claims.size
                    if (write) writeOrCount(source, r.id, target, now, n, claims = claims)
                }
                is SourceSale -> {
                    val rows = sales(target, r, stacks)
                    if (rows.isEmpty()) continue // mixed lot or no price: not part of the median
                    n.sales++
                    if (write) writeOrCount(source, r.id, target, now, n, sales = rows)
                }
            }
        }
        return Report(source, dryRun || unknown.isNotEmpty(), n.listings, n.itemClaims, n.moneyClaims, n.sales,
            n.done, unreadable, unknown, problems)
    }

    private fun writeOrCount(
        source: String, id: String, target: UUID, now: Long, n: Counter,
        listing: Listing? = null, claims: List<Claim> = emptyList(), sales: List<Sale> = emptyList(),
    ) {
        if (!store.importRecord(source, id, target, now, listing, claims, sales)) n.done++
    }

    private fun currenciesOf(r: SourceRecord): List<String> = when (r) {
        is SourceListing -> listOf(r.currency)
        is SourceClaim -> listOfNotNull(r.currency)
        is SourceSale -> listOf(r.currency)
    }

    private fun itemsOf(r: SourceRecord): List<SourceItem> = when (r) {
        is SourceListing -> r.items
        is SourceClaim -> r.items
        is SourceSale -> r.items
    }

    private fun listing(id: UUID, r: SourceListing, stacks: List<ConvertedItem>) = Listing(
        id = id, server = server, type = ListingType.SALE, status = ListingStatus.ACTIVE,
        sellerUuid = r.seller, sellerName = r.sellerName, currency = currency(r.currency) ?: r.currency,
        price = Fees.money(r.price), startPrice = null, currentBid = null, currentBidder = null, bidCount = 0,
        createdAt = r.createdAt, endsAt = r.endsAt, rowVersion = 0,
        items = stacks.mapIndexed { i, s -> ListingItem(i, s.blob, s.display, s.priceKey, s.quantity) },
    )

    private fun claims(listing: UUID, r: SourceClaim, stacks: List<ConvertedItem>): List<Claim> {
        if (r.amount != null && r.currency != null) {
            if (r.amount.signum() <= 0) return emptyList()
            return listOf(Claim.money(server, r.owner, currency(r.currency) ?: r.currency, Fees.money(r.amount), r.reason, null, r.createdAt))
        }
        return stacks.map { Claim.item(server, r.owner, it.blob, r.reason, null, r.createdAt) }
    }

    private fun sales(listing: UUID, r: SourceSale, stacks: List<ConvertedItem>): List<Sale> {
        val key = stacks.map { it.priceKey }.distinct().singleOrNull() ?: return emptyList()
        if (r.total.signum() <= 0) return emptyList()
        val qty = stacks.sumOf { it.quantity }.coerceAtLeast(1)
        return listOf(Sale(UUID.randomUUID(), listing, server, ListingType.SALE, key, currency(r.currency) ?: r.currency, qty,
            Fees.money(r.total), r.total.divide(BigDecimal(qty), 4, RoundingMode.HALF_UP), r.seller, r.buyer, r.soldAt))
    }
}
