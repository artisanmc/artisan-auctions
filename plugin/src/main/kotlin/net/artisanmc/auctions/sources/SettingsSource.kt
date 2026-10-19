package net.artisanmc.auctions.sources

import net.artisanmc.auctions.config.AuctionsConfig
import net.artisanmc.auctions.config.formatDuration
import net.artisanmc.modules.api.datasource.DataFetcher
import net.artisanmc.modules.api.datasource.DataSourceDeclaration
import net.artisanmc.modules.api.datasource.DataSourceField
import net.artisanmc.modules.api.datasource.FieldKind
import net.artisanmc.modules.api.datasource.Stability

/**
 * `auctions:settings`: one row describing what the house allows right now.
 * The default menus hide their type tabs and pick their dialogs with it, so the
 * same Project fits "sales only", "auctions only" and "both".
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
            DataSourceField("id", FieldKind.STRING, "Always `settings`"),
            DataSourceField("sales_enabled", FieldKind.BOOLEAN, "Fixed-price sales can be created"),
            DataSourceField("auctions_enabled", FieldKind.BOOLEAN, "Auctions can be created"),
            DataSourceField("both", FieldKind.BOOLEAN, "Both kinds are enabled: show the type tabs"),
            DataSourceField("read_only", FieldKind.BOOLEAN, "Nothing can be listed; claims still work"),
            DataSourceField("buyout", FieldKind.BOOLEAN, "Auctions may carry a buy-now price"),
            DataSourceField("currencies", FieldKind.LIST, "Currency ids a listing may use"),
            DataSourceField("default_currency", FieldKind.STRING, "Currency used when the seller picks none"),
            DataSourceField("multi_currency", FieldKind.BOOLEAN, "More than one currency: ask the seller"),
            DataSourceField("durations", FieldKind.LIST, "Auction durations offered, e.g. 24h"),
            DataSourceField("default_duration", FieldKind.STRING, "Preselected auction duration"),
            DataSourceField("sale_duration", FieldKind.STRING, "Lifetime of a fixed-price listing"),
            DataSourceField("max_active_listings", FieldKind.INTEGER, "Listings per player, before permissions"),
        ),
        Stability.STATIC,
        "id",
    )
}
