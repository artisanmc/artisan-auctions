package net.artisanmc.auctions.market

import net.artisanmc.auctions.config.AuctionsConfig

/** Rules about the item and the seller, kept apart from Bukkit so they can be tested. */
object SellRules {
    /** True when [material] or a lore line is forbidden by `items`. */
    fun forbidden(material: String, plainLore: List<String>, items: AuctionsConfig.Items): Boolean {
        if (items.blacklist.any { it.equals(material, ignoreCase = true) }) return true
        if (items.blacklistLoreContains.isEmpty()) return false
        val lore = plainLore.map { it.lowercase() }
        return items.blacklistLoreContains.any { needle -> lore.any { it.contains(needle.lowercase()) } }
    }

    private val LIMIT = Regex("^auctions\\.limit\\.(\\d+)$")

    /**
     * Active listings allowed to a player holding [permissions]: the highest
     * `auctions.limit.<n>`, `auctions.limit.unlimited`, else [default].
     */
    fun limit(permissions: Collection<String>, default: Int): Int {
        var best: Int? = null
        for (p in permissions) {
            val perm = p.lowercase()
            if (perm == "auctions.limit.unlimited") return Int.MAX_VALUE
            val n = LIMIT.matchEntire(perm)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            best = maxOf(best ?: n, n)
        }
        return best ?: default
    }
}
