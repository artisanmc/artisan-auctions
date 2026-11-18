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
            threads.main { handOver(taken, owners, skipped + (eligible - taken.toSet()), done) }
        }
    }

    /**
     * Money is paid here, on the main thread; items on the thread that owns their owner, who
     * may have left meanwhile. A claim not handed over is put back in the box. [done] runs once
     * every claim is settled, on the main thread.
     */
    private fun handOver(taken: List<Claim>, owners: Set<UUID>, notTaken: List<Claim>, done: (Report) -> Unit) {
        val delivered = mutableListOf<Claim>()
        val failed = mutableListOf<Claim>()
        val (money, items) = taken.partition { it.kind == ClaimKind.MONEY }
        for (c in money) {
            if (wallets.deposit(c.owner, c.currency!!, c.amount!!)) delivered += c else failed += c
        }
        val byOwner = items.groupBy { it.owner }
        var waiting = byOwner.size

        fun finish() {
            delivered.forEach { hooks().claimed(it) }
            val report = Report(delivered.toList(), notTaken + failed)
            if (failed.isEmpty()) {
                hooks().claimsChanged(owners)
            } else {
                val reopen = failed.toList()
                threads.async {
                    reopen.forEach { runCatching { store.reopenClaim(it.id) } }
                    threads.main { hooks().claimsChanged(owners) }
                }
            }
            done(report)
        }

        if (waiting == 0) return finish()
        for ((owner, claims) in byOwner) {
            threads.player(owner, task = {
                // One claim at a time: those that fit are given, the others wait in the box.
                val ok = claims.map { c -> c to giver.giveAll(owner, listOf(c.itemBlob!!)) }
                threads.main {
                    ok.forEach { (c, given) -> if (given) delivered += c else failed += c }
                    if (--waiting == 0) finish()
                }
            }, offline = {
                failed += claims
                if (--waiting == 0) finish()
            })
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
