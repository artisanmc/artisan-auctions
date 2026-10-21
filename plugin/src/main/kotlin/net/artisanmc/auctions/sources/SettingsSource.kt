package net.artisanmc.auctions.sources

import net.artisanmc.auctions.config.AuctionsConfig
import net.artisanmc.auctions.config.formatDuration
import net.artisanmc.modules.api.datasource.DataFetcher
import net.artisanmc.modules.api.datasource.DataSourceDeclaration
import net.artisanmc.modules.api.datasource.DataSourceField
import net.artisanmc.modules.api.model.ModelFieldType
import net.artisanmc.modules.api.datasource.Stability

/**
 * `auctions:settings`: one row describing what the house allows right now.
 * The default menus hide their type tabs and pick their dialogs with it, so the
 * same menus fit "sales only", "auctions only" and "both".
 */
object SettingsSource {
    const val REF = "auctions:settings"

    fun row(c: AuctionsConfig): Map<String, Any?> = mapOf(
        "id" to "settings",
        "sales_enabled" to c.sales.enabled,
        "auctions_enabled" to c.auctions.enabled,
        "both" to (c.sales.enabled && c.auctions.enabled),
        "read_only" to c.readOnly,
        "buyout" to (c.auctions.enabled && c.auctions.buyout),
        "currencies" to c.currencies.allowed,
        "default_currency" to c.currencies.default,
        "multi_currency" to (c.currencies.allowed.size > 1),
        "durations" to c.auctions.durations.map(::formatDuration),
        "default_duration" to formatDuration(c.auctions.defaultDuration),
        "sale_duration" to formatDuration(c.sales.duration),
        "max_active_listings" to c.limits.maxActiveListings,
    )

    fun declaration(config: () -> AuctionsConfig): DataSourceDeclaration = DataSourceDeclaration(
        REF,
        DataFetcher { _, _ -> listOf(row(config())) },
        listOf(
            DataSourceField("id", ModelFieldType.string(), "Always `settings`"),
            DataSourceField("sales_enabled", ModelFieldType.bool(), "Fixed-price sales can be created"),
            DataSourceField("auctions_enabled", ModelFieldType.bool(), "Auctions can be created"),
            DataSourceField("both", ModelFieldType.bool(), "Both kinds are enabled: show the type tabs"),
            DataSourceField("read_only", ModelFieldType.bool(), "Nothing can be listed; claims still work"),
            DataSourceField("buyout", ModelFieldType.bool(), "Auctions may carry a buy-now price"),
            DataSourceField("currencies", ModelFieldType.list(ModelFieldType.string()), "Currency ids a listing may use"),
            DataSourceField("default_currency", ModelFieldType.string(), "Currency used when the seller picks none"),
            DataSourceField("multi_currency", ModelFieldType.bool(), "More than one currency: ask the seller"),
            DataSourceField("durations", ModelFieldType.list(ModelFieldType.string()), "Auction durations offered, e.g. 24h"),
            DataSourceField("default_duration", ModelFieldType.string(), "Preselected auction duration"),
            DataSourceField("sale_duration", ModelFieldType.string(), "Lifetime of a fixed-price listing"),
            DataSourceField("max_active_listings", ModelFieldType.integer(), "Listings per player, before permissions"),
        ),
        Stability.STATIC,
        "id",
    )
}
