package net.artisanmc.auctions

import net.artisanmc.auctions.api.AuctionListing
import net.artisanmc.auctions.api.AuctionsService
import net.artisanmc.auctions.api.ListingType
import net.artisanmc.auctions.api.MedianPrice
import net.artisanmc.auctions.config.ConfigStore
import net.artisanmc.auctions.paper.AuctionHouse
import net.artisanmc.auctions.paper.ListingSnapshot
import org.bukkit.inventory.ItemStack
import java.util.Optional

class AuctionsServiceImpl(private val config: ConfigStore, private val house: AuctionHouse) : AuctionsService {
    override fun isEnabled(type: ListingType): Boolean = when (type) {
        ListingType.SALE -> config.current.sales.enabled
        ListingType.AUCTION -> config.current.auctions.enabled
    }

    override fun currencies(): List<String> = config.current.currencies.allowed

    override fun defaultCurrency(): String = config.current.currencies.default

    override fun activeListings(): List<AuctionListing> = house.cache.all().map(::ListingSnapshot)

    override fun priceKey(stack: ItemStack): String = house.priceKey(stack)

    override fun medianPrice(stack: ItemStack, currency: String): Optional<MedianPrice> =
        Optional.ofNullable(house.medianOf(stack, currency)?.takeIf { it.price != null }?.let { MedianPrice(it.price, it.sales, it.windowDays) })
}
