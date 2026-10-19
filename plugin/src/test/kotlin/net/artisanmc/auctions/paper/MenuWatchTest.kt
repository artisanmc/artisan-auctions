package net.artisanmc.auctions.paper

import net.artisanmc.modules.api.menu.MenuApi
import net.artisanmc.modules.api.menu.MenuClickListener
import net.artisanmc.modules.api.menu.MenuCloseListener
import net.artisanmc.modules.api.menu.MenuOpenEvent
import net.artisanmc.modules.api.menu.MenuOpenListener
import net.artisanmc.modules.api.menu.MenuRef
import net.artisanmc.modules.api.menu.MenuSubscription
import org.bukkit.entity.Player
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.*

class MenuWatchTest {
    private val id = UUID.randomUUID()
    private val player = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Player::class.java)) { _, m, _ ->
        if (m.name == "getUniqueId") id else null
    } as Player

    private class FakeMenus : MenuApi {
        var open: MenuRef? = null
        val opened = mutableListOf<Pair<MenuRef, Map<String, Any>>>()
        var listener: MenuOpenListener? = null
        override fun open(player: Player, menu: MenuRef, args: Map<String, Any>): Boolean {
            opened += menu to args
            return true
        }
        override fun open(player: Player, ref: String, args: Map<String, Any>) = open(player, MenuRef.parse(ref), args)
        override fun open(player: Player, ref: String) = open(player, ref, emptyMap())
        override fun close(player: Player) {}
        override fun openMenu(player: Player) = open
        override fun menus() = emptyList<MenuRef>()
        override fun onOpen(listener: MenuOpenListener): MenuSubscription { this.listener = listener; return MenuSubscription { this.listener = null } }
        override fun onClick(listener: MenuClickListener) = MenuSubscription {}
        override fun onClose(listener: MenuCloseListener) = MenuSubscription {}

    }

    @Test fun `a lot menu is opened again with the args it opened with`() {
        val menus = FakeMenus()
        val watch = MenuWatch(menus, "auctions")
        watch.start()
        val lot = MenuRef("auctions", "ah_lot")
        menus.open = lot
        menus.listener!!.onOpen(MenuOpenEvent(player, lot, mapOf("id" to "abc")))
        watch.repaint(player, listOf("ah_lot")) { it["id"] == "abc" }
        assertEquals(listOf(lot to mapOf<String, Any>("id" to "abc")), menus.opened)
    }

    @Test fun `another menu, another folder or a closed menu is left alone`() {
        val menus = FakeMenus()
        val watch = MenuWatch(menus, "auctions")
        watch.start()
        menus.open = MenuRef("auctions", "ah_main")
        watch.repaint(player, listOf("ah_lot"))
        menus.open = MenuRef("shop", "ah_lot")
        watch.repaint(player, listOf("ah_lot"))
        menus.open = null
        watch.repaint(player, listOf("ah_lot"))
        assertTrue(menus.opened.isEmpty())
    }

    @Test fun `a lot showing another listing is not repainted`() {
        val menus = FakeMenus()
        val watch = MenuWatch(menus, "auctions")
        watch.start()
        val lot = MenuRef("auctions", "ah_lot")
        menus.open = lot
        menus.listener!!.onOpen(MenuOpenEvent(player, lot, mapOf("id" to "other")))
        watch.repaint(player, listOf("ah_lot")) { it["id"] == "abc" }
        assertTrue(menus.opened.isEmpty())
        watch.stop()
        assertNull(menus.listener)
    }
}
