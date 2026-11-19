package net.artisanmc.auctions.importer

import net.artisanmc.auctions.market.ClaimReason
import java.math.BigDecimal
import java.sql.Connection
import java.sql.ResultSet

/**
 * AuctionHouse by Kiranhart / Tweetzy (`kiranhart/Auction-House`), read from its database:
 * `<prefix>auctions` (listings, and with `expired` the owners' collection bins), `<prefix>payments`
 * (money waiting for a player) and `<prefix>transactions` (sales history). SQLite in
 * `plugins/AuctionHouse/auctionhouse.db` with the prefix `auctionhouse_`, or MySQL with
 * `database.table prefix` of its config.
 *
 * Its items are Bukkit's YAML of an `ItemStack` under `i` (`serialize_version` 0, column `item`)
 * or the item's NBT as text (`serialize_version` 1, column `itemstack`).
 *
 * Left in the source, reported: a listing someone bid on (the bid's money is AuctionHouse's;
 * let those auctions end first), buy requests, the server's own and infinite listings, and amounts
 * paid in an item currency. An auction with no buy-now price cannot be a fixed-price listing
 * here: its item goes back to the seller's claims.
 */
class AuctionHouseReader(
    private val connect: () -> Connection,
    private val prefix: String,
) : AuctionSource {
    override val id = "auctionhouse"

    override fun read(): SourceData = connect().use { c ->
        val problems = mutableListOf<String>()
        val rejected = mutableListOf<SourceRejection>()
        val records = mutableListOf<SourceRecord>()

        c.createStatement().use { s ->
            s.executeQuery("SELECT * FROM ${prefix}auctions").use { rs ->
                val cols = columns(rs)
                while (rs.next()) {
                    val id = "auction:${rs.getString("id")}"
                    val owner = uuid(rs.getString("owner"))
                    if (owner == null) { problems += "$id: no owner, skipped"; continue }
                    if (flag(rs, cols, "is_request")) { rejected += SourceRejection(id, "buy_request", "buy requests are not imported"); continue }
                    if (flag(rs, cols, "server_auction") || flag(rs, cols, "infinite")) {
                        rejected += SourceRejection(id, "server_listing", "listings of the server itself are not imported"); continue
                    }
                    val item = item(rs, cols) ?: run { problems += "$id: no item stored, skipped"; null } ?: continue
                    val currency = currency(rs, cols)
                    if (currency == null) { rejected += SourceRejection(id, "item_currency", "priced in an item currency"); continue }
                    val created = if ("created_at" in cols) rs.getLong("created_at").takeIf { it > 0 } ?: 0L else 0L
                    if (rs.getBoolean("expired")) {
                        // In the owner's collection bin: expired, cancelled, or won (the winner is then the owner).
                        records += SourceClaim(id, owner, ClaimReason.IMPORTED, created, listOf(item))
                        continue
                    }
                    val bidder = uuid(rs.getString("highest_bidder"))
                    if (bidder != null && bidder != owner) {
                        rejected += SourceRejection(id, "active_bid", "an auction someone bid on: let it end in AuctionHouse first"); continue
                    }
                    val buyNow = rs.getBigDecimal("base_price")
                    if (buyNow == null || buyNow.signum() <= 0) {
                        records += SourceClaim(id, owner, ClaimReason.EXPIRED, created, listOf(item))
                        problems += "$id: an auction without a buy-now price, its item goes back to the seller"
                        continue
                    }
                    records += SourceListing(id, owner, rs.getString("owner_name") ?: "?", currency, buyNow, created,
                        rs.getLong("expires_at"), listOf(item))
                }
            }
        }

        if (exists(c, "payments")) c.createStatement().use { s ->
            s.executeQuery("SELECT * FROM ${prefix}payments").use { rs ->
                val cols = columns(rs)
                while (rs.next()) {
                    val id = "payment:${rs.getString("uuid")}"
                    val owner = uuid(rs.getString("payment_for")) ?: run { problems += "$id: no player, skipped"; null } ?: continue
                    val currency = currency(rs, cols)
                    if (currency == null) { rejected += SourceRejection(id, "item_currency", "paid in an item currency"); continue }
                    val amount = rs.getBigDecimal("amount") ?: continue
                    if (amount.signum() <= 0) continue
                    records += SourceClaim(id, owner, ClaimReason.SOLD, rs.getLong("time"), currency = currency, amount = amount)
                }
            }
        }

        if (exists(c, "transactions")) c.createStatement().use { s ->
            s.executeQuery("SELECT * FROM ${prefix}transactions").use { rs ->
                val cols = columns(rs)
                while (rs.next()) {
                    val id = "transaction:${rs.getString("id")}"
                    val seller = uuid(rs.getString("seller")) ?: continue
                    val item = item(rs, cols) ?: continue
                    val price = rs.getBigDecimal("final_price") ?: continue
                    // AuctionHouse keeps no currency with a sale: its default, Vault.
                    records += SourceSale(id, seller, uuid(rs.getString("buyer")), VAULT, price, rs.getLong("transaction_time"), listOf(item))
                }
            }
        }
        SourceData(records, problems, rejected)
    }

    private fun item(rs: ResultSet, cols: Set<String>): SourceItem? {
        val version = if ("serialize_version" in cols) rs.getInt("serialize_version") else 0
        if (version == 1 && "itemstack" in cols) rs.getString("itemstack")?.takeIf { it.isNotBlank() }?.let { return SourceItem(it, "snbt") }
        return rs.getString("item")?.takeIf { it.isNotBlank() }?.let { SourceItem(it, "bukkit_yaml") }
    }

    /** `Vault/Vault` → `vault`, `CoinsEngine/gems` → `gems`; null for an item currency. */
    private fun currency(rs: ResultSet, cols: Set<String>): String? {
        if ("currency_item" in cols && !rs.getString("currency_item").isNullOrBlank()) return null
        val raw = if ("currency" in cols) rs.getString("currency") else null
        return normalizeCurrency(raw)
    }

    private fun flag(rs: ResultSet, cols: Set<String>, column: String) = column in cols && rs.getBoolean(column)

    private fun columns(rs: ResultSet): Set<String> {
        val meta = rs.metaData
        return (1..meta.columnCount).map { meta.getColumnLabel(it).lowercase() }.toSet()
    }

    private fun exists(c: Connection, table: String): Boolean = runCatching {
        c.createStatement().use { it.executeQuery("SELECT 1 FROM $prefix$table WHERE 1 = 0").close() }
    }.isSuccess

    companion object {
        const val VAULT = "vault"
        fun normalizeCurrency(raw: String?): String {
            if (raw.isNullOrBlank()) return VAULT
            val name = raw.substringAfter('/').ifBlank { raw }
            return if (raw.equals("Vault/Vault", true)) VAULT else name
        }
    }
}
