package net.artisanmc.auctions.paper

import net.artisanmc.auctions.api.ListingType
import net.artisanmc.auctions.sources.ListingRows
import net.artisanmc.modules.api.ArtisanAPI
import net.artisanmc.modules.api.CommandDeclaration
import net.artisanmc.modules.api.DataSourceDeclaration
import net.artisanmc.modules.api.DataSourceField
import net.artisanmc.modules.api.FieldKind
import net.artisanmc.modules.api.Stability
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

    val LISTING_FIELDS = listOf(
        DataSourceField("id", FieldKind.STRING, "Listing id, for auctions:buy / bid / cancel"),
        DataSourceField("type", FieldKind.STRING, "sale or auction"),
        DataSourceField("is_sale", FieldKind.BOOLEAN, "Fixed-price sale"),
        DataSourceField("is_auction", FieldKind.BOOLEAN, "Auction"),
        DataSourceField("icon", FieldKind.OBJECT, "The item, for `item: \"{l.icon}\"`"),
        DataSourceField("name", FieldKind.STRING, "Item name (MiniMessage)"),
        DataSourceField("seller", FieldKind.STRING, "Seller name"),
        DataSourceField("seller_uuid", FieldKind.STRING, "Seller UUID"),
        DataSourceField("own", FieldKind.BOOLEAN, "The viewer is the seller"),
        DataSourceField("currency", FieldKind.STRING, "Currency id"),
        DataSourceField("price", FieldKind.NUMBER, "Sale price, or the bid to beat"),
        DataSourceField("price_display", FieldKind.STRING, "price, formatted with its currency"),
        DataSourceField("buy_now", FieldKind.BOOLEAN, "Can be bought outright"),
        DataSourceField("buy_now_display", FieldKind.STRING, "Buy-now price, formatted"),
        DataSourceField("start_price_display", FieldKind.STRING, "Opening bid, formatted"),
        DataSourceField("current_bid_display", FieldKind.STRING, "Leading bid, formatted (empty without bids)"),
        DataSourceField("min_bid", FieldKind.STRING, "Lowest next bid, plain number"),
        DataSourceField("min_bid_display", FieldKind.STRING, "Lowest next bid, formatted"),
        DataSourceField("has_bids", FieldKind.BOOLEAN, "At least one bid"),
        DataSourceField("bid_count", FieldKind.INTEGER, "Number of bids"),
        DataSourceField("leading", FieldKind.BOOLEAN, "The viewer holds the leading bid"),
        DataSourceField("is_lot", FieldKind.BOOLEAN, "Several stacks sold together"),
        DataSourceField("lot_size", FieldKind.INTEGER, "Stacks in the lot"),
        DataSourceField("quantity", FieldKind.INTEGER, "Items in total"),
        DataSourceField("created_at", FieldKind.NUMBER, "Epoch millis"),
        DataSourceField("ends_at", FieldKind.NUMBER, "Epoch millis, for {l.ends_at.until()}"),
        DataSourceField("ended", FieldKind.BOOLEAN, "Past its end, closing"),
    )

    fun declare(api: ArtisanAPI, house: AuctionHouse) {
        val now = System::currentTimeMillis
        api.dataSources.register(DataSourceDeclaration(LISTINGS, { player, params ->
            val q = ListingRows.Query.of(params)
            ListingRows.select(house.cache.all(), q, now()).map { ListingRows.row(it, player?.uniqueId, house.config(), now()) }
        }, LISTING_FIELDS))

        api.dataSources.register(DataSourceDeclaration(MY_LISTINGS, { player, params ->
            val viewer = player?.uniqueId ?: return@DataSourceDeclaration emptyList()
            val q = ListingRows.Query.of(params, seller = viewer)
            ListingRows.select(house.cache.all(), q, now()).map { ListingRows.row(it, viewer, house.config(), now()) }
        }, LISTING_FIELDS))

        api.dataSources.register(DataSourceDeclaration(LOT, { _, params ->
            val id = params["id"]?.toString()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val l = id?.let(house.cache::get) ?: return@DataSourceDeclaration emptyList()
            l.items.map { item ->
                mapOf("slot" to item.slot, "icon" to item.display + ("count" to item.quantity.coerceIn(1, 64)), "quantity" to item.quantity)
            }
        }, listOf(
            DataSourceField("slot", FieldKind.INTEGER, "Order in the lot"),
            DataSourceField("icon", FieldKind.OBJECT, "The stack"),
            DataSourceField("quantity", FieldKind.INTEGER, "Items in the stack"),
        )))

        api.dataSources.register(DataSourceDeclaration(CLAIMS, { player, _ ->
            player?.uniqueId?.let(house.claims::rows).orEmpty()
        }, listOf(
            DataSourceField("id", FieldKind.STRING, "Claim id, for auctions:claim"),
            DataSourceField("kind", FieldKind.STRING, "item or money"),
            DataSourceField("is_money", FieldKind.BOOLEAN, "An amount rather than an item"),
            DataSourceField("icon", FieldKind.OBJECT, "The item (gold ingot for money)"),
            DataSourceField("amount_display", FieldKind.STRING, "Amount, formatted"),
            DataSourceField("reason", FieldKind.STRING, "bought, sold, expired, cancelled, won, refund, outbid, imported"),
            DataSourceField("created_at", FieldKind.NUMBER, "Epoch millis"),
            DataSourceField("expires_at", FieldKind.NUMBER, "When an unclaimed item is purged, epoch millis"),
        )))

        // One row per viewer: counters for buttons ("My listings (3)", "Claims (2)").
        api.dataSources.register(DataSourceDeclaration(SUMMARY, { player, _ ->
            val viewer = player?.uniqueId
            listOf(mapOf(
                "id" to "summary",
                "listings" to house.cache.all().count { it.endsAt > now() },
                "mine" to (viewer?.let(house.cache::countBySeller) ?: 0),
                "limit" to (player?.let(house::limitOf) ?: house.config().limits.maxActiveListings),
                "claims" to (viewer?.let { house.claims.of(it).size } ?: 0),
            ))
        }, listOf(
            DataSourceField("id", FieldKind.STRING, "Always `summary`"),
            DataSourceField("listings", FieldKind.INTEGER, "Active listings in the house"),
            DataSourceField("mine", FieldKind.INTEGER, "Active listings of the viewer"),
            DataSourceField("limit", FieldKind.INTEGER, "How many the viewer may have"),
            DataSourceField("claims", FieldKind.INTEGER, "Items and amounts waiting in the viewer's box"),
        ), stability = Stability.DYNAMIC))

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
