package net.artisanmc.auctions.importer

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.artisanmc.auctions.market.ClaimReason
import java.io.Reader
import java.math.BigDecimal
import java.util.UUID

/**
 * The canonical import format of Auctions, `auctions/1` (ADR `data-imports` of Artisan, §4):
 * what any converter, hand-written or generated, produces for `/artisan import auctions:json <file>`.
 * Documented in `docs/site/recipes/import-file.md`. A format version is never changed, only added.
 *
 * ```json
 * {"format": "auctions/1", "source": "myplugin",
 *  "listings": [{"id": "1", "seller": "<uuid>", "seller_name": "Steve", "currency": "vault",
 *                "price": "12.50", "created_at": 1700000000000, "ends_at": 1700600000000,
 *                "items": [{"encoding": "bukkit_yaml", "data": "..."}]}],
 *  "claims":   [{"id": "2", "owner": "<uuid>", "reason": "expired", "created_at": 0, "items": [...]},
 *               {"id": "3", "owner": "<uuid>", "reason": "sold", "created_at": 0, "currency": "vault", "amount": "40"}],
 *  "sales":    [{"id": "4", "seller": "<uuid>", "buyer": "<uuid>", "currency": "vault", "total": "30",
 *                "sold_at": 0, "items": [...]}]}
 * ```
 *
 * Every value wrong is reported with its path and its record left aside; the rest reads.
 */
object CanonicalJson {
    const val FORMAT = "auctions/1"
    val ENCODINGS = setOf("bukkit_yaml", "snbt", "paper", "zah")
    private val SOURCE = Regex("[a-z0-9][a-z0-9_-]{0,39}")

    class Document(val source: String, val data: SourceData)

    class FormatException(message: String) : IllegalArgumentException(message)

    fun read(reader: Reader): Document {
        val root = runCatching { JsonParser.parseReader(reader) }.getOrElse { throw FormatException("not JSON: ${it.message}") }
        if (root !is JsonObject) throw FormatException("the file is not a JSON object")
        val format = root.str("format")
        if (format != FORMAT) throw FormatException("format is '$format', this version reads '$FORMAT'")
        val source = root.str("source")
        if (source == null || !SOURCE.matches(source)) throw FormatException("source must be a short id ([a-z0-9_-], 40 at most), got '$source'")
        val problems = mutableListOf<String>()
        val records = mutableListOf<SourceRecord>()
        fun each(key: String, read: (JsonObject, String) -> SourceRecord) {
            val array = root.get(key) ?: return
            if (array !is JsonArray) { problems += "$key: not an array"; return }
            array.forEachIndexed { i, e ->
                val path = "$key[$i]"
                if (e !is JsonObject) { problems += "$path: not an object"; return@forEachIndexed }
                try { records += read(e, path) } catch (x: FormatException) { problems += "$path: ${x.message}" }
            }
        }
        each("listings") { o, p ->
            SourceListing("listing:${o.req(p, "id")}", o.uuid(p, "seller"), o.str("seller_name") ?: "?", o.req(p, "currency"),
                o.money(p, "price"), o.long(p, "created_at"), o.long(p, "ends_at"), o.items(p))
        }
        each("claims") { o, p ->
            val reason = o.req(p, "reason").let { r -> ClaimReason.entries.firstOrNull { it.id == r } ?: throw FormatException("$p.reason: unknown '$r'") }
            val money = o.has("amount")
            SourceClaim("claim:${o.req(p, "id")}", o.uuid(p, "owner"), reason, o.long(p, "created_at"),
                items = if (money) emptyList() else o.items(p),
                currency = if (money) o.req(p, "currency") else null,
                amount = if (money) o.money(p, "amount") else null)
        }
        each("sales") { o, p ->
            SourceSale("sale:${o.req(p, "id")}", o.uuid(p, "seller"), o.str("buyer")?.let { uuid(it) ?: throw FormatException("$p.buyer: not a UUID") },
                o.req(p, "currency"), o.money(p, "total"), o.long(p, "sold_at"), o.items(p))
        }
        return Document(source, SourceData(records, problems))
    }

    private fun JsonObject.str(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.req(path: String, key: String): String = str(key)?.takeIf { it.isNotBlank() } ?: throw FormatException("$path.$key is required")
    private fun JsonObject.uuid(path: String, key: String): UUID = uuid(req(path, key)) ?: throw FormatException("$path.$key: not a UUID")
    private fun JsonObject.long(path: String, key: String): Long =
        get(key)?.let { runCatching { it.asLong }.getOrNull() } ?: throw FormatException("$path.$key: epoch milliseconds expected")
    private fun JsonObject.money(path: String, key: String): BigDecimal {
        val v = get(key)?.let { runCatching { BigDecimal(it.asString) }.getOrNull() } ?: throw FormatException("$path.$key: an amount expected")
        if (v.signum() < 0 || v.scale() > 2) throw FormatException("$path.$key: $v is negative or has more than two decimals")
        return v
    }
    private fun JsonObject.items(path: String): List<SourceItem> {
        val array = get("items") as? JsonArray ?: throw FormatException("$path.items: an array expected")
        if (array.size() == 0) throw FormatException("$path.items is empty")
        return array.mapIndexed { i, e: JsonElement ->
            val o = e as? JsonObject ?: throw FormatException("$path.items[$i]: not an object")
            val encoding = o.req("$path.items[$i]", "encoding")
            if (encoding !in ENCODINGS) throw FormatException("$path.items[$i].encoding: '$encoding' is not one of ${ENCODINGS.sorted()}")
            SourceItem(o.req("$path.items[$i]", "data"), encoding)
        }
    }
}
