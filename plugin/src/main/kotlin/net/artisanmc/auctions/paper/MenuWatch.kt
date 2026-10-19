package net.artisanmc.auctions.paper

import net.artisanmc.modules.api.menu.MenuApi
import net.artisanmc.modules.api.menu.MenuRef
import net.artisanmc.modules.api.menu.MenuSubscription
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Repaints a house menu a player has open when what it shows changed. Artisan re-fetches
 * sources on each render but has no repaint call: the menu is opened again, in place,
 * with the args it opened with (remembered from the open events). The page of a paged
 * grid goes back to the first, so only menus where that is harmless are repainted.
 * A player in a dialog has no menu open and is left alone.
 */
class MenuWatch(private val menus: MenuApi, private val folder: String) {
    private val opened = ConcurrentHashMap<UUID, Pair<String, Map<String, Any>>>()
    private var subscription: MenuSubscription? = null

    fun start() {
        subscription = menus.onOpen { e ->
            if (e.menu.projectId == folder) opened[e.player.uniqueId] = e.menu.menuId to HashMap(e.args)
        }
    }

    fun stop() {
        subscription?.cancel()
        subscription = null
        opened.clear()
    }

    fun forget(player: UUID) {
        opened.remove(player)
    }

    /** The house menu [p] has open, or null. */
    fun viewing(p: Player): String? = menus.openMenu(p)?.takeIf { it.projectId == folder }?.menuId

    fun args(p: Player): Map<String, Any> = opened[p.uniqueId]?.takeIf { it.first == viewing(p) }?.second.orEmpty()

    /** Opens [p]'s house menu again when it is one of [menuIds] and [where] holds for its args. */
    fun repaint(p: Player, menuIds: Collection<String>, where: (Map<String, Any>) -> Boolean = { true }) {
        val id = viewing(p)?.takeIf { it in menuIds } ?: return
        val args = args(p)
        if (where(args)) menus.open(p, MenuRef(folder, id), args)
    }
}
