package net.artisanmc.auctions.config

import net.artisanmc.modules.api.model.ModelDeclaration
import net.artisanmc.modules.api.model.ModelField
import net.artisanmc.modules.api.model.ModelFieldType
import net.artisanmc.modules.api.model.ModelFieldType.bool as boolType
import net.artisanmc.modules.api.model.ModelFieldType.enumOf
import net.artisanmc.modules.api.model.ModelFieldType.integer
import net.artisanmc.modules.api.model.ModelFieldType.list
import net.artisanmc.modules.api.model.ModelFieldType.map
import net.artisanmc.modules.api.model.ModelFieldType.string

/**
 * `auctions:config` (model/v1): the shape of `data/config.yaml`, declared to the
 * editor so the "Settings" screen renders a form. Field names are the YAML keys
 * [ConfigParser] reads; ConfigModelTest keeps both in step.
 */
object ConfigModel {
    const val ID = "auctions:config"
    const val VERSION = 1

    private const val DURATION = "^(\\d+\\s*[smhd]\\s*)+$"

    fun declaration(): ModelDeclaration = ModelDeclaration(ID, "id", listOf(
            obj("sales", "Fixed-price sales",
                bool("enabled", "Enabled", true),
                duration("duration", "Listing lifetime", "48h", "Time before an unsold item goes back to the seller's claims."),
                num("min_price", "Minimum price", 1),
                num("max_price", "Maximum price", 1_000_000_000),
            ),
            obj("auctions", "Auctions",
                bool("enabled", "Enabled", true),
                field("durations", list(string()), "Durations offered",
                    help = "Choices shown to the seller, e.g. 1h, 6h, 24h.", default = listOf("1h", "6h", "12h", "24h", "48h")),
                duration("default_duration", "Default duration", "24h", "Must be one of the durations offered."),
                obj("min_increment", "Minimum raise",
                    num("percent", "Percent of the current bid", 5),
                    num("min", "At least", 1),
                ),
                obj("anti_snipe", "Anti-snipe",
                    duration("window", "Last minutes window", "60s", "A bid this close to the end extends the auction."),
                    duration("extend", "Extend to", "60s"),
                ),
                bool("buyout", "Buy-now price", false, "Lets the seller add an optional buy-now price."),
            ),
            obj("limits", "Limits and fees",
                field("max_active_listings", integer(), "Active listings per player",
                    help = "Raised per player with the permission auctions.limit.<n>.", default = 10, min = 0),
                obj("listing_fee", "Listing fee (kept)",
                    num("percent", "Percent", 0),
                    num("flat", "Flat", 0),
                ),
                obj("sale_tax", "Sale tax (paid by the seller)",
                    num("percent", "Percent", 5),
                    num("flat", "Flat", 0),
                ),
                bool("cancel_auction_with_bids", "Seller may cancel an auction with bids", true, "Bids are refunded."),
            ),
            obj("currencies", "Currencies",
                field("allowed", list(string()), "Allowed currencies",
                    help = "vault (Vault) or the id of a currency another plugin registers.", default = listOf("vault")),
                field("default", string(), "Default currency", default = "vault"),
                field("display", map(string()), "How amounts are written",
                    help = "Per currency id; {amount} is replaced, e.g. vault: \"{amount} \$\".", default = mapOf("vault" to "{amount} \$")),
            ),
            obj("items", "Items",
                field("blacklist", list(string()), "Forbidden materials",
                    default = listOf("BEDROCK", "BARRIER", "COMMAND_BLOCK")),
                field("blacklist_lore_contains", list(string()),
                    "Forbidden if the lore contains", default = emptyList<String>()),
            ),
            obj("claims", "Claims box",
                duration("retention", "Kept for", "30d", "Unclaimed items older than this are purged (logged)."),
                bool("deliver_directly", "Give bought items directly when there is room", true),
            ),
            obj("median", "Median price",
                bool("enabled", "Enabled", true),
                window("window", "Window", 14, 100),
                field("min_sales", integer(), "Sales needed to show it", default = 5, min = 1),
                field("key", enumOf(listOf("auto", "vanilla")), "Item identity",
                    help = "auto: custom id, then name + lore, then material. vanilla: material and enchantments only.", default = "auto"),
                field("custom_id_keys", list(string()), "Extra PDC id keys",
                    help = "Besides Nexo, Oraxen and ItemsAdder, e.g. myplugin:item_id.", default = emptyList<String>()),
                field("lore_ignore", list(string()), "Lore lines to ignore (regex)",
                    help = "Lines that differ from one copy to another: durability, owner, kills.", default = emptyList<String>()),
                bool("custom_include_enchants", "Enchantments change a custom item's identity", false),
                field("attributes", enumOf(listOf("ignore", "keys", "values")), "PDC attributes",
                    help = "ignore when attributes are written in the lore.", default = "ignore"),
                window("custom_window", "Window for custom items", 90, 50),
                field("custom_min_sales", integer(), "Sales needed for a custom item", default = 3, min = 1),
                field("include", list(enumOf(listOf("sale", "auction"))),
                    "Counted listings", default = listOf("sale", "auction")),
                field("per_seller_cap", integer(), "Sales counted per seller", default = 3, min = 1),
                bool("suggest_on_sell", "Suggest it as the price when selling", true),
                num("warn_below", "Warn below (× median)", 0.5),
                num("warn_above", "Warn above (× median)", 3.0),
            ),
            obj("import", "Import from other plugins",
                field("currency_map", map(string()), "Currency names",
                    help = "Source currency name → currency id here, e.g. VAULT: vault. Same names map themselves.",
                    default = emptyMap<String, String>()),
            ),
        ),
    ).withName("Auctions settings").withVersion(VERSION)

    /** The model/v1 document the editor receives. */
    fun document(): Map<String, Any?> = declaration().toWire()

    private fun field(
        name: String, type: ModelFieldType, label: String,
        help: String? = null, default: Any? = null, min: Number? = null, pattern: String? = null,
    ): ModelField = ModelField(name, type).withLabel(label).withHelp(help).withDefault(default)
        .withRange(min?.toDouble(), null).withPattern(pattern)

    private fun obj(name: String, label: String, vararg fields: ModelField) =
        field(name, ModelFieldType.`object`(fields.toList()), label)

    private fun bool(name: String, label: String, default: Boolean, help: String? = null) =
        field(name, boolType(), label, help, default)

    private fun num(name: String, label: String, default: Number) =
        field(name, ModelFieldType.number(), label, default = default, min = 0)

    private fun duration(name: String, label: String, default: String, help: String? = null) =
        field(name, string(), label, help ?: "e.g. 90s, 15m, 48h, 30d", default, pattern = DURATION)

    private fun window(name: String, label: String, days: Int, maxSales: Int) = obj(name, label,
        field("days", integer(), "Days", default = days, min = 1),
        field("max_sales", integer(), "Most recent sales", default = maxSales, min = 1),
    )
}
