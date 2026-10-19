package net.artisanmc.auctions.importer

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.artisanmc.auctions.market.ClaimReason
import java.io.File
import java.math.BigDecimal
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.UUID

/**
 * zAuctionHouse v4 (GroupeZ-dev/zAuctionHouse, MIT), read from its database.
 * Tables `<prefix>items`, `auction_items`, `players`, `transactions`, `logs`.
 * Only `AUCTION` items exist there (its BID and RENT types are not implemented).
 */
class ZahV4Reader(
    private val connect: () -> Connection,
    private val prefix: String,
    /** End given to listings that never expire (`expired_at` = 0). */
    private val defaultEnd: () -> Long,
) : AuctionSource {
    override val id = "zauctionhouse"

    override fun read(): SourceData = connect().use { c ->
        val problems = mutableListOf<String>()
        val names = HashMap<String, String>()
        c.createStatement().use { s ->
            s.executeQuery("SELECT unique_id, name FROM ${prefix}players").use { rs ->
                while (rs.next()) names[rs.getString(1).lowercase()] = rs.getString(2)
            }
        }
        val stacks = HashMap<Long, MutableList<SourceItem>>()
        c.createStatement().use { s ->
            s.executeQuery("SELECT item_id, itemstack FROM ${prefix}auction_items ORDER BY id").use { rs ->
                while (rs.next()) stacks.getOrPut(rs.getLong(1)) { mutableListOf() } += SourceItem(rs.getString(2))
            }
        }
        val records = mutableListOf<SourceRecord>()
        c.createStatement().use { s ->
            s.executeQuery("SELECT * FROM ${prefix}items").use { rs ->
                while (rs.next()) {
                    val id = rs.getLong("id")
                    val type = rs.getString("item_type")
                    val state = rs.getString("storage_type")?.uppercase()
                    val seller = uuid(rs.getString("seller_unique_id"))
                    if (!type.equals("AUCTION", true)) continue
                    if (seller == null) { problems += "item:$id: no seller, skipped"; continue }
                    val items = stacks[id].orEmpty()
                    if (items.isEmpty()) { if (state != "DELETED") problems += "item:$id: no item stored, skipped"; continue }
                    val economy = rs.getString("economy_name") ?: "vault"
                    val end = millis(rs, "expired_at")
                    val created = millis(rs, "created_at") ?: 0L
                    val publish = runCatching { rs.getObject("pending_publish") as? Number }.getOrNull()?.toInt()
                    when (state) {
                        "LISTED" -> records += SourceListing("item:$id", seller, names[seller.toString()] ?: "?", economy,
                            rs.getBigDecimal("price"), created, end?.takeIf { it > 0 } ?: defaultEnd(), items)
                        "PURCHASED" -> uuid(rs.getString("buyer_unique_id"))?.let { buyer ->
                            records += SourceClaim("item:$id", buyer, ClaimReason.BOUGHT, created, items)
                        } ?: run { problems += "item:$id: purchased without a buyer, skipped" }
                        "EXPIRED" -> records += SourceClaim("item:$id", seller, ClaimReason.EXPIRED, created, items)
                        // Taken from the seller by a sale that never completed: theirs again.
                        "DELETED" -> if (publish == 2) records += SourceClaim("item:$id", seller, ClaimReason.EXPIRED, created, items)
                    }
                }
            }
        }
        c.createStatement().use { s ->
            s.executeQuery("SELECT * FROM ${prefix}transactions WHERE status = 'PENDING'").use { rs ->
                while (rs.next()) {
                    val owner = uuid(rs.getString("player_unique_id")) ?: continue
                    val value = rs.getBigDecimal("value") ?: continue
                    if (value.signum() <= 0) continue
                    records += SourceClaim("tx:${rs.getLong("id")}", owner, ClaimReason.SOLD, millis(rs, "created_at") ?: 0L,
                        currency = rs.getString("economy_name") ?: "vault", amount = value)
                }
            }
        }
        c.createStatement().use { s ->
            s.executeQuery("SELECT id, player_unique_id, target_unique_id, itemstack, price, economy_name, created_at " +
                "FROM ${prefix}logs WHERE log_type = 'PURCHASE'").use { rs ->
                while (rs.next()) {
                    val buyer = uuid(rs.getString("player_unique_id"))
                    val seller = uuid(rs.getString("target_unique_id")) ?: continue
                    val raw = rs.getString("itemstack") ?: continue
                    records += SourceSale("log:${rs.getLong("id")}", seller, buyer, rs.getString("economy_name") ?: "vault",
                        rs.getBigDecimal("price") ?: BigDecimal.ZERO, millis(rs, "created_at") ?: 0L, split(raw))
                }
            }
        }
        SourceData(records, problems)
    }
}

/**
 * zAuctionHouse v3, from its SQL database (`<prefix>items`, `<prefix>transactions`)
 * or its JSON files, in the shapes zAuctionHouse v4's own migration reads.
 */
class ZahV3Reader private constructor(
    private val sql: (() -> Connection)?,
    private val prefix: String,
    private val folder: File?,
    private val defaultEnd: () -> Long,
) : AuctionSource {
    override val id = "zauctionhouse-v3"

    override fun read(): SourceData = if (sql != null) readSql(sql) else readJson(folder!!)

    private data class Row(
        val id: String, val payload: String?, val price: BigDecimal, val seller: UUID?, val buyer: UUID?, val economy: String,
        val expireAt: Long, val state: String, val sellerName: String?,
    )

    private data class Tx(
        val id: String, val seller: UUID?, val buyer: UUID?, val payload: String?, val date: Long, val price: BigDecimal,
        val economy: String, val needMoney: Boolean,
    )

    private fun convert(items: List<Row>, txs: List<Tx>, problems: MutableList<String>): SourceData {
        val records = mutableListOf<SourceRecord>()
        for (r in items) {
            val seller = r.seller ?: run { problems += "v3item:${r.id}: no seller, skipped"; null } ?: continue
            val stacks = r.payload?.let(::split).orEmpty()
            if (stacks.isEmpty()) { problems += "v3item:${r.id}: no item stored, skipped"; continue }
            when (r.state) {
                "BUY" -> r.buyer?.let { records += SourceClaim("v3item:${r.id}", it, ClaimReason.BOUGHT, 0, stacks) }
                    ?: run { problems += "v3item:${r.id}: bought without a buyer, skipped" }
                "EXPIRE" -> records += SourceClaim("v3item:${r.id}", seller, ClaimReason.EXPIRED, 0, stacks)
                else -> records += SourceListing("v3item:${r.id}", seller, r.sellerName ?: "?", r.economy, r.price,
                    0, r.expireAt.takeIf { it > 0 } ?: defaultEnd(), stacks)
            }
        }
        for (t in txs) {
            val seller = t.seller ?: continue
            if (t.needMoney && t.price.signum() > 0) {
                records += SourceClaim("v3tx:${t.id}", seller, ClaimReason.SOLD, t.date, currency = t.economy, amount = t.price)
            }
            t.payload?.let { records += SourceSale("v3sale:${t.id}", seller, t.buyer, t.economy, t.price, t.date, split(it)) }
        }
        return SourceData(records, problems)
    }

    private fun readSql(connect: () -> Connection): SourceData = connect().use { c ->
        val items = mutableListOf<Row>()
        c.createStatement().use { s ->
            s.executeQuery("SELECT * FROM ${prefix}items").use { rs ->
                while (rs.next()) items += Row(
                    rs.getString("id"), rs.getString("itemstack"), BigDecimal.valueOf(rs.getLong("price")),
                    uuid(rs.getString("seller")), uuid(rs.getString("buyer")), rs.getString("economy") ?: "VAULT",
                    millis(rs, "expire_at") ?: 0L, (rs.getString("storage_type") ?: "STORAGE").uppercase(),
                    runCatching { rs.getString("sellerName") }.getOrNull(),
                )
            }
        }
        val txs = mutableListOf<Tx>()
        c.createStatement().use { s ->
            s.executeQuery("SELECT * FROM ${prefix}transactions").use { rs ->
                while (rs.next()) txs += Tx(
                    rs.getString("id"), uuid(rs.getString("seller")), uuid(rs.getString("buyer")), rs.getString("itemstack"),
                    millis(rs, "transaction_date") ?: 0L, BigDecimal.valueOf(rs.getLong("price")),
                    rs.getString("economy") ?: "VAULT", rs.getBoolean("need_money"),
                )
            }
        }
        convert(items, txs, mutableListOf())
    }

    private fun readJson(dir: File): SourceData {
        val problems = mutableListOf<String>()
        fun array(name: String): JsonArray? = File(dir, name).takeIf { it.isFile }?.let { f ->
            runCatching { JsonParser.parseString(f.readText()).asJsonArray }
                .onFailure { problems += "$name could not be read: ${it.message}" }.getOrNull()
        }
        val files = listOf("items.json" to "STORAGE", "buying_items.json" to "BUY", "expired_items.json" to "EXPIRE")
        var sources = files.mapNotNull { (f, state) -> array(f)?.let { it to state } }
        if (sources.all { it.first.isEmpty }) {
            File(dir, "data.json").takeIf { it.isFile }?.let { f ->
                val o = runCatching { JsonParser.parseString(f.readText()).asJsonObject }.getOrNull()
                if (o != null) sources = listOf("items" to "STORAGE", "buyingItems" to "BUY", "expiredItems" to "EXPIRE")
                    .mapNotNull { (k, state) -> o.getAsJsonArray(k)?.let { it to state } }
            }
        }
        val items = sources.flatMap { (arr, state) ->
            arr.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                Row(
                    str(o, "id", "uniqueId") ?: return@mapNotNull null, str(o, "itemstack", "itemStack"),
                    num(o, "price"), uuid(str(o, "seller", "sellerUniqueId")), uuid(str(o, "buyer", "buyerUniqueId")),
                    str(o, "economy") ?: "VAULT", num(o, "expireAt", "expire_at").toLong(),
                    (str(o, "storageType", "storage_type") ?: state).uppercase(), str(o, "sellerName"),
                )
            }
        }
        val txs = array("transactions.json").orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            Tx(
                str(o, "id") ?: return@mapNotNull null, uuid(str(o, "seller", "sellerUniqueId")), uuid(str(o, "buyer", "buyerUniqueId")),
                str(o, "itemstack", "itemStack"), num(o, "transactionDate", "transaction_date", "date").toLong(), num(o, "price"),
                str(o, "economy") ?: "VAULT", bool(o, "needMoney", "need_money"),
            )
        }
        return convert(items, txs, problems)
    }

    companion object {
        fun sql(connect: () -> Connection, prefix: String, defaultEnd: () -> Long) = ZahV3Reader(connect, prefix, null, defaultEnd)
        fun json(folder: File, defaultEnd: () -> Long) = ZahV3Reader(null, "", folder, defaultEnd)

        private fun field(o: JsonObject, vararg names: String): JsonElement? =
            names.firstNotNullOfOrNull { n -> o.get(n)?.takeUnless { it.isJsonNull } }

        private fun str(o: JsonObject, vararg names: String) = field(o, *names)?.asString
        private fun num(o: JsonObject, vararg names: String): BigDecimal = field(o, *names)?.asBigDecimal ?: BigDecimal.ZERO
        private fun bool(o: JsonObject, vararg names: String) = field(o, *names)?.asBoolean ?: false
    }
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray()

/** Several stacks of one listing are stored joined by `;` (Base64 never contains it). */
internal fun split(raw: String): List<SourceItem> = raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }.map(::SourceItem)

internal fun uuid(s: String?): UUID? = s?.takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { UUID.fromString(it) }.getOrNull() }

/** Timestamps: epoch millis, a SQL timestamp, or `yyyy-MM-dd HH:mm:ss` text, depending on the driver. */
internal fun millis(rs: ResultSet, column: String): Long? = when (val v = runCatching { rs.getObject(column) }.getOrNull()) {
    null -> null
    is Number -> v.toLong()
    is Timestamp -> v.time
    is java.util.Date -> v.time
    is java.time.LocalDateTime -> Timestamp.valueOf(v).time
    else -> v.toString().trim().let { t ->
        t.toLongOrNull() ?: runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(t.take(19)).time }.getOrNull()
    }
}
