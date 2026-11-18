package net.artisanmc.auctions.market

import java.math.BigDecimal
import java.util.UUID

/**
 * Where work runs. The market's state lives on one thread, [main]: the server's main thread on
 * Paper and Spigot, the global region on Folia. The database runs off it ([async]), and a
 * player's inventory is touched only on the thread that owns that player ([player]).
 */
interface Threads {
    fun async(task: () -> Unit)
    fun main(task: () -> Unit)

    /**
     * Runs [task] on the thread that owns [player], or [offline] on [main] when the player is not
     * online or leaves before [task] ran. Exactly one of the two runs, so a hand-over that did
     * not happen can always be undone.
     */
    fun player(player: UUID, task: () -> Unit, offline: () -> Unit)
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
