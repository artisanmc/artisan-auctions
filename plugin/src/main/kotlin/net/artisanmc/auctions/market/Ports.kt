package net.artisanmc.auctions.market

import java.math.BigDecimal
import java.util.UUID

/** Where work runs: the database off the main thread, money and inventories on it. */
interface Threads {
    fun async(task: () -> Unit)
    fun main(task: () -> Unit)
}

/** Money of any registered currency, online or offline player. */
interface Wallets {
    fun has(currency: String): Boolean
    fun balance(player: UUID, currency: String): BigDecimal
    fun withdraw(player: UUID, currency: String, amount: BigDecimal): Boolean
    fun deposit(player: UUID, currency: String, amount: BigDecimal): Boolean
}

/** Hands items to an online player. */
interface ItemGiver {
    /** True when the player is online and every item fit; false = nothing was given. */
    fun giveAll(player: UUID, blobs: List<String>): Boolean
    fun isOnline(player: UUID): Boolean
}

/** What a player is told. [key] is a message of the `auctions.messages` section of the module's `lang/` files. */
interface Notifier {
    fun tell(player: UUID, key: String, args: Map<String, Any> = emptyMap())
}
