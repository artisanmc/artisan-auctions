package net.artisanmc.auctions.paper

import net.artisanmc.auctions.stats.MarketFigures
import net.artisanmc.auctions.stats.MarketStats
import net.artisanmc.auctions.stats.StatSink
import net.artisanmc.modules.api.ArtisanAPI
import net.artisanmc.modules.api.stats.StatEvent
import net.artisanmc.modules.api.stats.StatStorage
import net.artisanmc.modules.api.stats.StatsApi
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The house in Artisan's statistics, from API [MIN_API] (`StatsApi`). Only this class names
 * the stats API: on an older Artisan it is never loaded and the house runs without statistics.
 */
object ArtisanStats {
    const val MIN_API = "1.5.0"
    /** The market moves slowly next to an admin reading it; the read walks the claims table. */
    private const val CACHE_SECONDS = 30

    fun declarations(): List<StatEvent> = listOf(
        StatEvent.builder(MarketStats.LISTED).storage(StatStorage.BUCKETED).dimensions("type", "currency", "item").value("price").build(),
        StatEvent.builder(MarketStats.SOLD).storage(StatStorage.BOTH).dimensions("type", "currency", "item").value("price").build(),
        StatEvent.builder(MarketStats.BID).storage(StatStorage.BUCKETED).dimensions("currency", "item").value("amount").build(),
        StatEvent.builder(MarketStats.EXPIRED).storage(StatStorage.BUCKETED).dimensions("type", "currency", "item").build(),
        StatEvent.builder(MarketStats.CANCELLED).storage(StatStorage.BUCKETED).dimensions("type", "currency", "item", "by").build(),
        StatEvent.builder(MarketStats.REVENUE).storage(StatStorage.BUCKETED).dimensions("currency", "kind").value("amount").build(),
    )

    /** Declares the events and the market figure; the sink to record with, or null when Artisan refuses. */
    fun install(api: ArtisanAPI, house: AuctionHouse, logger: Logger): StatSink? {
        if (!api.isCompatibleWith(MIN_API)) return null
        val stats: StatsApi = api.stats
        return try {
            declarations().forEach(stats::declare)
            stats.declareQuery(MarketStats.MARKET, CACHE_SECONDS) { _ ->
                val (items, money) = house.store.waitingClaims(house.server)
                MarketFigures.rows(house.cache.all().toList(), items, money)
            }
            StatSink { event, player, dimensions, value, details -> stats.record(event, player, dimensions, value, details) }
        } catch (e: RuntimeException) {
            logger.log(Level.WARNING, "[Auctions] statistics are off: Artisan refused them", e)
            null
        }
    }
}
