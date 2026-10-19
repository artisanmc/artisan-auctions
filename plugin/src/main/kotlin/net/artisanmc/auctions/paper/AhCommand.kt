package net.artisanmc.auctions.paper

import net.artisanmc.auctions.api.ListingType
import net.artisanmc.auctions.config.formatDuration
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * `/ah`: the house for players, plus `/ah admin …`. Everything a player can do
 * here is also reachable from the menus.
 */
class AhCommand(private val house: AuctionHouse, private val admin: AdminCommands) : TabExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.firstOrNull()?.lowercase() == "admin") return admin.run(sender, args.drop(1))
        val p = sender as? Player ?: run { sender.sendMessage("Players only (or /$label admin …)"); return true }
        if (!p.hasPermission(AuctionHouse.PERM_USE)) { house.notifier.tell(p, "no_permission"); return true }
        when (args.firstOrNull()?.lowercase()) {
            null -> house.open(p)
            "sell" -> if (args.size < 2) house.sellStart(p, ListingType.SALE)
                else house.sell(p, ListingType.SALE, args[1], null, null, args.getOrNull(2))
            "auction" -> if (args.size < 2) house.sellStart(p, ListingType.AUCTION)
                else house.sell(p, ListingType.AUCTION, args[1], args.getOrNull(2), args.getOrNull(3), args.getOrNull(4))
            "search" -> house.open(p, AuctionHouse.MENU_MAIN, mapOf("search" to args.drop(1).joinToString(" ").filter { it != '"' && it != '\\' }))
            "mine", "listings" -> house.open(p, AuctionHouse.MENU_MINE)
            "claim", "claims", "box" -> house.open(p, AuctionHouse.MENU_CLAIMS)
            else -> house.notifier.tell(p, "usage", mapOf("label" to label))
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val subs = buildList {
            addAll(listOf("sell", "auction", "search", "mine", "claim"))
            if (sender.hasPermission(AuctionHouse.PERM_ADMIN)) add("admin")
        }
        return when {
            args.size == 1 -> subs.filter { it.startsWith(args[0].lowercase()) }
            args[0].equals("admin", true) && sender.hasPermission(AuctionHouse.PERM_ADMIN) -> admin.complete(args.drop(1))
            args[0].equals("auction", true) && args.size == 3 -> house.config().auctions.durations
                .map(::formatDuration).filter { it.startsWith(args[2]) }
            (args[0].equals("sell", true) && args.size == 3) || (args[0].equals("auction", true) && args.size == 5) ->
                house.config().currencies.allowed.filter { it.startsWith(args.last()) }
            else -> emptyList()
        }
    }
}

/** `/ah admin cancel <id>`, `closeall [player]`, `reindex`. The importer adds `import`. */
class AdminCommands(private val house: AuctionHouse) {
    private val extra = LinkedHashMap<String, (CommandSender, List<String>) -> Unit>()

    fun register(name: String, run: (CommandSender, List<String>) -> Unit) { extra[name] = run }

    fun run(sender: CommandSender, args: List<String>): Boolean {
        if (!sender.hasPermission(AuctionHouse.PERM_ADMIN)) { sender.sendMessage("No permission"); return true }
        when (val sub = args.firstOrNull()?.lowercase()) {
            "cancel" -> {
                val p = sender as? Player ?: return true.also { sender.sendMessage("Run it in game") }
                val id = args.getOrNull(1)?.let(house::findListing)
                    ?: return true.also { sender.sendMessage("Unknown or ambiguous listing id") }
                house.cancel(p, id, admin = true)
            }
            "closeall" -> {
                val p = sender as? Player ?: return true.also { sender.sendMessage("Run it in game") }
                val seller = args.getOrNull(1)?.let { name ->
                    Bukkit.getOfflinePlayerIfCached(name)?.uniqueId ?: return true.also { sender.sendMessage("Unknown player $name") }
                }
                sender.sendMessage("Cancelling ${house.cancelAll(p, seller)} listing(s); items go to the sellers' claims")
            }
            "reindex" -> {
                sender.sendMessage("Reindexing price keys in the background…")
                house.reindex { sender.sendMessage(it) }
            }
            null -> sender.sendMessage("/ah admin cancel <id> | closeall [player] | reindex" + extra.keys.joinToString("") { " | $it" })
            else -> extra[sub]?.invoke(sender, args.drop(1)) ?: sender.sendMessage("Unknown admin command $sub")
        }
        return true
    }

    fun complete(args: List<String>): List<String> = when (args.size) {
        1 -> (listOf("cancel", "closeall", "reindex") + extra.keys).filter { it.startsWith(args[0].lowercase()) }
        2 -> if (args[0].equals("cancel", true)) house.cache.all().map { it.id.toString().take(8) }.filter { it.startsWith(args[1]) }
            else emptyList()
        else -> emptyList()
    }
}

class PlayerListener(private val house: AuctionHouse) : Listener {
    @EventHandler fun onJoin(e: PlayerJoinEvent) = house.join(e.player)
    @EventHandler fun onQuit(e: PlayerQuitEvent) = house.quit(e.player)
}
