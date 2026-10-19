package net.artisanmc.auctions.config

import java.math.BigDecimal
import java.time.Duration

/**
 * Reads `data/config.yaml` (already parsed to maps). A missing key takes its
 * default; a present but unreadable one is an error. The caller keeps its last
 * valid config when [Result.errors] is not empty: a typo in a hand-edited file
 * never turns the house off.
 */
object ConfigParser {

    data class Result(val config: AuctionsConfig, val errors: List<String>)

    fun parse(root: Map<*, *>?): Result {
        val r = Reader(root ?: emptyMap<String, Any?>(), "", mutableListOf())
        val d = AuctionsConfig()

        val sales = r.section("sales").let { s ->
            AuctionsConfig.Sales(
                enabled = s.bool("enabled", d.sales.enabled),
                duration = s.duration("duration", d.sales.duration),
                minPrice = s.amount("min_price", d.sales.minPrice),
                maxPrice = s.amount("max_price", d.sales.maxPrice),
            )
        }
        if (sales.minPrice > sales.maxPrice) r.error("sales.min_price is above sales.max_price")

        val auctions = r.section("auctions").let { s ->
            val durations = s.durations("durations", d.auctions.durations)
            val default = s.duration("default_duration", d.auctions.defaultDuration)
            if (durations.isNotEmpty() && default !in durations) {
                s.error("default_duration", "must be one of auctions.durations")
            }
            AuctionsConfig.Auctions(
                enabled = s.bool("enabled", d.auctions.enabled),
                durations = durations,
                defaultDuration = default,
                minIncrement = s.section("min_increment").let { i ->
                    AuctionsConfig.Increment(
                        percent = i.amount("percent", d.auctions.minIncrement.percent),
                        min = i.amount("min", d.auctions.minIncrement.min),
                    )
                },
                antiSnipe = s.section("anti_snipe").let { a ->
                    AuctionsConfig.AntiSnipe(
                        window = a.duration("window", d.auctions.antiSnipe.window),
                        extend = a.duration("extend", d.auctions.antiSnipe.extend),
                    )
                },
                buyout = s.bool("buyout", d.auctions.buyout),
            )
        }
        if (auctions.durations.isEmpty()) r.error("auctions.durations is empty")

        val limits = r.section("limits").let { s ->
            AuctionsConfig.Limits(
                maxActiveListings = s.int("max_active_listings", d.limits.maxActiveListings, min = 0),
                listingFee = s.fee("listing_fee", d.limits.listingFee),
                saleTax = s.fee("sale_tax", d.limits.saleTax),
                cancelAuctionWithBids = s.bool("cancel_auction_with_bids", d.limits.cancelAuctionWithBids),
            )
        }

        val currencies = r.section("currencies").let { s ->
            val allowed = s.strings("allowed", d.currencies.allowed).distinct()
            val default = s.string("default", allowed.firstOrNull() ?: d.currencies.default)
            if (allowed.isEmpty()) s.error("allowed", "lists no currency")
            else if (default !in allowed) s.error("default", "'$default' is not in currencies.allowed")
            AuctionsConfig.Currencies(allowed, default, s.stringMap("display", d.currencies.display))
        }

        val items = r.section("items").let { s ->
            AuctionsConfig.Items(
                blacklist = s.strings("blacklist", d.items.blacklist).map { it.uppercase() },
                blacklistLoreContains = s.strings("blacklist_lore_contains", d.items.blacklistLoreContains),
            )
        }

        val claims = r.section("claims").let { s ->
            AuctionsConfig.Claims(
                retention = s.duration("retention", d.claims.retention),
                deliverDirectly = s.bool("deliver_directly", d.claims.deliverDirectly),
            )
        }

        val median = r.section("median").let { s ->
            val m = d.median
            AuctionsConfig.Median(
                enabled = s.bool("enabled", m.enabled),
                window = s.window("window", m.window),
                minSales = s.int("min_sales", m.minSales, min = 1),
                key = s.choice("key", m.key, setOf("auto", "vanilla")),
                customIdKeys = s.strings("custom_id_keys", m.customIdKeys),
                loreIgnore = s.regexes("lore_ignore"),
                customIncludeEnchants = s.bool("custom_include_enchants", m.customIncludeEnchants),
                attributes = s.choice("attributes", m.attributes, setOf("ignore", "keys", "values")),
                customWindow = s.window("custom_window", m.customWindow),
                customMinSales = s.int("custom_min_sales", m.customMinSales, min = 1),
                include = s.strings("include", m.include.toList()).toSet().also { inc ->
                    val unknown = inc - setOf("sale", "auction")
                    if (unknown.isNotEmpty()) s.error("include", "unknown type(s) ${unknown.joinToString()}")
                },
                perSellerCap = s.int("per_seller_cap", m.perSellerCap, min = 1),
                suggestOnSell = s.bool("suggest_on_sell", m.suggestOnSell),
                warnBelow = s.amount("warn_below", m.warnBelow),
                warnAbove = s.amount("warn_above", m.warnAbove),
            )
        }

        val import = AuctionsConfig.Import(r.section("import").stringMap("currency_map", d.import.currencyMap))

        return Result(AuctionsConfig(sales, auctions, limits, currencies, items, claims, median, import), r.errors)
    }

    private class Reader(val map: Map<*, *>, val path: String, val errors: MutableList<String>) {
        private fun key(k: String) = if (path.isEmpty()) k else "$path.$k"

        fun error(message: String) { errors += message }
        fun error(k: String, message: String) { errors += "${key(k)} $message" }

        fun section(k: String): Reader {
            val v = map[k]
            if (v != null && v !is Map<*, *>) error(k, "must be a section")
            return Reader(v as? Map<*, *> ?: emptyMap<String, Any?>(), key(k), errors)
        }

        fun bool(k: String, def: Boolean): Boolean = when (val v = map[k]) {
            null -> def
            is Boolean -> v
            else -> def.also { error(k, "must be true or false") }
        }

        fun int(k: String, def: Int, min: Int): Int {
            val v = map[k] ?: return def
            val n = (v as? Number)?.toInt()?.takeIf { (v as? Number)?.toDouble() == it.toDouble() }
                ?: return def.also { error(k, "must be a whole number") }
            return if (n < min) def.also { error(k, "must be at least $min") } else n
        }

        fun amount(k: String, def: BigDecimal): BigDecimal {
            val v = map[k] ?: return def
            val n = when (v) {
                is Number, is String -> v.toString().trim().toBigDecimalOrNull()
                else -> null
            } ?: return def.also { error(k, "must be a number") }
            return if (n.signum() < 0) def.also { error(k, "must not be negative") } else n
        }

        fun string(k: String, def: String): String = when (val v = map[k]) {
            null -> def
            is String -> v.trim().ifEmpty { def }
            else -> v.toString()
        }

        fun choice(k: String, def: String, allowed: Set<String>): String {
            val v = string(k, def).lowercase()
            return if (v in allowed) v else def.also { error(k, "must be one of ${allowed.joinToString()}") }
        }

        fun strings(k: String, def: List<String>): List<String> = when (val v = map[k]) {
            null -> def
            is List<*> -> v.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            else -> def.also { error(k, "must be a list") }
        }

        fun stringMap(k: String, def: Map<String, String>): Map<String, String> = when (val v = map[k]) {
            null -> def
            is Map<*, *> -> v.entries.associate { (key, value) -> key.toString() to value.toString() }
            else -> def.also { error(k, "must be a section") }
        }

        fun duration(k: String, def: Duration): Duration {
            val v = map[k] ?: return def
            return parseDuration(v.toString()) ?: def.also { error(k, "'$v' is not a duration (e.g. 90s, 15m, 48h, 30d)") }
        }

        fun durations(k: String, def: List<Duration>): List<Duration> {
            val v = map[k] ?: return def
            if (v !is List<*>) return def.also { error(k, "must be a list") }
            return v.mapNotNull { raw ->
                parseDuration(raw.toString()) ?: null.also { error(k, "'$raw' is not a duration") }
            }.distinct().sorted()
        }

        fun fee(k: String, def: AuctionsConfig.Fee): AuctionsConfig.Fee = section(k).let { s ->
            AuctionsConfig.Fee(s.amount("percent", def.percent), s.amount("flat", def.flat))
        }

        fun window(k: String, def: AuctionsConfig.Window): AuctionsConfig.Window = section(k).let { s ->
            AuctionsConfig.Window(s.int("days", def.days, min = 1), s.int("max_sales", def.maxSales, min = 1))
        }

        fun regexes(k: String): List<Regex> = strings(k, emptyList()).mapNotNull { p ->
            runCatching { Regex(p) }.getOrElse { null.also { error(k, "'$p' is not a valid regex") } }
        }
    }
}
