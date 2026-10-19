package net.artisanmc.auctions.paper

import net.artisanmc.auctions.config.AuctionsConfig
import net.artisanmc.auctions.db.AuctionStore
import net.artisanmc.auctions.market.Claim
import net.artisanmc.auctions.market.ClaimKind
import net.artisanmc.auctions.market.Threads
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Open claims of online players, kept in memory: a menu's data source runs on
 * the main thread and must not wait on the database. Reloaded on join and
 * whenever the market says a box changed.
 */
class ClaimBox(
    private val server: String,
    private val store: AuctionStore,
    private val threads: Threads,
    private val config: () -> AuctionsConfig,
    private val logger: Logger,
    private val changed: (UUID) -> Unit,
) {
    private val boxes = ConcurrentHashMap<UUID, List<Claim>>()
    /** Decoded icons, by claim id: decoding an item on every repaint is wasted work. */
    private val icons = ConcurrentHashMap<UUID, Map<String, Any?>>()

    fun of(owner: UUID): List<Claim> = boxes[owner].orEmpty()

    fun reload(owner: UUID, then: (List<Claim>) -> Unit = {}) {
        threads.async {
            val open = runCatching { store.unclaimed(server, owner) }
                .onFailure { logger.log(Level.WARNING, "[Auctions] claims of $owner could not be read", it) }
                .getOrNull()
            threads.main {
                if (open != null) {
                    boxes[owner] = open
                    changed(owner)
                }
                then(open.orEmpty())
            }
        }
    }

    fun forget(owner: UUID) {
        boxes.remove(owner)?.forEach { icons.remove(it.id) }
    }

    fun rows(owner: UUID): List<Map<String, Any?>> = of(owner).map { c -> row(c) }

    private fun row(c: Claim): Map<String, Any?> {
        val money = c.kind == ClaimKind.MONEY
        val icon = if (money) mapOf("material" to "GOLD_INGOT")
        else icons.getOrPut(c.id) {
            c.itemBlob?.let(PaperItems::decodeOrNull)?.let(PaperItems::display) ?: mapOf("material" to "BARRIER")
        }
        return mapOf(
            "id" to c.id.toString(),
            "kind" to c.kind.id,
            "is_money" to money,
            "icon" to icon,
            "amount_display" to (if (money && c.currency != null && c.amount != null) config().currencies.format(c.currency, c.amount) else ""),
            "reason" to c.reason.id,
            "created_at" to c.createdAt,
            "expires_at" to (c.createdAt + config().claims.retention.toMillis()),
        )
    }
}
