package net.artisanmc.auctions.market

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Active listings of this server, kept in memory so menus never wait on the database. */
class ListingCache {
    private val byId = ConcurrentHashMap<UUID, Listing>()

    fun replaceAll(listings: Collection<Listing>) {
        byId.clear()
        listings.forEach { byId[it.id] = it }
    }

    fun put(l: Listing) { byId[l.id] = l }
    fun remove(id: UUID): Listing? = byId.remove(id)
    fun get(id: UUID): Listing? = byId[id]
    fun all(): Collection<Listing> = byId.values
    fun countBySeller(seller: UUID): Int = byId.values.count { it.sellerUuid == seller }
    fun due(now: Long): List<Listing> = byId.values.filter { it.endsAt <= now }
}
