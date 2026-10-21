package net.artisanmc.auctions.db

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.artisanmc.auctions.api.ListingType
import net.artisanmc.auctions.market.Claim
import net.artisanmc.auctions.market.ClaimKind
import net.artisanmc.auctions.market.ClaimReason
import net.artisanmc.auctions.market.Listing
import net.artisanmc.auctions.market.ListingItem
import net.artisanmc.auctions.market.ListingStatus
import net.artisanmc.auctions.market.Payment
import net.artisanmc.auctions.market.PaymentState
import net.artisanmc.auctions.market.Sale
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.UUID
import javax.sql.DataSource

/**
 * Every SQL statement of the add-on. Blocking: called from the database
 * executor, never from the main thread. Each state change of a listing is a
 * compare-and-swap on `status = 'active' AND row_version = ?`, so a listing is
 * sold, expired or cancelled exactly once, even with two servers on one database.
 * [prefix] is `api.database.tablePrefix(Schema.MODULE)`, such as `artisan_auctions_`.
 */
class AuctionStore(private val ds: DataSource, private val prefix: String) {

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type

    // ── Listings ─────────────────────────────────────────────────────────────

    fun insertListing(l: Listing) = tx { c -> insertListing(c, l) }

    private fun insertListing(c: Connection, l: Listing) {
        c.prepareStatement(
            "INSERT INTO ${prefix}listings (id, server, listing_type, status, seller_uuid, seller_name, currency, price, " +
                "start_price, current_bid, current_bidder, bid_count, created_at, ends_at, row_version) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        ).use { s ->
            s.setString(1, l.id.toString()); s.setString(2, l.server); s.setString(3, l.type.id())
            s.setString(4, l.status.id); s.setString(5, l.sellerUuid.toString()); s.setString(6, l.sellerName)
            s.setString(7, l.currency); s.setAmount(8, l.price); s.setAmount(9, l.startPrice)
            s.setAmount(10, l.currentBid); s.setString(11, l.currentBidder?.toString()); s.setInt(12, l.bidCount)
            s.setLong(13, l.createdAt); s.setLong(14, l.endsAt); s.setInt(15, l.rowVersion)
            s.executeUpdate()
        }
        c.prepareStatement(
            "INSERT INTO ${prefix}listing_items (listing_id, slot_index, item_blob, item_display, price_key, quantity) VALUES (?, ?, ?, ?, ?, ?)",
        ).use { s ->
            for (item in l.items) {
                s.setString(1, l.id.toString()); s.setInt(2, item.slot); s.setString(3, item.blob)
                s.setString(4, gson.toJson(item.display)); s.setString(5, item.priceKey); s.setInt(6, item.quantity)
                s.addBatch()
            }
            s.executeBatch()
        }
    }

    fun activeListings(server: String): List<Listing> = read { c ->
        val rows = c.prepareStatement("SELECT * FROM ${prefix}listings WHERE server = ? AND status = 'active'").use { s ->
            s.setString(1, server)
            s.executeQuery().use { rs -> buildList { while (rs.next()) add(listingRow(rs)) } }
        }
        attachItems(c, rows)
    }

    fun listing(id: UUID): Listing? = read { c ->
        val row = c.prepareStatement("SELECT * FROM ${prefix}listings WHERE id = ?").use { s ->
            s.setString(1, id.toString())
            s.executeQuery().use { rs -> if (rs.next()) listingRow(rs) else null }
        } ?: return@read null
        attachItems(c, listOf(row)).single()
    }

    /**
     * Moves an active listing to [status] if nobody changed it since [expectedVersion],
     * and in the same transaction writes what that outcome owes: [claims], [sales],
     * and the settlement of [payment]. False (and nothing written) when the listing
     * was not active at that version anymore.
     */
    fun close(
        id: UUID, expectedVersion: Int, status: ListingStatus, now: Long,
        claims: List<Claim> = emptyList(), sales: List<Sale> = emptyList(), payment: UUID? = null,
        winner: UUID? = null,
        /** New state of the listing's `held` bids (`refunded`, `captured`), if any. */
        bids: String? = null,
    ): Boolean = tx { c ->
        val changed = c.prepareStatement(
            "UPDATE ${prefix}listings SET status = ?, closed_at = ?, row_version = row_version + 1" +
                (if (winner != null) ", current_bidder = ?" else "") +
                " WHERE id = ? AND status = 'active' AND row_version = ?",
        ).use { s ->
            var i = 0
            s.setString(++i, status.id); s.setLong(++i, now)
            if (winner != null) s.setString(++i, winner.toString())
            s.setString(++i, id.toString()); s.setInt(++i, expectedVersion)
            s.executeUpdate()
        }
        if (changed != 1) return@tx false
        insertClaims(c, claims)
        insertSales(c, sales)
        if (payment != null) setPaymentState(c, payment, PaymentState.SETTLED)
        if (bids != null) {
            c.prepareStatement("UPDATE ${prefix}bids SET state = ? WHERE listing_id = ? AND state = 'held'").use { s ->
                s.setString(1, bids); s.setString(2, id.toString()); s.executeUpdate()
            }
        }
        true
    }

    /**
     * Records a bid on an active auction still at [expectedVersion]: the listing
     * takes the new leader, end and bid count, the bid is held, the previous held
     * bid is released through [outbid] (its refund), and the bidder's [payment]
     * is settled. False (nothing written) when someone changed the listing first.
     */
    fun placeBid(
        id: UUID, expectedVersion: Int, bidId: UUID, bidder: UUID, currency: String, amount: BigDecimal,
        endsAt: Long, now: Long, outbid: Claim?, payment: UUID,
    ): Boolean = tx { c ->
        val changed = c.prepareStatement(
            "UPDATE ${prefix}listings SET current_bid = ?, current_bidder = ?, bid_count = bid_count + 1, ends_at = ?, " +
                "row_version = row_version + 1 WHERE id = ? AND status = 'active' AND row_version = ?",
        ).use { s ->
            s.setAmount(1, amount); s.setString(2, bidder.toString()); s.setLong(3, endsAt)
            s.setString(4, id.toString()); s.setInt(5, expectedVersion)
            s.executeUpdate()
        }
        if (changed != 1) return@tx false
        c.prepareStatement("UPDATE ${prefix}bids SET state = 'refunded' WHERE listing_id = ? AND state = 'held'").use { s ->
            s.setString(1, id.toString()); s.executeUpdate()
        }
        c.prepareStatement(
            "INSERT INTO ${prefix}bids (id, listing_id, bidder_uuid, currency, amount, state, created_at) VALUES (?, ?, ?, ?, ?, 'held', ?)",
        ).use { s ->
            s.setString(1, bidId.toString()); s.setString(2, id.toString()); s.setString(3, bidder.toString())
            s.setString(4, currency); s.setAmount(5, amount); s.setLong(6, now)
            s.executeUpdate()
        }
        if (outbid != null) insertClaims(c, listOf(outbid))
        setPaymentState(c, payment, PaymentState.SETTLED)
        true
    }

    /** Bids of a listing, newest first. */
    fun bids(listing: UUID): List<Triple<UUID, BigDecimal, String>> = read { c ->
        c.prepareStatement("SELECT bidder_uuid, amount, state FROM ${prefix}bids WHERE listing_id = ? ORDER BY created_at DESC").use { s ->
            s.setString(1, listing.toString())
            s.executeQuery().use { rs ->
                buildList { while (rs.next()) add(Triple(UUID.fromString(rs.getString(1)), rs.getBigDecimal(2), rs.getString(3))) }
            }
        }
    }

    // ── Imports ──────────────────────────────────────────────────────────────

    /** Source ids of [source] already imported. */
    fun importedIds(source: String): Set<String> = read { c ->
        c.prepareStatement("SELECT source_id FROM ${prefix}import_map WHERE source = ?").use { s ->
            s.setString(1, source)
            s.executeQuery().use { rs -> buildSet { while (rs.next()) add(rs.getString(1)) } }
        }
    }

    /**
     * Writes one imported record and its `{prefix}import_map` row in one transaction.
     * False when [sourceId] was already imported: replaying an import skips it.
     */
    fun importRecord(
        source: String, sourceId: String, target: UUID, now: Long,
        listing: Listing? = null, claims: List<Claim> = emptyList(), sales: List<Sale> = emptyList(),
    ): Boolean = tx { c ->
        val fresh = c.prepareStatement("SELECT 1 FROM ${prefix}import_map WHERE source = ? AND source_id = ?").use { s ->
            s.setString(1, source); s.setString(2, sourceId)
            s.executeQuery().use { rs -> !rs.next() }
        }
        if (!fresh) return@tx false
        // The primary key makes a concurrent replay fail here rather than write twice.
        c.prepareStatement("INSERT INTO ${prefix}import_map (source, source_id, target_id, imported_at) VALUES (?, ?, ?, ?)").use { s ->
            s.setString(1, source); s.setString(2, sourceId); s.setString(3, target.toString()); s.setLong(4, now)
            s.executeUpdate()
        }
        listing?.let { insertListing(c, it) }
        insertClaims(c, claims)
        insertSales(c, sales)
        true
    }

    // ── Reindexing price keys ────────────────────────────────────────────────

    /** A page of this server's listings (any status) with their items, by id, after [afterId]. */
    fun listingsPage(server: String, afterId: String, limit: Int): List<Listing> = read { c ->
        val rows = c.prepareStatement("SELECT * FROM ${prefix}listings WHERE server = ? AND id > ? ORDER BY id LIMIT ?").use { s ->
            s.setString(1, server); s.setString(2, afterId); s.setInt(3, limit)
            s.executeQuery().use { rs -> buildList { while (rs.next()) add(listingRow(rs)) } }
        }
        attachItems(c, rows)
    }

    /** New keys of a listing's items and, when they all agree, of its sale rows. */
    fun updatePriceKeys(listing: UUID, keys: Map<Int, String>, saleKey: String?) = tx { c ->
        c.prepareStatement("UPDATE ${prefix}listing_items SET price_key = ? WHERE listing_id = ? AND slot_index = ?").use { s ->
            for ((slot, key) in keys) {
                s.setString(1, key); s.setString(2, listing.toString()); s.setInt(3, slot); s.addBatch()
            }
            s.executeBatch()
        }
        if (saleKey != null) {
            c.prepareStatement("UPDATE ${prefix}sales SET price_key = ? WHERE listing_id = ?").use { s ->
                s.setString(1, saleKey); s.setString(2, listing.toString()); s.executeUpdate()
            }
        }
    }

    // ── Claims ───────────────────────────────────────────────────────────────

    fun insertClaims(claims: List<Claim>) { if (claims.isNotEmpty()) tx { c -> insertClaims(c, claims) } }

    fun unclaimed(server: String, owner: UUID): List<Claim> = read { c ->
        c.prepareStatement(
            "SELECT * FROM ${prefix}claims WHERE server = ? AND owner_uuid = ? AND claimed_at IS NULL ORDER BY created_at",
        ).use { s ->
            s.setString(1, server); s.setString(2, owner.toString())
            s.executeQuery().use { rs -> buildList { while (rs.next()) add(claimRow(rs)) } }
        }
    }

    fun claim(id: UUID): Claim? = read { c ->
        c.prepareStatement("SELECT * FROM ${prefix}claims WHERE id = ? AND claimed_at IS NULL").use { s ->
            s.setString(1, id.toString())
            s.executeQuery().use { rs -> if (rs.next()) claimRow(rs) else null }
        }
    }

    /** True for the one caller that takes the claim. */
    fun markClaimed(id: UUID, now: Long): Boolean = tx { c ->
        c.prepareStatement("UPDATE ${prefix}claims SET claimed_at = ? WHERE id = ? AND claimed_at IS NULL").use { s ->
            s.setLong(1, now); s.setString(2, id.toString()); s.executeUpdate() == 1
        }
    }

    /** Puts back a claim whose delivery failed (full inventory, refused deposit). */
    fun reopenClaim(id: UUID) = tx { c ->
        c.prepareStatement("UPDATE ${prefix}claims SET claimed_at = NULL WHERE id = ?").use { s ->
            s.setString(1, id.toString()); s.executeUpdate()
        }
    }

    /** Unclaimed claims created before [before]; [delete] removes them. */
    fun staleClaims(server: String, before: Long): List<Claim> = read { c ->
        c.prepareStatement("SELECT * FROM ${prefix}claims WHERE server = ? AND claimed_at IS NULL AND created_at < ?").use { s ->
            s.setString(1, server); s.setLong(2, before)
            s.executeQuery().use { rs -> buildList { while (rs.next()) add(claimRow(rs)) } }
        }
    }

    fun deleteClaim(id: UUID) = tx { c ->
        c.prepareStatement("DELETE FROM ${prefix}claims WHERE id = ? AND claimed_at IS NULL").use { s ->
            s.setString(1, id.toString()); s.executeUpdate()
        }
    }

    // ── Payments journal ─────────────────────────────────────────────────────

    fun insertPayment(p: Payment) = tx { c ->
        c.prepareStatement(
            "INSERT INTO ${prefix}payments (id, server, player_uuid, listing_id, currency, amount, purpose, state, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        ).use { s ->
            s.setString(1, p.id.toString()); s.setString(2, p.server); s.setString(3, p.player.toString())
            s.setString(4, p.listingId.toString()); s.setString(5, p.currency); s.setAmount(6, p.amount)
            s.setString(7, p.purpose); s.setString(8, PaymentState.PENDING.id); s.setLong(9, p.createdAt)
            s.executeUpdate()
        }
    }

    /** The charge never happened (refused by the economy): forget the intent. */
    fun deletePayment(id: UUID) = tx { c ->
        c.prepareStatement("DELETE FROM ${prefix}payments WHERE id = ? AND state = 'pending'").use { s ->
            s.setString(1, id.toString()); s.executeUpdate()
        }
    }

    /** Settles a pending payment as a refund: the money goes to the claims box. False when no longer pending. */
    fun refundPayment(id: UUID, refund: Claim): Boolean = tx { c ->
        if (!setPaymentState(c, id, PaymentState.REFUNDED)) return@tx false
        insertClaims(c, listOf(refund))
        true
    }

    fun pendingPayments(server: String): List<Payment> = read { c ->
        c.prepareStatement("SELECT * FROM ${prefix}payments WHERE server = ? AND state = 'pending'").use { s ->
            s.setString(1, server)
            s.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(Payment(
                        UUID.fromString(rs.getString("id")), rs.getString("server"), UUID.fromString(rs.getString("player_uuid")),
                        UUID.fromString(rs.getString("listing_id")), rs.getString("currency"), rs.getBigDecimal("amount"),
                        rs.getString("purpose"), rs.getLong("created_at"),
                    ))
                }
            }
        }
    }

    // ── Sales history ────────────────────────────────────────────────────────

    fun insertSales(sales: List<Sale>) { if (sales.isNotEmpty()) tx { c -> insertSales(c, sales) } }

    /** Seller and unit price of the latest sales of a key, newest first. */
    fun recentSales(priceKey: String, currency: String, since: Long, limit: Int, types: Set<String>): List<Pair<UUID, BigDecimal>> =
        read { c ->
            c.prepareStatement(
                "SELECT seller_uuid, unit_price, listing_type FROM ${prefix}sales WHERE price_key = ? AND currency = ? AND sold_at >= ? " +
                    "ORDER BY sold_at DESC",
            ).use { s ->
                s.setString(1, priceKey); s.setString(2, currency); s.setLong(3, since)
                s.maxRows = limit * 2
                s.executeQuery().use { rs ->
                    buildList {
                        while (rs.next() && size < limit) {
                            if (rs.getString("listing_type") in types) {
                                add(UUID.fromString(rs.getString("seller_uuid")) to rs.getBigDecimal("unit_price"))
                            }
                        }
                    }
                }
            }
        }

    // ── Rows ─────────────────────────────────────────────────────────────────

    private fun listingRow(rs: ResultSet) = Listing(
        id = UUID.fromString(rs.getString("id")),
        server = rs.getString("server"),
        type = ListingType.valueOf(rs.getString("listing_type").uppercase()),
        status = ListingStatus.of(rs.getString("status")),
        sellerUuid = UUID.fromString(rs.getString("seller_uuid")),
        sellerName = rs.getString("seller_name"),
        currency = rs.getString("currency"),
        price = rs.getBigDecimal("price"),
        startPrice = rs.getBigDecimal("start_price"),
        currentBid = rs.getBigDecimal("current_bid"),
        currentBidder = rs.getString("current_bidder")?.let(UUID::fromString),
        bidCount = rs.getInt("bid_count"),
        createdAt = rs.getLong("created_at"),
        endsAt = rs.getLong("ends_at"),
        rowVersion = rs.getInt("row_version"),
        items = emptyList(),
    )

    private fun attachItems(c: Connection, listings: List<Listing>): List<Listing> {
        if (listings.isEmpty()) return listings
        val byId = HashMap<String, MutableList<ListingItem>>()
        // Chunked IN lists: H2 and MySQL both cap the statement size.
        for (chunk in listings.chunked(500)) {
            val marks = chunk.joinToString(",") { "?" }
            c.prepareStatement("SELECT * FROM ${prefix}listing_items WHERE listing_id IN ($marks)").use { s ->
                chunk.forEachIndexed { i, l -> s.setString(i + 1, l.id.toString()) }
                s.executeQuery().use { rs ->
                    while (rs.next()) {
                        byId.getOrPut(rs.getString("listing_id")) { mutableListOf() } += ListingItem(
                            slot = rs.getInt("slot_index"),
                            blob = rs.getString("item_blob"),
                            display = gson.fromJson(rs.getString("item_display"), mapType) ?: emptyMap(),
                            priceKey = rs.getString("price_key"),
                            quantity = rs.getInt("quantity"),
                        )
                    }
                }
            }
        }
        return listings.map { l -> l.copy(items = byId[l.id.toString()].orEmpty().sortedBy { it.slot }) }
    }

    private fun claimRow(rs: ResultSet) = Claim(
        id = UUID.fromString(rs.getString("id")),
        server = rs.getString("server"),
        owner = UUID.fromString(rs.getString("owner_uuid")),
        kind = ClaimKind.of(rs.getString("claim_kind")),
        itemBlob = rs.getString("item_blob"),
        currency = rs.getString("currency"),
        amount = rs.getBigDecimal("amount"),
        reason = ClaimReason.of(rs.getString("reason")),
        listingId = rs.getString("listing_id")?.let(UUID::fromString),
        createdAt = rs.getLong("created_at"),
    )

    private fun insertClaims(c: Connection, claims: List<Claim>) {
        if (claims.isEmpty()) return
        c.prepareStatement(
            "INSERT INTO ${prefix}claims (id, server, owner_uuid, claim_kind, item_blob, currency, amount, reason, listing_id, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        ).use { s ->
            for (cl in claims) {
                s.setString(1, cl.id.toString()); s.setString(2, cl.server); s.setString(3, cl.owner.toString())
                s.setString(4, cl.kind.id); s.setString(5, cl.itemBlob); s.setString(6, cl.currency)
                s.setAmount(7, cl.amount); s.setString(8, cl.reason.id); s.setString(9, cl.listingId?.toString())
                s.setLong(10, cl.createdAt)
                s.addBatch()
            }
            s.executeBatch()
        }
    }

    private fun insertSales(c: Connection, sales: List<Sale>) {
        if (sales.isEmpty()) return
        c.prepareStatement(
            "INSERT INTO ${prefix}sales (id, listing_id, server, listing_type, price_key, currency, quantity, total, unit_price, " +
                "seller_uuid, buyer_uuid, sold_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        ).use { s ->
            for (sale in sales) {
                s.setString(1, sale.id.toString()); s.setString(2, sale.listingId.toString()); s.setString(3, sale.server)
                s.setString(4, sale.type.id()); s.setString(5, sale.priceKey); s.setString(6, sale.currency)
                s.setInt(7, sale.quantity); s.setAmount(8, sale.total); s.setAmount(9, sale.unitPrice)
                s.setString(10, sale.seller.toString()); s.setString(11, sale.buyer?.toString()); s.setLong(12, sale.soldAt)
                s.addBatch()
            }
            s.executeBatch()
        }
    }

    private fun setPaymentState(c: Connection, id: UUID, state: PaymentState): Boolean =
        c.prepareStatement("UPDATE ${prefix}payments SET state = ? WHERE id = ? AND state = 'pending'").use { s ->
            s.setString(1, state.id); s.setString(2, id.toString()); s.executeUpdate() == 1
        }

    private fun PreparedStatement.setAmount(i: Int, v: BigDecimal?) {
        if (v == null) setNull(i, Types.DECIMAL) else setBigDecimal(i, v)
    }

    // ── Transactions ─────────────────────────────────────────────────────────

    internal fun <T> tx(block: (Connection) -> T): T = ds.connection.use { c ->
        val auto = c.autoCommit
        c.autoCommit = false
        try {
            block(c).also { c.commit() }
        } catch (e: Throwable) {
            runCatching { c.rollback() }
            throw e
        } finally {
            runCatching { c.autoCommit = auto }
        }
    }

    internal fun <T> read(block: (Connection) -> T): T = ds.connection.use(block)
}
