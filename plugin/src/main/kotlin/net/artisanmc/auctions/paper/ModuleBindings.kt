package net.artisanmc.auctions.paper

import net.artisanmc.auctions.api.ListingType
import net.artisanmc.auctions.sources.ListingRows
import net.artisanmc.modules.api.ArtisanAPI
import net.artisanmc.modules.api.command.CommandDeclaration
import net.artisanmc.modules.api.datasource.DataFetcher
import net.artisanmc.modules.api.datasource.DataSourceDeclaration
import net.artisanmc.modules.api.datasource.DataSourceField
import net.artisanmc.modules.api.model.ModelFieldType
import org.bukkit.entity.Player
import java.util.UUID

/**
 * What the add-on's menus and dialogs call: data sources to show the house and
 * `auctions:*` commands for their buttons (`run_command: "auctions:buy {l.id}"`).
 */
object ModuleBindings {
    const val LISTINGS = "auctions:listings"
    const val MY_LISTINGS = "auctions:my_listings"
    const val LOT = "auctions:lot"
    const val CLAIMS = "auctions:claims"
    const val SUMMARY = "auctions:summary"
    const val PRICE = "auctions:price"

    val LISTING_FIELDS = listOf(
        DataSourceField("id", ModelFieldType.string(), "Listing id, for auctions:buy / bid / cancel"),
        DataSourceField("type", ModelFieldType.string(), "sale or auction"),
        DataSourceField("is_sale", ModelFieldType.bool(), "Fixed-price sale"),
        DataSourceField("is_auction", ModelFieldType.bool(), "Auction"),
        DataSourceField("icon", ModelFieldType.`object`(emptyList()), "The item, for `item: \"{l.icon}\"`"),
        DataSourceField("name", ModelFieldType.string(), "Item name (MiniMessage)"),
        DataSourceField("seller", ModelFieldType.string(), "Seller name"),
        DataSourceField("seller_uuid", ModelFieldType.string(), "Seller UUID"),
        DataSourceField("own", ModelFieldType.bool(), "The viewer is the seller"),
        DataSourceField("currency", ModelFieldType.string(), "Currency id"),
        DataSourceField("price", ModelFieldType.number(), "Sale price, or the bid to beat"),
        DataSourceField("price_display", ModelFieldType.string(), "price, formatted with its currency"),
        DataSourceField("buy_now", ModelFieldType.bool(), "Can be bought outright"),
        DataSourceField("buy_now_display", ModelFieldType.string(), "Buy-now price, formatted"),
        DataSourceField("start_price_display", ModelFieldType.string(), "Opening bid, formatted"),
        DataSourceField("current_bid_display", ModelFieldType.string(), "Leading bid, formatted (empty without bids)"),
        DataSourceField("min_bid", ModelFieldType.string(), "Lowest next bid, plain number"),
        DataSourceField("min_bid_display", ModelFieldType.string(), "Lowest next bid, formatted"),
        DataSourceField("has_bids", ModelFieldType.bool(), "At least one bid"),
        DataSourceField("bid_count", ModelFieldType.integer(), "Number of bids"),
        DataSourceField("leading", ModelFieldType.bool(), "The viewer holds the leading bid"),
        DataSourceField("is_lot", ModelFieldType.bool(), "Several stacks sold together"),
        DataSourceField("lot_size", ModelFieldType.integer(), "Stacks in the lot"),
        DataSourceField("quantity", ModelFieldType.integer(), "Items in total"),
        DataSourceField("created_at", ModelFieldType.number(), "Epoch millis"),
        DataSourceField("ends_at", ModelFieldType.number(), "Epoch millis, for {l.ends_at.until()}"),
        DataSourceField("ended", ModelFieldType.bool(), "Past its end, closing"),
        DataSourceField("has_median", ModelFieldType.bool(), "Enough sales of this item for a median"),
        DataSourceField("median_display", ModelFieldType.string(), "Median price of one item, formatted"),
        DataSourceField("median_sales", ModelFieldType.integer(), "Sales the median rests on"),
        DataSourceField("median_days", ModelFieldType.integer(), "Days those sales cover"),
        DataSourceField("median_ratio", ModelFieldType.integer(), "Unit price against the median, in percent (-20 = 20 % below)"),
        DataSourceField("median_ratio_display", ModelFieldType.string(), "median_ratio as text: -20 %, +15 %"),
    )

    fun declare(api: ArtisanAPI, house: AuctionHouse) {
        val now = System::currentTimeMillis
        api.dataSources.register(DataSourceDeclaration(LISTINGS, { player, params ->
            val q = ListingRows.Query.of(params)
            ListingRows.select(house.cache.all(), q, now()).map { ListingRows.row(it, player?.uniqueId, house.config(), now(), house.medianOf(it)) }
        }, LISTING_FIELDS))

        api.dataSources.register(DataSourceDeclaration(MY_LISTINGS, { player, params ->
            val viewer = player?.uniqueId ?: return@DataSourceDeclaration emptyList()
            val q = ListingRows.Query.of(params, seller = viewer)
            ListingRows.select(house.cache.all(), q, now()).map { ListingRows.row(it, viewer, house.config(), now(), house.medianOf(it)) }
        }, LISTING_FIELDS))

        api.dataSources.register(DataSourceDeclaration(LOT, { _, params ->
            val id = params["id"]?.toString()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val l = id?.let(house.cache::get) ?: return@DataSourceDeclaration emptyList()
            l.items.map { item ->
                mapOf("slot" to item.slot, "icon" to item.display + ("count" to item.quantity.coerceIn(1, 64)), "quantity" to item.quantity)
            }
        }, listOf(
            DataSourceField("slot", ModelFieldType.integer(), "Order in the lot"),
            DataSourceField("icon", ModelFieldType.`object`(emptyList()), "The stack"),
            DataSourceField("quantity", ModelFieldType.integer(), "Items in the stack"),
        )))

        api.dataSources.register(DataSourceDeclaration(CLAIMS, { player, _ ->
            player?.uniqueId?.let(house.claims::rows).orEmpty()
        }, listOf(
            DataSourceField("id", ModelFieldType.string(), "Claim id, for auctions:claim"),
            DataSourceField("kind", ModelFieldType.string(), "item or money"),
            DataSourceField("is_money", ModelFieldType.bool(), "An amount rather than an item"),
            DataSourceField("icon", ModelFieldType.`object`(emptyList()), "The item (gold ingot for money)"),
            DataSourceField("amount_display", ModelFieldType.string(), "Amount, formatted"),
            DataSourceField("reason", ModelFieldType.string(), "bought, sold, expired, cancelled, won, refund, outbid, imported"),
            DataSourceField("created_at", ModelFieldType.number(), "Epoch millis"),
            DataSourceField("expires_at", ModelFieldType.number(), "When an unclaimed item is purged, epoch millis"),
        )))

        // One row per viewer: counters for buttons ("My listings (3)", "Claims (2)").
        api.dataSources.register(DataSourceDeclaration(SUMMARY, DataFetcher { player, _ ->
            val viewer = player?.uniqueId
            listOf(mapOf(
                "id" to "summary",
                "listings" to house.cache.all().count { it.endsAt > now() },
                "mine" to (viewer?.let(house.cache::countBySeller) ?: 0),
                "limit" to (player?.let(house::limitOf) ?: house.config().limits.maxActiveListings),
                "claims" to (viewer?.let { house.claims.of(it).size } ?: 0),
            ))
        }, listOf(
            DataSourceField("id", ModelFieldType.string(), "Always `summary`"),
            DataSourceField("listings", ModelFieldType.integer(), "Active listings in the house"),
            DataSourceField("mine", ModelFieldType.integer(), "Active listings of the viewer"),
            DataSourceField("limit", ModelFieldType.integer(), "How many the viewer may have"),
            DataSourceField("claims", ModelFieldType.integer(), "Items and amounts waiting in the viewer's box"),
        )))

        // The median of the held item (or of `item`, a material name), for any menu.
        api.dataSources.register(DataSourceDeclaration(PRICE, { player, params ->
            val currency = params["currency"]?.toString()?.takeIf { it.isNotBlank() } ?: house.config().currencies.default
            val stack = params["item"]?.toString()?.takeIf { it.isNotBlank() }
                ?.let { runCatching { org.bukkit.inventory.ItemStack(org.bukkit.Material.valueOf(it.uppercase()), 1) }.getOrNull() }
                ?: player?.inventory?.itemInMainHand?.takeUnless { it.type.isAir }
                ?: return@DataSourceDeclaration listOf(mapOf("id" to "price", "has_item" to false, "has_median" to false))
            val key = house.priceKey(stack)
            listOf(mapOf("id" to "price", "has_item" to true, "key" to key) + house.medianArgs(house.medians.get(key, currency), currency))
        }, listOf(
            DataSourceField("id", ModelFieldType.string(), "Always `price`"),
            DataSourceField("has_item", ModelFieldType.bool(), "An item was found (held, or given by `item`)"),
            DataSourceField("key", ModelFieldType.string(), "The item's price key"),
            DataSourceField("has_median", ModelFieldType.bool(), "Enough sales for a median"),
            DataSourceField("median", ModelFieldType.string(), "Median price of one item, formatted (- when unknown)"),
            DataSourceField("median_sales", ModelFieldType.integer(), "Sales the median rests on"),
            DataSourceField("median_days", ModelFieldType.integer(), "Days those sales cover"),
        )))

        command(api, "auctions:sell_confirm") { p, _ -> house.confirmSell(p) }
        command(api, "auctions:sell_start") { p, argv -> house.sellStart(p, argv.firstOrNull()?.let(::typeOf)) }
        command(api, "auctions:sell") { p, argv ->
            val type = argv.getOrNull(0)?.let(::typeOf) ?: return@command house.notifier.tell(p, "bad_command")
            when (type) {
                ListingType.SALE -> house.sell(p, type, argv.getOrNull(1), null, null, argv.getOrNull(2))
                ListingType.AUCTION -> house.sell(p, type, argv.getOrNull(1), argv.getOrNull(2), argv.getOrNull(3), argv.getOrNull(4))
            }
        }
        command(api, "auctions:buy") { p, argv -> idOf(argv)?.let { house.buy(p, it) } ?: house.notifier.tell(p, "gone") }
        command(api, "auctions:bid") { p, argv -> idOf(argv)?.let { house.bid(p, it, argv.getOrNull(1)) } ?: house.notifier.tell(p, "gone") }
        command(api, "auctions:cancel") { p, argv -> idOf(argv)?.let { house.cancel(p, it, admin = false) } ?: house.notifier.tell(p, "gone") }
        command(api, "auctions:admin_cancel") { p, argv -> idOf(argv)?.let { house.cancel(p, it, admin = true) } ?: house.notifier.tell(p, "gone") }
        command(api, "auctions:claim") { p, argv ->
            val arg = argv.firstOrNull()
            house.claim(p, if (arg == null || arg == "all") null else runCatching { UUID.fromString(arg) }.getOrNull() ?: return@command)
        }
    }

    private fun typeOf(s: String): ListingType? = when (s.lowercase()) {
        "sale", "sell", "fixed" -> ListingType.SALE
        "auction", "bid" -> ListingType.AUCTION
        else -> null
    }

    private fun idOf(argv: List<String>): UUID? = argv.firstOrNull()?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun command(api: ArtisanAPI, name: String, handler: (Player, List<String>) -> Unit) {
        api.commands.register(CommandDeclaration(name) { player, args ->
            val argv = (args["argv"] as? List<*>)?.map { it.toString() }.orEmpty()
            handler(player, argv)
        })
    }
}
