package net.artisanmc.auctions.median

import net.artisanmc.auctions.config.AuctionsConfig
import net.artisanmc.auctions.db.AuctionStore
import net.artisanmc.auctions.market.Threads
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/** A median and what it rests on. [price] is null below the minimum number of sales. */
data class MedianPrice(val price: BigDecimal?, val sales: Int, val windowDays: Int)

object MedianMath {
    /**
     * Median unit price of [sales] (newest first): at most [perSellerCap] sales
     * of one seller count, so a player selling to themselves cannot set the price.
     */
    fun of(sales: List<Pair<UUID, BigDecimal>>, perSellerCap: Int, minSales: Int, windowDays: Int): MedianPrice {
        val bySeller = HashMap<UUID, Int>()
        val kept = sales.filter { (seller, _) ->
            val n = bySeller.merge(seller, 1, Int::plus)!!
            perSellerCap <= 0 || n <= perSellerCap
        }.map { it.second }.sorted()
        if (kept.isEmpty() || kept.size < minSales) return MedianPrice(null, kept.size, windowDays)
        val mid = kept.size / 2
        val value = if (kept.size % 2 == 1) kept[mid] else (kept[mid - 1] + kept[mid]).divide(BigDecimal(2))
        return MedianPrice(value.setScale(2, RoundingMode.HALF_UP), kept.size, windowDays)
    }

    /** Signed percent of [price] against [median]: -20 = 20 % below. */
    fun ratio(price: BigDecimal, median: BigDecimal): Int =
        if (median.signum() == 0) 0 else ((price - median) * BigDecimal(100)).divide(median, 0, RoundingMode.HALF_UP).toInt()
}

/**
 * Medians by price key and currency, cached. [get] never blocks: a missing
 * entry is computed in the background and [loaded] is called when it lands.
 */
class MedianCache(
    private val store: AuctionStore,
    private val threads: Threads,
    private val config: () -> AuctionsConfig,
    private val logger: Logger,
    private val clock: () -> Long = System::currentTimeMillis,
    private val loaded: () -> Unit = {},
) {
    private data class Key(val priceKey: String, val currency: String)
    private class Entry(val median: MedianPrice, val at: Long)

    private val entries = object : LinkedHashMap<Key, Entry>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>) = size > MAX_ENTRIES
    }
    private val loading = HashSet<Key>()

    /** Main thread. Null while unknown (a computation is started). */
    fun get(priceKey: String, currency: String): MedianPrice? {
        val m = config().median
        if (!m.enabled) return null
        val key = Key(priceKey, currency)
        val e = entries[key]
        if (e != null && clock() - e.at < TTL_MS) return e.median
        load(key)
        return e?.median
    }

    /** A sale of this key landed: the next read recomputes. */
    fun invalidate(priceKey: String, currency: String) {
        entries.remove(Key(priceKey, currency))
    }

    fun clear() = entries.clear()

    private fun load(key: Key) {
        if (!loading.add(key)) return
        val m = config().median
        val custom = PriceKeys.isCustom(key.priceKey)
        val window = if (custom) m.customWindow else m.window
        val minSales = if (custom) m.customMinSales else m.minSales
        val since = clock() - window.days * DAY_MS
        threads.async {
            val result = runCatching {
                MedianMath.of(store.recentSales(key.priceKey, key.currency, since, window.maxSales, m.include), m.perSellerCap, minSales, window.days)
            }.onFailure { logger.log(Level.WARNING, "[Auctions] median of ${key.priceKey} failed", it) }.getOrNull()
            threads.main {
                loading -= key
                if (result != null) {
                    entries[key] = Entry(result, clock())
                    loaded()
                }
            }
        }
    }

    companion object {
        const val MAX_ENTRIES = 4096
        /** Old sales leave the window over time, even without a new sale. */
        const val TTL_MS = 10 * 60 * 1000L
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
