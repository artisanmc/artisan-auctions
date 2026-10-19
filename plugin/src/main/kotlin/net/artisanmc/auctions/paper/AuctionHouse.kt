package net.artisanmc.auctions.paper

import net.artisanmc.auctions.api.ListingType
import net.artisanmc.auctions.config.AuctionsConfig
import net.artisanmc.auctions.config.formatDuration
import net.artisanmc.auctions.config.parseDuration
import net.artisanmc.auctions.db.AuctionStore
import net.artisanmc.auctions.market.ClaimDelivery
import net.artisanmc.auctions.market.ClaimKind
import net.artisanmc.auctions.market.Fees
import net.artisanmc.auctions.market.ListRequest
import net.artisanmc.auctions.market.ListingCache
import net.artisanmc.auctions.market.ListingItem
import net.artisanmc.auctions.market.Market
import net.artisanmc.auctions.market.SellRules
import net.artisanmc.auctions.median.MedianCache
import net.artisanmc.auctions.median.MedianMath
import net.artisanmc.auctions.median.MedianPrice
import net.artisanmc.auctions.median.PriceKeys
import net.artisanmc.auctions.sources.ListingRows
import net.artisanmc.modules.api.ArtisanAPI
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.math.BigDecimal
import java.util.UUID
import java.util.logging.Level

/**
 * The add-on running on Paper: the market wired to Artisan's economy, items,
 * menus and messages. Commands and data sources call into this.
 */
class AuctionHouse(
    private val plugin: Plugin,
    private val api: ArtisanAPI,
    val server: String,
    val store: AuctionStore,
    val config: () -> AuctionsConfig,
) {
    private val logger = plugin.logger
    val threads = PaperThreads(plugin)
    val wallets = EconomyWallets(api.economy)
    val cache = ListingCache()
    val notifier = ProjectNotifier(api.actions, config, logger)
    val claims = ClaimBox(server, store, threads, config, logger) { owner ->
        Bukkit.getPlayer(owner)?.let { p -> if (api.menus.openMenuId(p) == MENU_CLAIMS) api.menus.refresh(p) }
    }
    val medians = MedianCache(store, threads, config, logger, loaded = ::scheduleRefresh)
    val hooks = EventHooks(notifier,
        median = { r -> r.items.map { it.priceKey }.distinct().singleOrNull()?.let { medians.get(it, r.currency)?.price } },
        onSold = { l -> l.sharedPriceKey?.let { medians.invalidate(it, l.currency) } },
        listingsChanged = ::scheduleRefresh, claimsChanged = { owners ->
        owners.filter { Bukkit.getPlayer(it) != null }.forEach { claims.reload(it) }
    })
    val delivery = ClaimDelivery(store, threads, wallets, InventoryGiver(), { hooks }, logger)
    val market = Market(server, store, cache, threads, wallets, delivery, config, { hooks }, logger)

    /** The item a player is about to sell, remembered while the price dialog is open. */
    private data class Pending(val slot: Int, val stack: ItemStack, val type: ListingType?)
    private val pending = HashMap<UUID, Pending>()
    /** A listing waiting for the seller to confirm a price far from the median. */
    private val confirmations = HashMap<UUID, () -> Unit>()

    fun start() {
        market.start { Bukkit.getOnlinePlayers().forEach { join(it) } }
    }

    fun stop() {
        pending.clear()
        confirmations.clear()
        threads.shutdown()
    }

    // ── Menus ────────────────────────────────────────────────────────────────

    private var refreshQueued = false

    /** Repaints the house menus once per tick at most, however many listings changed. */
    fun scheduleRefresh() {
        if (refreshQueued || !plugin.isEnabled) return
        refreshQueued = true
        Bukkit.getScheduler().runTask(plugin, Runnable {
            refreshQueued = false
            LISTING_MENUS.forEach(api.menus::refreshAll)
        })
    }

    fun join(p: Player) {
        // Money waiting in the box is paid at once; items wait for the player to take them.
        threads.async {
            val open = runCatching { store.unclaimed(server, p.uniqueId) }.getOrNull().orEmpty()
            threads.main {
                delivery.deliver(open, items = false) { report ->
                    val paid = report.delivered.size
                    if (paid > 0) notifier.tell(p, "claims_paid", mapOf("count" to paid))
                    val items = report.left.size
                    if (items > 0) notifier.tell(p, "claims_waiting", mapOf("count" to items))
                    claims.reload(p.uniqueId)
                }
            }
        }
    }

    fun quit(p: Player) {
        pending.remove(p.uniqueId)
        confirmations.remove(p.uniqueId)
        claims.forget(p.uniqueId)
    }

    fun open(p: Player, menu: String = MENU_MAIN, args: Map<String, Any> = emptyMap()) = notifier.openMenu(p, menu, args)

    // ── Selling ──────────────────────────────────────────────────────────────

    /** Limit of active listings for [p], permissions included. */
    fun limitOf(p: Player): Int = SellRules.limit(
        p.effectivePermissions.filter { it.value }.map { it.permission }, config().limits.maxActiveListings)

    /** Checks the held item and opens the price dialog (menu "Sell" button). */
    fun sellStart(p: Player, type: ListingType?) {
        val c = config()
        if (!p.hasPermission(PERM_SELL)) return notifier.tell(p, "no_permission")
        if (c.readOnly) return notifier.tell(p, "read_only")
        val slot = p.inventory.heldItemSlot
        val stack = p.inventory.getItem(slot)?.takeUnless { it.type.isAir } ?: return notifier.tell(p, "no_item")
        problemWithItem(p, stack)?.let { return notifier.tell(p, it) }
        if (cache.countBySeller(p.uniqueId) >= limitOf(p)) return notifier.tell(p, "limit_reached", mapOf("limit" to limitOf(p)))

        val chosen = type ?: when {
            c.sales.enabled && c.auctions.enabled -> null
            c.sales.enabled -> ListingType.SALE
            else -> ListingType.AUCTION
        }
        pending[p.uniqueId] = Pending(slot, stack.clone(), chosen)
        val args = sellArgs(stack, chosen)
        when (chosen) {
            null -> notifier.openDialog(p, DIALOG_SELL_TYPE, args)
            ListingType.SALE -> notifier.openDialog(p, DIALOG_SELL_FIXED, args)
            ListingType.AUCTION -> notifier.openDialog(p, DIALOG_SELL_AUCTION, args)
        }
    }

    private fun sellArgs(stack: ItemStack, type: ListingType?): Map<String, Any> {
        val c = config()
        val fee = c.limits.listingFee
        val tax = c.limits.saleTax
        fun pct(f: AuctionsConfig.Fee) = buildString {
            if (f.percent.signum() > 0) append(f.percent.stripTrailingZeros().toPlainString()).append(" %")
            if (f.flat.signum() > 0) { if (isNotEmpty()) append(" + "); append(c.currencies.format(c.currencies.default, f.flat)) }
            if (isEmpty()) append("0")
        }
        return mapOf(
            "item_name" to (PaperItems.display(stack)["title"] ?: ListingRows.prettyMaterial(stack.type.name)),
            "quantity" to stack.amount,
            "fee" to pct(fee),
            "tax" to pct(tax),
            "currency" to c.currencies.default,
            "durations" to c.auctions.durations.joinToString(", ", transform = ::formatDuration),
            "default_duration" to formatDuration(c.auctions.defaultDuration),
            "sale_duration" to formatDuration(c.sales.duration),
            "type" to (type?.id() ?: ""),
        ) + medianArgs(medianOf(stack, c.currencies.default), c.currencies.default)
    }

    /** The cached median of [stack] in [currency]; starts computing it when unknown. */
    fun medianOf(stack: ItemStack, currency: String): MedianPrice? = medians.get(priceKey(stack), currency)

    /** Median of a listing's item; a mixed lot has none. */
    fun medianOf(l: net.artisanmc.auctions.market.Listing): MedianPrice? = l.sharedPriceKey?.let { medians.get(it, l.currency) }

    fun medianArgs(m: MedianPrice?, currency: String): Map<String, Any> = mapOf(
        "has_median" to (m?.price != null),
        "median" to (m?.price?.let { notifier.money(currency, it) } ?: "-"),
        "median_sales" to (m?.sales ?: 0),
        "median_days" to (m?.windowDays ?: config().median.window.days),
    )

    private fun problemWithItem(p: Player, stack: ItemStack): String? {
        if (SellRules.forbidden(stack.type.name, PaperItems.plainLore(stack), config().items)) return "forbidden_item"
        return null
    }

    /**
     * `auctions:sell <sale|auction> <price> [duration] [buyout] [currency]`.
     * Uses the item remembered by [sellStart] when it is still in its slot,
     * else the held item (direct `/ah sell`).
     */
    fun sell(p: Player, type: ListingType, price: String?, duration: String?, buyout: String?, currencyArg: String?, force: Boolean = false) {
        val c = config()
        if (!p.hasPermission(PERM_SELL)) return notifier.tell(p, "no_permission")
        val amount = price?.let(Fees::parseAmount) ?: return notifier.tell(p, "bad_amount", mapOf("input" to (price ?: "")))
        val buyNow = buyout?.takeIf { it.isNotBlank() && it != "-" && it != "0" }?.let {
            Fees.parseAmount(it) ?: return notifier.tell(p, "bad_amount", mapOf("input" to it))
        }
        val length = when (type) {
            ListingType.SALE -> c.sales.duration
            ListingType.AUCTION -> duration?.takeIf { it.isNotBlank() && it != "-" }?.let {
                parseDuration(it) ?: return notifier.tell(p, "bad_duration")
            } ?: c.auctions.defaultDuration
        }
        val currency = currencyArg?.takeIf { it.isNotBlank() && it != "-" }?.lowercase() ?: c.currencies.default

        val remembered = pending.remove(p.uniqueId)
        val slot = remembered?.slot ?: p.inventory.heldItemSlot
        val stack = p.inventory.getItem(slot)?.takeUnless { it.type.isAir } ?: return notifier.tell(p, "no_item")
        if (remembered != null && (!stack.isSimilar(remembered.stack) || stack.amount != remembered.stack.amount)) {
            return notifier.tell(p, "item_changed")
        }
        problemWithItem(p, stack)?.let { return notifier.tell(p, it) }

        val item = ListingItem(0, PaperItems.encode(stack), PaperItems.display(stack), priceKey(stack), stack.amount)
        var request = ListRequest(
            seller = p.uniqueId, sellerName = p.name, type = type, items = listOf(item), currency = currency,
            price = if (type == ListingType.SALE) amount else buyNow,
            startPrice = if (type == ListingType.AUCTION) amount else null,
            duration = length, limit = limitOf(p),
        )
        market.check(request)?.let { return notifier.tell(p, it.key, display(it.args, currency)) }
        val (changed, veto) = hooks.preList(request)
        if (veto != null || changed == null) return notifier.veto(p, veto?.message)
        request = changed
        market.check(request)?.let { return notifier.tell(p, it.key, display(it.args, currency)) }

        // Far from the median: ask once, the seller stays free to confirm.
        val median = medians.get(item.priceKey, currency)?.price
        val asked = request.price.takeIf { type == ListingType.SALE } ?: request.startPrice
        if (!force && median != null && asked != null) {
            val unit = asked.divide(BigDecimal(stack.amount), 4, java.math.RoundingMode.HALF_UP)
            val m = c.median
            if (unit < median * m.warnBelow || unit > median * m.warnAbove) {
                remembered?.let { pending[p.uniqueId] = it }
                confirmations[p.uniqueId] = { sell(p, type, price, duration, buyout, currencyArg, force = true) }
                notifier.openDialog(p, DIALOG_PRICE_WARNING, mapOf(
                    "item_name" to (item.display["title"] ?: ListingRows.prettyMaterial(stack.type.name)),
                    "quantity" to stack.amount,
                    "price" to notifier.money(currency, asked),
                    "unit_price" to notifier.money(currency, unit),
                    "ratio" to ListingRows.ratioText(MedianMath.ratio(unit, median)),
                ) + medianArgs(medians.get(item.priceKey, currency), currency))
                return
            }
        }

        // Take the item now; it comes back if the listing cannot be written.
        val taken = stack.clone()
        p.inventory.setItem(slot, null)
        market.create(request, itemsBack = { giveBack(p.uniqueId, taken) }) { outcome ->
            when (outcome) {
                is Market.Listed -> notifier.tell(p, "listed", notifier.listingArgs(outcome.listing) +
                    mapOf("fee" to notifier.money(currency, outcome.fee)))
                else -> notifier.tell(p, outcome.key, display(outcome.args, currency))
            }
        }
    }

    /** `auctions:sell_confirm`: the seller accepts a price far from the median. */
    fun confirmSell(p: Player) {
        confirmations.remove(p.uniqueId)?.invoke() ?: notifier.tell(p, "item_changed")
    }

    /** Back to the player, or at their feet if the inventory filled meanwhile. */
    private fun giveBack(owner: UUID, stack: ItemStack) {
        val p = Bukkit.getPlayer(owner)
        if (p == null) {
            logger.severe("[Auctions] could not return ${stack.type} x${stack.amount} to offline $owner: ${PaperItems.encode(stack)}")
            return
        }
        p.inventory.addItem(stack).values.forEach { p.world.dropItemNaturally(p.location, it) }
    }

    fun priceKey(stack: ItemStack): String = PriceKeys.of(PaperFacts.of(stack), config().median)

    /** BigDecimal args become amounts written in the listing's currency. */
    fun display(args: Map<String, Any>, currency: String): Map<String, Any> =
        args.mapValues { (_, v) -> if (v is BigDecimal) notifier.money(currency, v) else v }

    // ── Buying, cancelling, claims ───────────────────────────────────────────

    fun buy(p: Player, id: UUID) {
        if (!p.hasPermission(PERM_USE)) return notifier.tell(p, "no_permission")
        val currency = cache.get(id)?.currency ?: config().currencies.default
        market.buy(p.uniqueId, id) { outcome ->
            when (outcome) {
                is Market.Bought -> notifier.tell(p, "bought", notifier.listingArgs(outcome.listing))
                is Market.Vetoed -> notifier.veto(p, outcome.veto.message)
                else -> notifier.tell(p, outcome.key, display(outcome.args, currency))
            }
        }
    }

    fun bid(p: Player, id: UUID, amountText: String?) {
        if (!p.hasPermission(PERM_BID)) return notifier.tell(p, "no_permission")
        val amount = amountText?.let(Fees::parseAmount) ?: return notifier.tell(p, "bad_amount", mapOf("input" to (amountText ?: "")))
        val currency = cache.get(id)?.currency ?: config().currencies.default
        market.bid(p.uniqueId, id, amount) { outcome ->
            when (outcome) {
                is Market.BidPlaced -> notifier.tell(p, if (outcome.extended) "bid_placed_extended" else "bid_placed",
                    notifier.listingArgs(outcome.listing) + mapOf("amount" to notifier.money(currency, outcome.amount)))
                is Market.Bought -> notifier.tell(p, "bought", notifier.listingArgs(outcome.listing))
                is Market.Vetoed -> notifier.veto(p, outcome.veto.message)
                else -> notifier.tell(p, outcome.key, display(outcome.args, currency))
            }
        }
    }

    fun cancel(p: Player, id: UUID, admin: Boolean) {
        if (admin && !p.hasPermission(PERM_ADMIN)) return notifier.tell(p, "no_permission")
        market.cancel(p.uniqueId, id, admin) { outcome ->
            when (outcome) {
                is Market.Cancelled -> notifier.tell(p, "cancelled", notifier.listingArgs(outcome.listing))
                is Market.Vetoed -> notifier.veto(p, outcome.veto.message)
                else -> notifier.tell(p, outcome.key, outcome.args)
            }
        }
    }

    fun claim(p: Player, id: UUID?) {
        val done = { report: ClaimDelivery.Report ->
            when {
                report.delivered.isNotEmpty() && report.left.isEmpty() -> notifier.tell(p, "claimed", mapOf("count" to report.delivered.size))
                report.delivered.isNotEmpty() -> notifier.tell(p, "claimed_partly", mapOf("count" to report.delivered.size, "left" to report.left.size))
                report.left.isNotEmpty() -> notifier.tell(p, "inventory_full")
                else -> notifier.tell(p, "nothing_to_claim")
            }
        }
        if (id == null) delivery.deliverAll(server, p.uniqueId, done) else delivery.deliverOne(p.uniqueId, id, done)
    }

    /** Cancels every active listing (admin). Returns how many were asked. */
    fun cancelAll(actor: Player, seller: UUID?): Int {
        val targets = cache.all().filter { seller == null || it.sellerUuid == seller }
        targets.forEach { l -> market.cancel(actor.uniqueId, l.id, admin = true) {} }
        return targets.size
    }

    /** A listing id typed by an admin: full UUID or a unique prefix. */
    fun findListing(text: String): UUID? {
        runCatching { UUID.fromString(text) }.getOrNull()?.let { return it }
        val matches = cache.all().filter { it.id.toString().startsWith(text.lowercase()) }
        return matches.singleOrNull()?.id
    }

    /**
     * Recomputes every price key from the stored items, after a change of the
     * median settings. Runs in the background; [report] gets progress lines.
     */
    fun reindex(report: (String) -> Unit) {
        val median = config().median
        threads.async {
            var after = ""
            var listings = 0
            var changed = 0
            var unreadable = 0
            while (true) {
                val page = runCatching { store.listingsPage(server, after, 200) }.getOrElse {
                    logger.log(Level.WARNING, "[Auctions] reindex stopped", it)
                    threads.main { report("Reindex stopped: ${it.message}") }
                    return@async
                }
                if (page.isEmpty()) break
                for (l in page) {
                    val keys = l.items.associate { item ->
                        val stack = PaperItems.decodeOrNull(item.blob)
                        if (stack == null) unreadable++
                        item.slot to (stack?.let { PriceKeys.of(PaperFacts.of(it), median) } ?: item.priceKey)
                    }
                    if (keys.any { (slot, key) -> l.items.first { it.slot == slot }.priceKey != key }) {
                        runCatching { store.updatePriceKeys(l.id, keys, keys.values.distinct().singleOrNull()) }
                            .onFailure { e -> logger.log(Level.WARNING, "[Auctions] reindex of ${l.id} failed", e) }
                            .onSuccess { changed++ }
                    }
                }
                listings += page.size
                after = page.last().id.toString()
            }
            val active = runCatching { store.activeListings(server) }.getOrNull()
            threads.main {
                active?.let(cache::replaceAll)
                medians.clear()
                report("Reindexed $listings listing(s): $changed changed, $unreadable unreadable item(s) kept their key")
            }
        }
    }

    // ── Periodic work ────────────────────────────────────────────────────────

    fun tick() {
        market.expireDue()
    }

    /** Items unclaimed past the retention are logged, then dropped; money is paid out. */
    fun purgeStaleClaims() {
        val cutoff = System.currentTimeMillis() - config().claims.retention.toMillis()
        threads.async {
            val stale = runCatching { store.staleClaims(server, cutoff) }.getOrElse {
                logger.log(Level.WARNING, "[Auctions] stale claims could not be read", it); return@async
            }
            if (stale.isEmpty()) return@async
            val (money, items) = stale.partition { it.kind == ClaimKind.MONEY }
            for (c in items) {
                logger.warning("[Auctions] unclaimed item of ${c.owner} purged after ${formatDuration(config().claims.retention)}: ${c.itemBlob}")
                runCatching { store.deleteClaim(c.id) }
            }
            if (money.isNotEmpty()) threads.main { delivery.deliver(money, items = false) }
        }
    }

    companion object {
        const val MENU_MAIN = "ah_main"
        const val MENU_MINE = "ah_mine"
        const val MENU_CLAIMS = "ah_claims"
        const val MENU_LOT = "ah_lot"
        val LISTING_MENUS = listOf(MENU_MAIN, MENU_MINE, MENU_LOT)
        const val DIALOG_SELL_TYPE = "ah_sell_type"
        const val DIALOG_SELL_FIXED = "ah_sell_fixed"
        const val DIALOG_SELL_AUCTION = "ah_sell_auction"
        const val DIALOG_PRICE_WARNING = "ah_price_warning"

        const val PERM_USE = "auctions.use"
        const val PERM_SELL = "auctions.sell"
        const val PERM_BID = "auctions.bid"
        const val PERM_ADMIN = "auctions.admin"
    }
}
