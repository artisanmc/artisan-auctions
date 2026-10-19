package net.artisanmc.auctions.config

import java.math.BigDecimal
import java.time.Duration

/**
 * `data/config.yaml` of the add-on's Project, edited by the "Settings" config
 * screen (model `auctions:config`). Every key is optional: a missing one takes
 * the default below, which is also what the bootstrap file contains.
 */
data class AuctionsConfig(
    val sales: Sales = Sales(),
    val auctions: Auctions = Auctions(),
    val limits: Limits = Limits(),
    val currencies: Currencies = Currencies(),
    val items: Items = Items(),
    val claims: Claims = Claims(),
    val median: Median = Median(),
) {
    data class Sales(
        val enabled: Boolean = true,
        val duration: Duration = Duration.ofHours(48),
        val minPrice: BigDecimal = BigDecimal.ONE,
        val maxPrice: BigDecimal = BigDecimal("1000000000"),
    )

    data class Auctions(
        val enabled: Boolean = true,
        val durations: List<Duration> = listOf(1, 6, 12, 24, 48).map { Duration.ofHours(it.toLong()) },
        val defaultDuration: Duration = Duration.ofHours(24),
        val minIncrement: Increment = Increment(),
        val antiSnipe: AntiSnipe = AntiSnipe(),
        val buyout: Boolean = false,
    )

    /** Next bid ≥ current + max(current × percent / 100, min). */
    data class Increment(val percent: BigDecimal = BigDecimal(5), val min: BigDecimal = BigDecimal.ONE)

    /** A bid in the last [window] pushes the end back to at least now + [extend]. */
    data class AntiSnipe(val window: Duration = Duration.ofSeconds(60), val extend: Duration = Duration.ofSeconds(60))

    data class Limits(
        /** Raised per player by the permission `auctions.limit.<n>`. */
        val maxActiveListings: Int = 10,
        val listingFee: Fee = Fee(),
        val saleTax: Fee = Fee(percent = BigDecimal(5)),
        val cancelAuctionWithBids: Boolean = true,
    )

    data class Fee(val percent: BigDecimal = BigDecimal.ZERO, val flat: BigDecimal = BigDecimal.ZERO)

    /** Ids of the core currency registry (`api.economy`). */
    data class Currencies(val allowed: List<String> = listOf("vault"), val default: String = "vault")

    data class Items(
        val blacklist: List<String> = listOf("BEDROCK", "BARRIER", "COMMAND_BLOCK"),
        val blacklistLoreContains: List<String> = emptyList(),
    )

    data class Claims(
        val retention: Duration = Duration.ofDays(30),
        /** Hand bought items straight to an online buyer with room, instead of the claims box. */
        val deliverDirectly: Boolean = true,
    )

    data class Median(
        val enabled: Boolean = true,
        val window: Window = Window(days = 14, maxSales = 100),
        val minSales: Int = 5,
        /** `auto` (custom id, item_name + lore, custom_name + lore, vanilla) or `vanilla` only. */
        val key: String = "auto",
        val customIdKeys: List<String> = emptyList(),
        val loreIgnore: List<Regex> = emptyList(),
        val customIncludeEnchants: Boolean = false,
        /** `ignore`, `keys` or `values`: how PDC attributes enter the key of a custom item. */
        val attributes: String = "ignore",
        val customWindow: Window = Window(days = 90, maxSales = 50),
        val customMinSales: Int = 3,
        val include: Set<String> = setOf("sale", "auction"),
        val perSellerCap: Int = 3,
        val suggestOnSell: Boolean = true,
        val warnBelow: BigDecimal = BigDecimal("0.5"),
        val warnAbove: BigDecimal = BigDecimal("3.0"),
    )

    data class Window(val days: Int, val maxSales: Int)

    /** Both off = read-only house: nothing new is listed, claims still work. */
    val readOnly: Boolean get() = !sales.enabled && !auctions.enabled
}
