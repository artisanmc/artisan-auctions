package net.artisanmc.auctions.paper

import net.artisanmc.auctions.market.ItemGiver
import net.artisanmc.auctions.market.Threads
import net.artisanmc.auctions.market.Wallets
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * One database thread: writes stay in the order the main thread asked for
 * them, which the market relies on (journal before settlement).
 */
class PaperThreads(private val plugin: Plugin) : Threads {
    private val db = Executors.newSingleThreadExecutor { r -> Thread(r, "ArtisanAuctions-db").apply { isDaemon = true } }

    override fun async(task: () -> Unit) {
        db.execute {
            try { task() } catch (e: Throwable) { plugin.logger.log(Level.SEVERE, "[Auctions] database task failed", e) }
        }
    }

    override fun main(task: () -> Unit) {
        if (!plugin.isEnabled) {
            // Shutting down: finish on this thread rather than drop a result.
            runCatching(task).onFailure { plugin.logger.log(Level.WARNING, "[Auctions] task after shutdown failed", it) }
            return
        }
        Bukkit.getScheduler().runTask(plugin, Runnable { task() })
    }

    /** Waits for queued writes, so a stop never cuts a settlement in half. */
    fun shutdown() {
        db.shutdown()
        if (!db.awaitTermination(10, TimeUnit.SECONDS)) plugin.logger.warning("[Auctions] database tasks still running at shutdown")
    }
}

class EconomyWallets(private val economy: Currencies) : Wallets {
    override fun has(currency: String) = economy.has(currency)

    override fun balance(player: UUID, currency: String): BigDecimal =
        runCatching { economy.get(currency)?.balance(Bukkit.getOfflinePlayer(player)) }.getOrNull() ?: BigDecimal.ZERO

    override fun withdraw(player: UUID, currency: String, amount: BigDecimal): Boolean =
        runCatching { economy.get(currency)?.withdraw(Bukkit.getOfflinePlayer(player), amount) == true }.getOrDefault(false)

    override fun deposit(player: UUID, currency: String, amount: BigDecimal): Boolean =
        runCatching { economy.get(currency)?.deposit(Bukkit.getOfflinePlayer(player), amount) == true }.getOrDefault(false)
}

class InventoryGiver : ItemGiver {
    override fun isOnline(player: UUID) = Bukkit.getPlayer(player) != null

    override fun giveAll(player: UUID, blobs: List<String>): Boolean {
        val p = Bukkit.getPlayer(player) ?: return false
        val stacks = blobs.map { PaperItems.decodeOrNull(it) ?: return false }
        if (!fits(p, stacks)) return false
        val left = p.inventory.addItem(*stacks.map(ItemStack::clone).toTypedArray())
        // fits() said yes: anything left is a race with another plugin, keep it safe.
        left.values.forEach { p.world.dropItemNaturally(p.location, it) }
        return true
    }

    companion object {
        /** Tries the stacks on a copy of the storage slots. */
        fun fits(p: Player, stacks: List<ItemStack>): Boolean {
            val storage = p.inventory.storageContents
            val copy = Bukkit.createInventory(null, 36)
            copy.storageContents = storage.map { it?.clone() }.toTypedArray().copyOf(36)
            return copy.addItem(*stacks.map(ItemStack::clone).toTypedArray()).isEmpty()
        }
    }
}
