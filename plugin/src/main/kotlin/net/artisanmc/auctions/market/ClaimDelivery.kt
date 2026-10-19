package net.artisanmc.auctions.market

import net.artisanmc.auctions.db.AuctionStore
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Everything owed to a player goes through a claim, then this hands it over.
 * A claim is marked taken in the database before the item or money is given,
 * and put back if the hand-over fails: a claim is delivered at most once.
 */
class ClaimDelivery(
    private val store: AuctionStore,
    private val threads: Threads,
    private val wallets: Wallets,
    private val giver: ItemGiver,
    private val hooks: () -> Hooks,
    private val logger: Logger,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Outcome of a delivery round, on the main thread. */
    data class Report(val delivered: List<Claim>, val left: List<Claim>)

    /**
     * Delivers [claims] (main thread). Items go only to an online owner with room;
     * with [items] false they stay in the box (the buyer chose not to receive directly).
     */
    fun deliver(claims: List<Claim>, items: Boolean = true, done: (Report) -> Unit = {}) {
        val eligible = claims.filter { c ->
            when (c.kind) {
                ClaimKind.MONEY -> c.currency != null && c.amount != null && wallets.has(c.currency)
                ClaimKind.ITEM -> items && c.itemBlob != null && giver.isOnline(c.owner)
            }
        }
        val skipped = claims - eligible.toSet()
        val owners = claims.mapTo(HashSet()) { it.owner }
        if (eligible.isEmpty()) {
            if (owners.isNotEmpty()) hooks().claimsChanged(owners)
            done(Report(emptyList(), skipped))
            return
        }
        threads.async {
            val taken = eligible.filter { c ->
                runCatching { store.markClaimed(c.id, clock()) }
                    .onFailure { logger.log(Level.WARNING, "[Auctions] claim ${c.id} could not be marked", it) }
                    .getOrDefault(false)
            }
            threads.main {
                val delivered = mutableListOf<Claim>()
                val failed = mutableListOf<Claim>()
                for (c in taken) {
                    val ok = when (c.kind) {
                        ClaimKind.MONEY -> wallets.deposit(c.owner, c.currency!!, c.amount!!)
                        ClaimKind.ITEM -> giver.giveAll(c.owner, listOf(c.itemBlob!!))
                    }
                    if (ok) delivered += c else failed += c
                }
                delivered.forEach { hooks().claimed(it) }
                val report = Report(delivered, skipped + failed + (eligible - taken.toSet()))
                if (failed.isEmpty()) {
                    hooks().claimsChanged(owners)
                } else threads.async {
                    failed.forEach { runCatching { store.reopenClaim(it.id) } }
                    threads.main { hooks().claimsChanged(owners) }
                }
                done(report)
            }
        }
    }

    /** Every open claim of [owner] (main thread). */
    fun deliverAll(server: String, owner: UUID, done: (Report) -> Unit) {
        threads.async {
            val open = runCatching { store.unclaimed(server, owner) }.getOrElse {
                logger.log(Level.WARNING, "[Auctions] claims of $owner could not be read", it); emptyList()
            }
            threads.main { deliver(open, items = true, done) }
        }
    }

    /** One claim, if it belongs to [owner] (main thread). */
    fun deliverOne(owner: UUID, id: UUID, done: (Report) -> Unit) {
        threads.async {
            val claim = runCatching { store.claim(id) }.getOrNull()?.takeIf { it.owner == owner }
            threads.main { if (claim == null) done(Report(emptyList(), emptyList())) else deliver(listOf(claim), true, done) }
        }
    }
}
