package net.artisanmc.auctions.paper

import net.artisanmc.auctions.api.AuctionCurrency
import net.milkbowl.vault.economy.Economy
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.ServicesManager
import java.math.BigDecimal

/**
 * The currencies of the server: those registered as [AuctionCurrency], plus Vault's
 * economy as `vault`. Looked up on each call, so a plugin that registers late is seen.
 */
class Currencies(private val services: ServicesManager) {
    private val vaultPresent = runCatching { Class.forName("net.milkbowl.vault.economy.Economy") }.isSuccess

    fun get(id: String): AuctionCurrency? = all()[id.lowercase()]

    fun has(id: String): Boolean = get(id) != null

    fun ids(): Set<String> = all().keys

    private fun all(): Map<String, AuctionCurrency> {
        val found = LinkedHashMap<String, AuctionCurrency>()
        // Highest priority first: the first of an id wins.
        for (reg in services.getRegistrations(AuctionCurrency::class.java)) {
            runCatching { found.putIfAbsent(reg.provider.id().lowercase(), reg.provider) }
        }
        if (vaultPresent && "vault" !in found) VaultCurrency.find(services)?.let { found["vault"] = it }
        return found
    }
}

/** Vault counts in doubles: amounts are converted at the edge. Only loaded when Vault is there. */
private class VaultCurrency(private val economy: Economy) : AuctionCurrency {
    override fun id() = "vault"

    override fun balance(player: OfflinePlayer): BigDecimal = BigDecimal.valueOf(economy.getBalance(player))

    override fun withdraw(player: OfflinePlayer, amount: BigDecimal): Boolean {
        val value = amount.toDouble()
        if (!economy.has(player, value)) return false
        return economy.withdrawPlayer(player, value).transactionSuccess()
    }

    override fun deposit(player: OfflinePlayer, amount: BigDecimal): Boolean =
        economy.depositPlayer(player, amount.toDouble()).transactionSuccess()

    companion object {
        fun find(services: ServicesManager): AuctionCurrency? =
            services.getRegistration(Economy::class.java)?.provider?.let(::VaultCurrency)
    }
}
