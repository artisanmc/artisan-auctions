package net.artisanmc.auctions.paper

import net.artisanmc.auctions.api.ListingType
import net.artisanmc.modules.api.playercommand.CommandArgument
import net.artisanmc.modules.api.playercommand.PlayerCommandContext
import net.artisanmc.modules.api.playercommand.PlayerCommandDeclaration
import net.artisanmc.modules.api.playercommand.PlayerCommandExecutor
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * `/ah` and its subcommands, declared to Artisan: it parses, completes and checks the
 * permission; the admin may rename `/ah`, add aliases, a cooldown or conditions with a
 * `command_override/v1` file targeting [ID]. Everything a player can do here is also
 * reachable from the menus.
 */
object AhCommand {
    const val ID = "auctions:ah"

    fun declaration(house: AuctionHouse, admin: AdminCommands): PlayerCommandDeclaration =
        PlayerCommandDeclaration.builder(ID)
            .name("ah")
            .aliases("auctionhouse", "hdv")
            .permission(AuctionHouse.PERM_USE)
            .description(mapOf("en" to "Open the auction house", "fr" to "Ouvrir l'hôtel des ventes"))
            .executor(player { p, _ -> house.open(p) })
            .child(sub("sell", AuctionHouse.PERM_SELL)
                .argument(CommandArgument.string("price").optional())
                .argument(CommandArgument.string("currency").optional())
                .executor(player { p, ctx ->
                    val price = ctx.text("price")
                    if (price == null) house.sellStart(p, ListingType.SALE)
                    else house.sell(p, ListingType.SALE, price, null, null, ctx.text("currency"))
                })
                .build())
            .child(sub("auction", AuctionHouse.PERM_SELL)
                .argument(CommandArgument.string("start").optional())
                .argument(CommandArgument.string("duration").optional())
                .argument(CommandArgument.string("buy_now").optional())
                .argument(CommandArgument.string("currency").optional())
                .executor(player { p, ctx ->
                    val start = ctx.text("start")
                    if (start == null) house.sellStart(p, ListingType.AUCTION)
                    else house.sell(p, ListingType.AUCTION, start, ctx.text("duration"), ctx.text("buy_now"), ctx.text("currency"))
                })
                .build())
            .child(sub("search", AuctionHouse.PERM_USE)
                .argument(CommandArgument.text("text"))
                .executor(player { p, ctx ->
                    val text = ctx.text("text").orEmpty().filter { it != '"' && it != '\\' }
                    house.open(p, AuctionHouse.MENU_MAIN, mapOf("search" to text))
                })
                .build())
            .child(sub("mine", AuctionHouse.PERM_USE).executor(player { p, _ -> house.open(p, AuctionHouse.MENU_MINE) }).build())
            .child(sub("claim", AuctionHouse.PERM_USE).executor(player { p, _ -> house.open(p, AuctionHouse.MENU_CLAIMS) }).build())
            .child(admin.declaration())
            .build()

    private fun sub(name: String, permission: String) =
        PlayerCommandDeclaration.builder("auctions:$name").name(name).permission(permission)

    /** Artisan refuses the console for these (not `consoleAllowed`): the player is there. */
    private fun player(run: (Player, PlayerCommandContext) -> Unit) = PlayerCommandExecutor { ctx ->
        ctx.player?.let { run(it, ctx) }
    }

    fun PlayerCommandContext.text(name: String): String? = get(name, String::class.java)
}

/** `/ah admin cancel <id>`, `closeall [player]`, `reindex`, `import <source> [confirm]`. */
class AdminCommands(private val house: AuctionHouse, private val imports: Imports) {

    fun declaration(): PlayerCommandDeclaration = admin("admin")
        .child(admin("admin.cancel")
            .name("cancel")
            .argument(CommandArgument.string("id"))
            .executor { ctx -> ctx.player?.let { cancel(it, ctx.get("id", String::class.java).orEmpty()) } }
            .build())
        .child(admin("admin.closeall")
            .name("closeall")
            .argument(CommandArgument.string("player").optional())
            .executor { ctx -> ctx.player?.let { closeAll(it, ctx.get("player", String::class.java)) } }
            .build())
        .child(admin("admin.reindex")
            .name("reindex")
            .consoleAllowed(true)
            .executor { ctx -> reindex(ctx.sender) }
            .build())
        .child(admin("admin.import")
            .name("import")
            .consoleAllowed(true)
            .argument(CommandArgument.choice("source", Imports.SOURCES))
            .argument(CommandArgument.choice("confirm", listOf("confirm")).optional())
            .executor { ctx -> imports.run(ctx.sender, ctx.get("source", String::class.java).orEmpty(), ctx.get("confirm", String::class.java) != null) }
            .build())
        .build()

    private fun admin(id: String) =
        PlayerCommandDeclaration.builder("auctions:$id").name(id).permission(AuctionHouse.PERM_ADMIN)

    fun cancel(p: Player, id: String) {
        val listing = house.findListing(id) ?: return p.sendMessage("Unknown or ambiguous listing id")
        house.cancel(p, listing, admin = true)
    }

    fun closeAll(p: Player, name: String?) {
        val seller = name?.let {
            cachedOfflinePlayer(it)?.uniqueId ?: return p.sendMessage("Unknown player $it")
        }
        p.sendMessage("Cancelling ${house.cancelAll(p, seller)} listing(s); items go to the sellers' claims")
    }

    fun reindex(sender: CommandSender) {
        sender.sendMessage("Reindexing price keys in the background…")
        house.reindex { sender.sendMessage(it) }
    }
}

class PlayerListener(private val house: AuctionHouse) : Listener {
    @EventHandler fun onJoin(e: PlayerJoinEvent) = house.join(e.player)
    @EventHandler fun onQuit(e: PlayerQuitEvent) = house.quit(e.player)
}

/** A player the server has seen, by name, without a Mojang lookup. Paper indexes them; Spigot is scanned. */
internal fun cachedOfflinePlayer(name: String): OfflinePlayer? = try {
    Bukkit.getOfflinePlayerIfCached(name)
} catch (_: NoSuchMethodError) {
    Bukkit.getOfflinePlayers().firstOrNull { it.name.equals(name, ignoreCase = true) }
}
