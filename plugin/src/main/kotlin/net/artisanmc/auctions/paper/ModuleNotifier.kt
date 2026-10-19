package net.artisanmc.auctions.paper

import net.artisanmc.auctions.AuctionsModule
import net.artisanmc.auctions.config.AuctionsConfig
import net.artisanmc.auctions.market.Listing
import net.artisanmc.auctions.market.Notifier
import net.artisanmc.auctions.sources.ListingRows
import net.artisanmc.modules.api.action.ModuleActionsApi
import net.artisanmc.modules.api.menu.MenuApi
import net.artisanmc.modules.api.menu.MenuRef
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.math.BigDecimal
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Messages live in the module's folder (`lang/<lang>.yaml`, section
 * `auctions.messages`), so server owners edit them in the editor like any
 * other text. They are sent through Artisan's action engine, which picks the
 * player's language and fills `{placeholders}` from the args.
 */
class ModuleNotifier(
    private val actions: ModuleActionsApi,
    private val menus: MenuApi,
    private val config: () -> AuctionsConfig,
    private val logger: Logger,
) : Notifier {
    override fun tell(player: UUID, key: String, args: Map<String, Any>) {
        Bukkit.getPlayer(player)?.let { tell(it, key, args) }
    }

    fun tell(player: Player, key: String, args: Map<String, Any> = emptyMap()) {
        run(player, "send_message @auctions.messages.$key", args)
    }

    /** A message another plugin supplied with its veto, or the generic one. */
    fun veto(player: Player, message: Any?) {
        if (message is Component) player.sendMessage(message) else tell(player, "vetoed")
    }

    fun openMenu(player: Player, menu: String, args: Map<String, Any> = emptyMap()) {
        if (!menus.open(player, MenuRef(AuctionsModule.FOLDER, menu), args)) {
            logger.warning("[Auctions] menu '${AuctionsModule.FOLDER}:$menu' not found: was it deleted from the module's folder?")
        }
    }

    fun openDialog(player: Player, dialog: String, args: Map<String, Any> = emptyMap()) = run(player, "open_dialog $dialog", args)

    private fun run(player: Player, dsl: String, args: Map<String, Any>) {
        runCatching { actions.execute(player, dsl, args.mapValues { (_, v) -> v.toString() }, AuctionsModule.FOLDER) }
            .onFailure { logger.log(Level.WARNING, "[Auctions] '$dsl' failed for ${player.name}", it) }
    }

    fun money(currency: String, amount: BigDecimal): String = config().currencies.format(currency, amount)

    fun listingArgs(l: Listing): Map<String, Any> = mapOf(
        "name" to ListingRows.name(l),
        "quantity" to l.quantity,
        "price" to money(l.currency, ListingRows.shownPrice(l)),
        "seller" to l.sellerName,
    )
}
